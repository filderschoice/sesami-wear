package com.sesamiwear.mobile.messaging

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleMultiProbeThrottle
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.core.ble.SesameBleProbeThrottle
import com.sesamiwear.mobile.ble.BleMultiProbeOperations
import com.sesamiwear.mobile.ble.BleProbeOperations
import com.sesamiwear.mobile.ble.SesameBleMultiProber
import com.sesamiwear.mobile.ble.SesameBleProber
import com.sesamiwear.mobile.showcase.ShowcaseMode
import com.sesamiwear.mobile.state.SesameDeviceStores
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ウォッチの「Bluetooth接続を確認」（BL-273）の要求を受けて、スマホで確認を実行し、進捗を返す。
 *
 * ウォッチはsecretKeyもBLEも持たないため、探索・接続はスマホが行う（DESIGN.md「アーキテクチャ方針」）。
 * 確認は**ウォッチの画面が開いている間だけ**続ける。画面を閉じたときの停止要求が届かなくても、
 * 上限時間（[MAX_SESSION_MILLIS]）で自動的に止まる（スキャンの電力を使い続けないため）。
 * 同時に動かす確認は1つだけで、新しい開始要求は前の確認を止めて置き換える。
 *
 * [com.sesamiwear.mobile.messaging.SesameMessageListenerService]のインスタンスは短命なため、
 * プロセス単位のスコープで動かす。Android依存の薄いアダプタのためユニットテスト対象外
 * （段階・距離の判定はcoreでテスト済み）。
 */
object SesameBleProbeRelay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Session? = null

    private class Session(
        val job: Job,
        val connectRequests: Channel<Unit>,
    )

    /**
     * [deviceUuid]の確認を始め、進捗を[nodeId]のウォッチへ返す。[deviceUuid]が全デバイスを表す値
     * （[SesameWearProtocol.ALL_DEVICES_TARGET_UUID]）なら、登録済み全デバイスの「まとめて接続確認」（BL-280）になる。
     */
    @Synchronized
    fun start(
        context: Context,
        deviceUuid: String,
        nodeId: String,
    ) {
        stop()
        val appContext = context.applicationContext
        val requests = Channel<Unit>(Channel.CONFLATED)
        val job =
            scope.launch {
                withTimeoutOrNull(MAX_SESSION_MILLIS) {
                    if (deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID) {
                        runAll(appContext, nodeId, requests)
                    } else {
                        run(appContext, deviceUuid, nodeId, requests)
                    }
                }
            }
        Log.d(TAG, "probe session started all=${deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID}")
        // 止まった（停止要求・置き換え）か、上限時間・終了で終わったかを残す。ウォッチを閉じたときに停止が届いたかの判定用。
        job.invokeOnCompletion { cause ->
            Log.d(TAG, "probe session ended: ${if (cause is CancellationException) "stopped" else "finished"}")
        }
        session = Session(job, requests)
    }

    /** 接続・ログインまで試す。確認が始まっていなければ何もしない。 */
    @Synchronized
    fun connect() {
        session?.connectRequests?.trySend(Unit)
    }

    /** 確認を止める（スキャンも止まる）。 */
    @Synchronized
    fun stop() {
        session?.job?.cancel()
        session = null
    }

    private suspend fun run(
        context: Context,
        deviceUuid: String,
        nodeId: String,
        connectRequests: Channel<Unit>,
    ) {
        val credentials = SesameDeviceStores.credentials(context).loadAll().find { it.uuid == deviceUuid }
        if (credentials == null) {
            send(context, nodeId, SesameBleProbeSnapshot(SesameBleProbeStage.FAILED))
            return
        }
        val prober = ShowcaseMode.probeOperationsOrNull(context, credentials) ?: SesameBleProber(context, credentials)
        var lastRssi: Int? = null
        val throttle = SesameBleProbeThrottle()
        while (true) {
            val observing =
                scope.launch {
                    prober.observe().collect { snapshot ->
                        lastRssi = snapshot.rssiDbm
                        if (throttle.shouldSend(snapshot, System.currentTimeMillis())) send(context, nodeId, snapshot)
                    }
                }
            connectRequests.receive()
            observing.cancelAndJoin()
            checkAndReport(context, nodeId, prober, lastRssi)
            // 結果をウォッチの画面で読めるよう少し保ってから、探索へ戻る。
            delay(RESULT_HOLD_MILLIS)
            throttle.reset()
        }
    }

    /**
     * 接続を試し、途中の段階と結果を**順番どおりに**ウォッチへ送る。途中の段階は非中断の
     * コールバックで届くため、いったん順序を保つ待ち行列へ積み、1つの送信処理が取り出して送る
     * （個別に送ると、結果より後に途中の段階が届いて表示が戻ることがある）。
     */
    private suspend fun checkAndReport(
        context: Context,
        nodeId: String,
        prober: BleProbeOperations,
        lastRssi: Int?,
    ) {
        val outbox = Channel<SesameBleProbeSnapshot>(Channel.UNLIMITED)
        val sender = scope.launch { for (snapshot in outbox) send(context, nodeId, snapshot) }
        outbox.trySend(prober.checkConnection(lastRssi) { outbox.trySend(it) })
        outbox.close()
        sender.join()
    }

    private suspend fun send(
        context: Context,
        nodeId: String,
        snapshot: SesameBleProbeSnapshot,
    ) = sendRaw(context, nodeId, SesameWearProtocol.PATH_BLE_PROBE_PROGRESS, snapshot.encode())

    private suspend fun sendRaw(
        context: Context,
        nodeId: String,
        path: String,
        payload: ByteArray,
    ) {
        DataLayerBestEffort.run(onFailure = { Log.w(TAG, "probe progress send failed: statusCode=$it") }) {
            Wearable.getMessageClient(context).sendMessage(nodeId, path, payload).await()
        }
    }

    /** 登録済み全デバイスの「まとめて接続確認」（BL-280）。進捗は[SesameWearProtocol.PATH_BLE_PROBE_PROGRESS_ALL]へ返す。 */
    private suspend fun runAll(
        context: Context,
        nodeId: String,
        connectRequests: Channel<Unit>,
    ) {
        val devices = SesameDeviceStores.credentials(context).loadAll()
        val multi = ShowcaseMode.multiProbeOperationsOrNull(context, devices) ?: SesameBleMultiProber(context, devices)
        var latest = SesameBleMultiProbeSnapshot.initial(devices.map { SesameDeviceSummary(it.uuid, it.displayName) })
        val throttle = SesameBleMultiProbeThrottle()
        while (true) {
            val observing =
                scope.launch {
                    multi.observe().collect { snapshot ->
                        latest = snapshot
                        if (throttle.shouldSend(
                                snapshot,
                                System.currentTimeMillis(),
                            )
                        ) {
                            sendAll(context, nodeId, snapshot)
                        }
                    }
                }
            connectRequests.receive()
            observing.cancelAndJoin()
            checkAndReportAll(context, nodeId, multi, latest)
            delay(RESULT_HOLD_MILLIS)
            throttle.reset()
        }
    }

    /** 全台の接続を1台ずつ試し、途中の段階と結果を順番どおりにウォッチへ送る（理由は[checkAndReport]と同じ）。 */
    private suspend fun checkAndReportAll(
        context: Context,
        nodeId: String,
        multi: BleMultiProbeOperations,
        latest: SesameBleMultiProbeSnapshot,
    ) {
        val outbox = Channel<SesameBleMultiProbeSnapshot>(Channel.UNLIMITED)
        val sender = scope.launch { for (snapshot in outbox) sendAll(context, nodeId, snapshot) }
        outbox.trySend(multi.checkConnections(latest) { outbox.trySend(it) })
        outbox.close()
        sender.join()
    }

    private suspend fun sendAll(
        context: Context,
        nodeId: String,
        snapshot: SesameBleMultiProbeSnapshot,
    ) = sendRaw(context, nodeId, SesameWearProtocol.PATH_BLE_PROBE_PROGRESS_ALL, snapshot.encode())

    /** 確認の最長時間。停止要求が届かない場合の保険。 */
    private const val MAX_SESSION_MILLIS = 90_000L

    /** 接続確認の結果を保つ時間。 */
    private const val RESULT_HOLD_MILLIS = 4_000L

    private const val TAG = "SesameBleProbeRelay"
}

package com.sesamiwear.mobile.ble

import android.content.Context
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.core.ble.SesameBleSightingTracker
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * 「Bluetooth接続を確認」（BL-272）の実行部。スマホのレーダー画面と、ウォッチからの要求
 * （BL-273）の両方がこれを使う。
 *
 * - [observe]: 対象Sesameの電波を追い続け、探索中 / 発見（RSSI付き）/ 圏外の進捗を流す。
 *   収集をやめるとスキャンも止まる。接続はしない（電力はスキャン分だけ）。
 * - [checkConnection]: 利用者が明示的に求めたときだけ、接続・ログインまで行って切断する。
 *   **Web APIへは倒さない**（Bluetoothの確認が目的のため）。
 *
 * 「常にインターネット経由」の設定でも確認は行える（利用者が明示的に求める操作のため）。
 * 判定と文言はcoreの[SesameBleSightingTracker] / [SesameBleProbeSnapshot]に置き、ここは
 * Androidの呼び出しだけを持つためユニットテスト対象外。
 */
class SesameBleProber(
    private val context: Context,
    private val credentials: SesameCredentials,
    private val scanner: SesameBleScanner = SesameBleScanner(context),
) : BleProbeOperations {
    override val usesBluetooth: Boolean = true

    private val client =
        context.applicationContext.let { appContext ->
            val store = SharedPreferencesKeyValueStore.forBleReachability(appContext)
            SesameBleClient(appContext, addressCache = SesameBleAddressCache(store))
        }

    /**
     * 電波を追う。権限が無い・Bluetoothがオフ・スキャンを開始できない場合は、その旨の
     * 終了段階を1つ流して完了する。それ以外は収集をやめるまで続く。
     */
    override fun observe(): Flow<SesameBleProbeSnapshot> =
        channelFlow {
            val blocked = blockedStage()
            if (blocked != null) {
                send(SesameBleProbeSnapshot(blocked))
                return@channelFlow
            }
            val tracker = SesameBleSightingTracker(startedAtMillis = System.currentTimeMillis())
            send(tracker.initial())
            val watchdog =
                launch {
                    while (true) {
                        delay(TICK_MILLIS)
                        tracker.onTick(System.currentTimeMillis())?.let { send(it) }
                    }
                }
            try {
                scanner.sightings(setOf(credentials.uuid)).collect {
                    send(tracker.onSighting(it.rssiDbm, System.currentTimeMillis()))
                }
            } catch (
                @Suppress("SwallowedException") e: IllegalStateException,
            ) {
                // スキャンの開始が拒否された（アプリごとの回数制限など）。確認としては失敗として示す。
                send(SesameBleProbeSnapshot(SesameBleProbeStage.FAILED))
            } finally {
                watchdog.cancel()
            }
        }

    /**
     * 接続とログインまで試し、結果の終了段階を返す。途中の段階（接続中・ログイン中）は[onProgress]へ流す。
     * 呼ぶ前に[observe]の収集を止めておくこと（スキャンと接続の同時実行を避けるため）。
     *
     * @param lastRssiDbm 直前に捉えていたRSSI。結果の表示で距離を併記するために引き継ぐ。
     */
    override suspend fun checkConnection(
        lastRssiDbm: Int?,
        onProgress: (SesameBleProbeSnapshot) -> Unit,
    ): SesameBleProbeSnapshot {
        val blocked = blockedStage()
        if (blocked != null) return SesameBleProbeSnapshot(blocked)
        val outcome =
            client.fetchStatus(
                credentials,
                onStage = { stage -> onProgress(SesameBleProbeSnapshot(stage, lastRssiDbm)) },
                // 確認に成功したら接続を保持し、次の施錠・解錠・状態取得で再利用する（BL-279）。
                retain = true,
            )
        val status = outcome.status
        return when (outcome.result) {
            SesameBleClient.Result.SUCCESS ->
                SesameBleProbeSnapshot(
                    SesameBleProbeStage.CONNECTED,
                    rssiDbm = lastRssiDbm,
                    batteryPercentage = status?.batteryPercentage,
                    position = status?.position,
                )
            SesameBleClient.Result.PERMISSION_DENIED ->
                SesameBleProbeSnapshot(SesameBleProbeStage.PERMISSION_DENIED)
            SesameBleClient.Result.NOT_FOUND -> SesameBleProbeSnapshot(SesameBleProbeStage.NOT_FOUND)
            else -> SesameBleProbeSnapshot(SesameBleProbeStage.FAILED, rssiDbm = lastRssiDbm)
        }
    }

    /** 権限・Bluetoothの状態から、そもそも確認できない場合の終了段階。確認できるならnull。 */
    private fun blockedStage(): SesameBleProbeStage? =
        when {
            !SesameBlePermissions.hasAll(context) -> SesameBleProbeStage.PERMISSION_DENIED
            !scanner.isBluetoothEnabled() -> SesameBleProbeStage.BLUETOOTH_OFF
            else -> null
        }

    private companion object {
        /** 見失い判定の確認間隔。 */
        const val TICK_MILLIS = 1_000L
    }
}

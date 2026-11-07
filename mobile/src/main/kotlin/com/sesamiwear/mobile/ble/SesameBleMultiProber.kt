package com.sesamiwear.mobile.ble

import android.content.Context
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleMultiSightingTracker
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「まとめて接続確認」（BL-280）の実行部。[devices]の全台を対象にする。
 *
 * - [observe]: **1回のスキャンで複数台を追う**（台ごとに別のスキャンを始めると、Androidのスキャン開始の
 *   回数制限に当たりやすいため）。台ごとの判定は[SesameBleMultiSightingTracker]に置く。
 * - [checkConnections]: 1台ずつ[SesameBleProber]で順番に確かめる（[checkSequentially]）。
 *
 * 権限なし・Bluetoothオフ・スキャン開始の拒否は、全台へ同じ終了段階を流して完了する。
 * 判定と文言はcoreにあり、ここはAndroidの呼び出しだけを持つためユニットテスト対象外。
 */
class SesameBleMultiProber(
    private val context: Context,
    private val devices: List<SesameCredentials>,
    private val scanner: SesameBleScanner = SesameBleScanner(context),
) : BleMultiProbeOperations {
    override val usesBluetooth: Boolean = true

    private val initial =
        SesameBleMultiProbeSnapshot(
            devices.map {
                SesameBleMultiProbeSnapshot.Entry(
                    it.uuid,
                    it.displayName,
                    SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING),
                )
            },
        )

    override fun observe(): Flow<SesameBleMultiProbeSnapshot> =
        channelFlow {
            val blocked = blockedStage()
            if (blocked != null) {
                send(initial.withAll(SesameBleProbeSnapshot(blocked)))
                return@channelFlow
            }
            // 見失い判定とスキャンが別の処理から更新するため、競合しないようStateFlowで原子的に更新する。
            val state = MutableStateFlow(initial)
            val tracker = SesameBleMultiSightingTracker(devices.map { it.uuid }, System.currentTimeMillis())
            val watchdog = launch { watchLost(tracker, state) }
            val scan = launch { scan(tracker, state) }
            try {
                state.collect { send(it) }
            } finally {
                watchdog.cancel()
                scan.cancel()
            }
        }

    /** 見失った台を定期的に「圏外」へ更新する。 */
    private suspend fun watchLost(
        tracker: SesameBleMultiSightingTracker,
        state: MutableStateFlow<SesameBleMultiProbeSnapshot>,
    ) {
        while (true) {
            delay(TICK_MILLIS)
            val lost = tracker.onTick(System.currentTimeMillis())
            if (lost.isNotEmpty()) {
                state.update { snapshot ->
                    lost.entries.fold(snapshot) { acc, (uuid, lostSnapshot) -> acc.withSnapshot(uuid, lostSnapshot) }
                }
            }
        }
    }

    /** 1回のスキャンで全台の電波を捉え、台ごとの「発見」へ更新する。 */
    private suspend fun scan(
        tracker: SesameBleMultiSightingTracker,
        state: MutableStateFlow<SesameBleMultiProbeSnapshot>,
    ) {
        try {
            scanner.sightings(devices.mapTo(HashSet()) { it.uuid }).collect {
                tracker.onSighting(it.deviceUuid, it.rssiDbm, System.currentTimeMillis())?.let { snapshot ->
                    state.update { current -> current.withSnapshot(it.deviceUuid, snapshot) }
                }
            }
        } catch (
            @Suppress("SwallowedException") e: IllegalStateException,
        ) {
            // スキャンの開始が拒否された（アプリごとの回数制限など）。確認としては失敗として示す。
            state.update { it.withAll(SesameBleProbeSnapshot(SesameBleProbeStage.FAILED)) }
        }
    }

    override suspend fun checkConnections(
        current: SesameBleMultiProbeSnapshot,
        onProgress: (SesameBleMultiProbeSnapshot) -> Unit,
    ): SesameBleMultiProbeSnapshot {
        val byUuid = devices.associateBy { it.uuid }
        return checkSequentially(current, { SesameBleProber(context, byUuid.getValue(it), scanner) }, onProgress)
    }

    /** 権限・Bluetoothの状態から、そもそも確認できない場合の終了段階。確認できるならnull。 */
    private fun blockedStage(): SesameBleProbeStage? =
        when {
            !SesameBlePermissions.hasAll(context) -> SesameBleProbeStage.PERMISSION_DENIED
            !scanner.isBluetoothEnabled() -> SesameBleProbeStage.BLUETOOTH_OFF
            else -> null
        }

    private fun SesameBleMultiProbeSnapshot.withAll(snapshot: SesameBleProbeSnapshot): SesameBleMultiProbeSnapshot =
        copy(entries = entries.map { it.copy(snapshot = snapshot) })

    private companion object {
        /** 見失い判定の確認間隔。 */
        const val TICK_MILLIS = 1_000L
    }
}

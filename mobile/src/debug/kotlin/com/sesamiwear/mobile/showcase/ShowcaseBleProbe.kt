package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.mobile.ble.BleProbeOperations
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 撮影モード中の「Bluetooth接続を確認」（BL-272 / BL-273）の動き。**デバッグ版にだけ含まれる。**
 * **Bluetoothを使わず**、撮影用の状態（[ShowcaseModeActivity]で選んだ失敗・経路・電池・施錠）を
 * レーダーの段階・距離へ次のとおり対応させる。
 *
 * - 失敗なし: 発見。経路がBluetoothなら約2m（-66dBm）、インターネット・未設定なら約4m（-76dBm）。
 *   RSSIは±1dBだけ揺らし、ブリップとレーダーが動いて見えるようにする。接続を試すと接続OK
 *   （電池残量と、施錠・解錠の実測の角度）。
 * - 失敗「通信エラー」: 圏外のまま（接続を試しても圏外）。
 * - 失敗「認証エラー」: 発見はするが、接続を試すと接続に失敗（ログインで失敗する状況）。
 * - 失敗「端末設定」: 権限がありません。
 *
 * 待ちを見せるため、各段階の間に[delayMillis]だけ待つ。
 */
class ShowcaseBleProbe(
    private val uuid: String,
    private val lockStateStore: LockStateStore,
    private val delayMillis: Long = DEFAULT_DELAY_MILLIS,
    private val tickMillis: Long = DEFAULT_TICK_MILLIS,
) : BleProbeOperations {
    override val usesBluetooth: Boolean = false

    override fun observe(): Flow<SesameBleProbeSnapshot> =
        flow {
            emit(SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING))
            delay(delayMillis)
            when (lockStateStore.load(uuid)?.lastFailure) {
                SesameStatusFailure.BACKGROUND_RESTRICTED ->
                    emit(SesameBleProbeSnapshot(SesameBleProbeStage.PERMISSION_DENIED))
                SesameStatusFailure.COMMUNICATION -> {
                    emit(SesameBleProbeSnapshot(SesameBleProbeStage.NOT_FOUND))
                    awaitCancellation()
                }
                else -> {
                    var tick = 0
                    while (true) {
                        emit(SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = baseRssi() + wobble(tick++)))
                        delay(tickMillis)
                    }
                }
            }
        }

    override suspend fun checkConnection(
        lastRssiDbm: Int?,
        onProgress: (SesameBleProbeSnapshot) -> Unit,
    ): SesameBleProbeSnapshot {
        val rssi = lastRssiDbm ?: baseRssi()
        val snapshot = lockStateStore.load(uuid)
        onProgress(SesameBleProbeSnapshot(SesameBleProbeStage.CONNECTING, rssi))
        delay(delayMillis)
        return when (snapshot?.lastFailure) {
            SesameStatusFailure.BACKGROUND_RESTRICTED -> SesameBleProbeSnapshot(SesameBleProbeStage.PERMISSION_DENIED)
            SesameStatusFailure.COMMUNICATION -> SesameBleProbeSnapshot(SesameBleProbeStage.NOT_FOUND)
            SesameStatusFailure.AUTH_OR_QUOTA -> {
                onProgress(SesameBleProbeSnapshot(SesameBleProbeStage.LOGGING_IN, rssi))
                delay(delayMillis)
                SesameBleProbeSnapshot(SesameBleProbeStage.FAILED, rssiDbm = rssi)
            }
            null -> {
                onProgress(SesameBleProbeSnapshot(SesameBleProbeStage.LOGGING_IN, rssi))
                delay(delayMillis)
                SesameBleProbeSnapshot(
                    SesameBleProbeStage.CONNECTED,
                    rssiDbm = rssi,
                    batteryPercentage = snapshot?.batteryPercentage,
                    position = ShowcaseDialOperations.positionOf(snapshot?.isLocked),
                )
            }
        }
    }

    /** 撮影用の経路から決める、発見時のRSSIの中心。Bluetooth経路なら近く、そうでなければやや遠く見せる。 */
    private fun baseRssi(): Int =
        if (lockStateStore.load(uuid)?.lastRoute == SesameStatusRoute.BLE) NEAR_RSSI_DBM else FAR_RSSI_DBM

    companion object {
        const val NEAR_RSSI_DBM = -66
        const val FAR_RSSI_DBM = -76
        private const val DEFAULT_DELAY_MILLIS = 800L
        private const val DEFAULT_TICK_MILLIS = 1_000L
        private val WOBBLE = intArrayOf(0, -1, 0, 1)

        /** 決まった順で±1dBだけ揺らす（撮影のたびに値が変わらないよう、乱数を使わない）。 */
        fun wobble(tick: Int): Int = WOBBLE[tick % WOBBLE.size]
    }
}

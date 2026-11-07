package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 撮影モードの「Bluetooth接続を確認」。Bluetoothを使わず、撮影用の状態をレーダーの段階・距離へ対応させる。 */
class ShowcaseBleProbeTest {
    private val device = ShowcasePresets.devices.first().first
    private val store = LockStateStore(InMemoryKeyValueStore())

    private fun probe() = ShowcaseBleProbe(device.uuid, store, delayMillis = 0, tickMillis = 0)

    @Test
    fun `Bluetoothの権限を要らない`() {
        assertFalse(probe().usesBluetooth)
    }

    @Test
    fun `失敗なしは探索中から発見へ進み、経路がBluetoothなら近くに見える`() =
        runTest {
            ShowcaseDeviceState(true, route = SesameStatusRoute.BLE).writeTo(store, device.uuid, 0)

            val snapshots = probe().observe().take(3).toList()

            assertEquals(SesameBleProbeStage.SCANNING, snapshots[0].stage)
            assertEquals(SesameBleProbeStage.FOUND, snapshots[1].stage)
            assertEquals(ShowcaseBleProbe.NEAR_RSSI_DBM + ShowcaseBleProbe.wobble(0), snapshots[1].rssiDbm)
            assertEquals(ShowcaseBleProbe.NEAR_RSSI_DBM + ShowcaseBleProbe.wobble(1), snapshots[2].rssiDbm)
        }

    @Test
    fun `経路がインターネットや未設定ならやや遠くに見える`() =
        runTest {
            val snapshots = probe().observe().take(2).toList()

            assertEquals(ShowcaseBleProbe.FAR_RSSI_DBM, snapshots[1].rssiDbm)
        }

    @Test
    fun `通信エラーは圏外のまま`() =
        runTest {
            ShowcaseDeviceState(true, failure = SesameStatusFailure.COMMUNICATION).writeTo(store, device.uuid, 0)

            assertEquals(SesameBleProbeStage.NOT_FOUND, probe().observe().take(2).toList()[1].stage)
            assertEquals(SesameBleProbeStage.NOT_FOUND, probe().checkConnection(null) {}.stage)
        }

    @Test
    fun `端末設定は権限がありません`() =
        runTest {
            ShowcaseDeviceState(true, failure = SesameStatusFailure.BACKGROUND_RESTRICTED)
                .writeTo(store, device.uuid, 0)

            assertEquals(SesameBleProbeStage.PERMISSION_DENIED, probe().observe().toList()[1].stage)
            assertEquals(SesameBleProbeStage.PERMISSION_DENIED, probe().checkConnection(null) {}.stage)
        }

    @Test
    fun `認証エラーは発見できるが接続に失敗する`() =
        runTest {
            ShowcaseDeviceState(true, failure = SesameStatusFailure.AUTH_OR_QUOTA).writeTo(store, device.uuid, 0)
            val progress = mutableListOf<SesameBleProbeStage>()

            val result = probe().checkConnection(-70) { progress += it.stage }

            assertEquals(listOf(SesameBleProbeStage.CONNECTING, SesameBleProbeStage.LOGGING_IN), progress)
            assertEquals(SesameBleProbeSnapshot(SesameBleProbeStage.FAILED, rssiDbm = -70), result)
        }

    @Test
    fun `失敗なしの接続確認は接続OKで、電池と施錠の角度を返す`() =
        runTest {
            ShowcaseDeviceState(true, batteryPercentage = 85).writeTo(store, device.uuid, 0)
            val progress = mutableListOf<SesameBleProbeStage>()

            val result = probe().checkConnection(-68) { progress += it.stage }

            assertEquals(listOf(SesameBleProbeStage.CONNECTING, SesameBleProbeStage.LOGGING_IN), progress)
            assertEquals(
                SesameBleProbeSnapshot(
                    SesameBleProbeStage.CONNECTED,
                    rssiDbm = -68,
                    batteryPercentage = 85,
                    position = ShowcaseDialOperations.LOCKED_POSITION,
                ),
                result,
            )
        }
}

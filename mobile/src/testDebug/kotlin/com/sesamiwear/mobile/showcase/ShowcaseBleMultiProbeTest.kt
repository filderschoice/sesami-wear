package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 撮影モードの「まとめて接続確認」（BL-280）。Bluetoothを使わず、台ごとの撮影用の状態を並べることを確かめる。 */
class ShowcaseBleMultiProbeTest {
    private val devices = ShowcasePresets.devices.map { it.first }
    private val store = LockStateStore(InMemoryKeyValueStore())

    private fun multi() =
        ShowcaseBleMultiProbe(
            devices.map {
                ShowcaseBleMultiProbe.Member(
                    it.uuid,
                    it.displayName,
                    ShowcaseBleProbe(it.uuid, store, delayMillis = 0, tickMillis = 1_000),
                )
            },
        )

    @Test
    fun `Bluetoothの権限を要らない`() {
        assertFalse(multi().usesBluetooth)
    }

    @Test
    fun `全台が発見になり、経路がBluetoothの台は近く、そうでない台は遠く見える`() =
        runTest {
            ShowcaseDeviceState(true, route = SesameStatusRoute.BLE).writeTo(store, devices[0].uuid, 0)
            ShowcaseDeviceState(false, route = SesameStatusRoute.WEB_API).writeTo(store, devices[1].uuid, 0)

            val found =
                multi().observe().first { snapshot ->
                    snapshot.entries.all { it.snapshot.stage == SesameBleProbeStage.FOUND }
                }

            assertEquals(devices.map { it.displayName }, found.entries.map { it.name })
            val near = requireNotNull(found.entries[0].snapshot.rssiDbm)
            val far = requireNotNull(found.entries[1].snapshot.rssiDbm)
            assertEquals(true, near > far)
        }

    @Test
    fun `接続確認は全台を順に確かめ、失敗の台だけ失敗になる`() =
        runTest {
            ShowcaseDeviceState(true, batteryPercentage = 85).writeTo(store, devices[0].uuid, 0)
            ShowcaseDeviceState(false, failure = SesameStatusFailure.AUTH_OR_QUOTA).writeTo(store, devices[1].uuid, 0)
            ShowcaseDeviceState(true, failure = SesameStatusFailure.COMMUNICATION).writeTo(store, devices[2].uuid, 0)
            val initial = SesameBleMultiProbeSnapshot(multi().observe().first().entries)

            val result = multi().checkConnections(initial) {}

            assertEquals(
                listOf(SesameBleProbeStage.CONNECTED, SesameBleProbeStage.FAILED, SesameBleProbeStage.NOT_FOUND),
                result.entries.map { it.snapshot.stage },
            )
            assertEquals(85, result.entries[0].snapshot.batteryPercentage)
        }
}

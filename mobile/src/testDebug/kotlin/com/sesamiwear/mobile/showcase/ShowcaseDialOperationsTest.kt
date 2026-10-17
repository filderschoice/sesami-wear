package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleClient
import com.sesamiwear.mobile.command.LockStateNotifier
import com.sesamiwear.mobile.dial.DialUiState
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 撮影モードのダイヤル操作（BL-246）。Bluetoothを使わず、撮影用の状態を表示・操作することを確かめる。 */
class ShowcaseDialOperationsTest {
    private val device = ShowcasePresets.devices.first().first
    private val store = LockStateStore(InMemoryKeyValueStore())

    private fun operations() =
        ShowcaseDialOperations(
            uuid = device.uuid,
            lockStateStore = store,
            commands =
                ShowcaseDeviceCommands(
                    loadDevices = { listOf(device) },
                    lockStateStore = store,
                    notifier = LockStateNotifier(local = { _, _ -> }, watch = { _, _ -> }),
                ),
            delayMillis = 0,
        )

    @Test
    fun `施錠中なら施錠の角度と電池で操作できる表示になる`() =
        runTest {
            ShowcaseDeviceState(true, batteryPercentage = 85).writeTo(store, device.uuid, 0)

            val state = operations().load() as DialUiState.Ready

            assertEquals(ShowcaseDialOperations.LOCKED_POSITION, state.position)
            assertEquals(true, state.isLocked)
            assertEquals(85, state.batteryPercentage)
            assertEquals(SesameCommand.UNLOCK, state.command)
            assertNull(state.lastResult)
        }

    @Test
    fun `未取得なら途中の位置の表示になり、なぞっても送らない`() =
        runTest {
            val state = operations().load() as DialUiState.Ready

            assertEquals(ShowcaseDialOperations.MIDDLE_POSITION, state.position)
            assertNull(state.isLocked)
            assertNull(state.command)
        }

    @Test
    fun `失敗を設定していれば届かないときの文言を出す`() =
        runTest {
            ShowcaseDeviceState(true, failure = SesameStatusFailure.AUTH_OR_QUOTA).writeTo(store, device.uuid, 0)

            assertEquals(
                DialUiState.Unavailable(DialUiState.unavailableMessage(SesameBleClient.Result.LOGIN_FAILED)),
                operations().load(),
            )
        }

    @Test
    fun `操作すると撮影用の状態を切り替え、結果の1行を添えて読み直す`() =
        runTest {
            ShowcaseDeviceState(true).writeTo(store, device.uuid, 0)

            val state = operations().run(SesameCommand.UNLOCK) as DialUiState.Ready

            assertEquals(false, store.load(device.uuid)?.isLocked)
            assertEquals(ShowcaseDialOperations.UNLOCKED_POSITION, state.position)
            assertEquals(DialUiState.resultMessage(SesameCommand.UNLOCK, succeeded = true), state.lastResult)
        }

    @Test
    fun `失敗の種類ごとに届かないときの文言の種類が決まる`() {
        assertEquals(
            SesameBleClient.Result.PERMISSION_DENIED,
            ShowcaseDialOperations.bleResultOf(SesameStatusFailure.BACKGROUND_RESTRICTED),
        )
        assertEquals(
            SesameBleClient.Result.CONNECTION_FAILED,
            ShowcaseDialOperations.bleResultOf(SesameStatusFailure.COMMUNICATION),
        )
    }
}

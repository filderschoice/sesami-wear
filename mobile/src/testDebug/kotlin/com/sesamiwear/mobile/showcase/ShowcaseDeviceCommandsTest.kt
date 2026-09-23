package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.LockStateNotifier
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 撮影モードの実行口（BL-212）。実物へ通信せず、撮影用の状態だけを書き換えることを確かめる。 */
class ShowcaseDeviceCommandsTest {
    private val device = ShowcasePresets.devices.first().first
    private val store = LockStateStore(InMemoryKeyValueStore())
    private val watchSyncs = mutableListOf<Pair<String, SesameStatusSnapshot>>()
    private val localNotifications = mutableListOf<String>()
    private var now = 1_000_000L

    private fun commands(devices: List<SesameCredentials> = listOf(device)) =
        ShowcaseDeviceCommands(
            loadDevices = { devices },
            lockStateStore = store,
            notifier =
                LockStateNotifier(
                    local = { uuid, _ -> localNotifications += uuid },
                    watch = { uuid, snapshot -> watchSyncs += uuid to snapshot },
                ),
            nowMillis = { now },
        )

    @Test
    fun `施錠と解錠は撮影用の状態を切り替え、電池と経路は残してウォッチとウィジェットへ知らせる`() =
        runTest {
            ShowcaseDeviceState(
                true,
                batteryPercentage = 85,
                route = SesameStatusRoute.BLE,
            ).writeTo(store, device.uuid, 0)

            assertEquals(Outcome.SUCCESS, commands().execute(device.uuid, SesameCommand.UNLOCK))

            val snapshot = store.load(device.uuid)
            assertEquals(false, snapshot?.isLocked)
            assertEquals(now, snapshot?.updatedAtEpochMillis)
            assertEquals(85, snapshot?.batteryPercentage)
            assertEquals(SesameStatusRoute.BLE, snapshot?.lastRoute)
            assertEquals(listOf(device.uuid), watchSyncs.map { it.first })
            assertEquals(listOf(device.uuid), localNotifications)
        }

    @Test
    fun `撮影用でないデバイスへの操作は何もせず失敗を返す`() =
        runTest {
            val realUuid = "11111111-2222-3333-4444-555555555555"

            assertEquals(Outcome.FAILURE, commands().execute(realUuid, SesameCommand.LOCK))
            assertNull(commands().refreshStatus(realUuid))

            assertNull(store.load(realUuid))
            assertTrue(watchSyncs.isEmpty())
            assertTrue(localNotifications.isEmpty())
        }

    @Test
    fun `直近の失敗が設定されていれば操作も失敗させて状態を変えない`() =
        runTest {
            ShowcaseDeviceState(true, failure = SesameStatusFailure.COMMUNICATION).writeTo(store, device.uuid, 0)

            assertEquals(Outcome.FAILURE, commands().execute(device.uuid, SesameCommand.UNLOCK))

            assertEquals(true, store.load(device.uuid)?.isLocked)
            assertTrue(watchSyncs.isEmpty())
        }

    @Test
    fun `状態取得は施錠状態を保ったまま取得時刻を今へ進める`() =
        runTest {
            ShowcaseDeviceState(false, ageMillis = 600_000).writeTo(store, device.uuid, now)

            assertEquals(false, commands().refreshStatus(device.uuid))

            assertEquals(now, store.load(device.uuid)?.updatedAtEpochMillis)
            assertEquals(listOf(device.uuid), watchSyncs.map { it.first })
        }

    @Test
    fun `未取得のデバイスの状態取得はnullを返し何も書かない`() =
        runTest {
            assertNull(commands().refreshStatus(device.uuid))
            assertNull(store.load(device.uuid))
        }
}

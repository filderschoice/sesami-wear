package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleFailure
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.messaging.CommandDebouncer
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLE経路とWeb APIの使い分けの検証（BL-152 / BL-167 / BL-190 / BL-191）。
 * 土台は[SesameDeviceCommandExecutorTestFixture]が持つ。
 */
class SesameDeviceCommandExecutorBleRouteTest : SesameDeviceCommandExecutorTestFixture() {
    @Test
    fun `a device with no ble history goes straight to the web api`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome = createExecutor(bleAccess = bleAccess(reachability)).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(1, server.requestCount)
            assertEquals(0, bleAttempts)
            assertEquals(1, apiCalls)
        }

    @Test
    fun `a reachable device is operated over ble without calling the web api`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)

            val outcome = createExecutor(bleAccess = bleAccess(reachability)).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(1, bleAttempts)
            assertEquals(0, server.requestCount)
            // BLEはSesame Web APIの月間上限を消費しない（BL-141 / BL-152）。
            assertEquals(0, apiCalls)
            assertEquals(true, lockStateStore.load(DEVICE_UUID)?.isLocked)
        }

    @Test
    fun `a failed ble attempt falls back to the web api and forgets the history`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome =
                createExecutor(bleAccess = bleAccess(reachability, bleFailure = SesameBleFailure.NOT_REACHED))
                    .execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(1, bleAttempts)
            assertEquals(1, server.requestCount)
            assertFalse(reachability.preferBle(DEVICE_UUID, now))
        }

    @Test
    fun `status is fetched over ble without calling the web api`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)

            val isLocked =
                createExecutor(bleAccess = bleAccess(reachability, bleStatus = false)).refreshStatus(DEVICE_UUID)

            assertEquals(false, isLocked)
            assertEquals(0, server.requestCount)
            assertEquals(0, apiCalls)
            assertEquals(false, lockStateStore.load(DEVICE_UUID)?.isLocked)
        }

    @Test
    fun `a failed ble status falls back to the web api`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            val isLocked =
                createExecutor(bleAccess = bleAccess(reachability, bleStatus = null)).refreshStatus(DEVICE_UUID)

            assertEquals(true, isLocked)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a ble failure with the device in range keeps trying ble on the next operation`() =
        runTest {
            // 接続・ログインだけが失敗した場合は圏内にいる証拠があるため、到達確認を待たずに再試行する（BL-191）。
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            val executor =
                createExecutor(
                    bleAccess =
                        bleAccess(
                            reachability,
                            bleFailure = SesameBleFailure.REACHED_BUT_FAILED,
                        ),
                    debouncer = CommandDebouncer(windowMillis = 0, nowMillis = { now }),
                )

            executor.execute(DEVICE_UUID, SesameCommand.LOCK)
            executor.execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(2, bleAttempts)
            assertTrue(reachability.preferBle(DEVICE_UUID, now))
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a ble failure with the device out of range moves the next operation to the web api`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            val executor =
                createExecutor(
                    bleAccess = bleAccess(reachability, bleFailure = SesameBleFailure.NOT_REACHED),
                    debouncer = CommandDebouncer(windowMillis = 0, nowMillis = { now }),
                )

            executor.execute(DEVICE_UUID, SesameCommand.LOCK)
            executor.execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(1, bleAttempts)
            assertFalse(reachability.preferBle(DEVICE_UUID, now))
        }

    @Test
    fun `a successful probe alongside the web api makes the next operation use ble`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            createExecutor(bleAccess = bleAccess(reachability, probeFindsDevice = true))
                .execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(1, probes)
            assertTrue(reachability.preferBle(DEVICE_UUID, now))
        }

    @Test
    fun `a probe that finds nothing keeps the next operation on the web api`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            createExecutor(bleAccess = bleAccess(reachability, probeFindsDevice = false))
                .execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(1, probes)
            assertFalse(reachability.preferBle(DEVICE_UUID, now))
        }

    @Test
    fun `the web api only policy never touches bluetooth`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            // 直近にBLEで到達できていても、方針が「常にインターネット経由」なら使わない。
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome =
                createExecutor(
                    bleAccess =
                        bleAccess(
                            reachability,
                            probeFindsDevice = true,
                            routePolicy = SesameRoutePolicy.WEB_API_ONLY,
                        ),
                ).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(0, bleAttempts)
            // 到達確認のスキャンも行わない（電力とスキャン回数を消費しない）。
            assertEquals(0, probes)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `the web api only policy also applies to status fetches`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            val isLocked =
                createExecutor(
                    bleAccess = bleAccess(reachability, routePolicy = SesameRoutePolicy.WEB_API_ONLY),
                ).refreshStatus(DEVICE_UUID)

            assertEquals(true, isLocked)
            assertEquals(0, bleAttempts)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `falling back from ble notifies the user once`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            createExecutor(bleAccess = bleAccess(reachability, bleFailure = SesameBleFailure.NOT_REACHED))
                .execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(1, fallbackNotices)
        }

    @Test
    fun `a device that was never reachable does not notify a fallback`() =
        runTest {
            // BLEを試していないため「切り替わった」わけではない。毎回通知するとうるさい。
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            createExecutor(bleAccess = bleAccess(reachability)).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(0, fallbackNotices)
        }

    @Test
    fun `the web api only policy does not notify a fallback`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            createExecutor(
                bleAccess = bleAccess(reachability, routePolicy = SesameRoutePolicy.WEB_API_ONLY),
            ).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(0, fallbackNotices)
        }

    @Test
    fun `a successful ble operation does not notify a fallback`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)

            createExecutor(bleAccess = bleAccess(reachability)).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(0, fallbackNotices)
        }

    @Test
    fun `a failed ble status fetch also notifies the fallback`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            createExecutor(bleAccess = bleAccess(reachability, bleStatus = null)).refreshStatus(DEVICE_UUID)

            assertEquals(1, fallbackNotices)
        }

    @Test
    fun `the route is stored so the display can show which one was used`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)

            createExecutor(bleAccess = bleAccess(reachability)).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameStatusRoute.BLE, lockStateStore.load(DEVICE_UUID)?.lastRoute)
            assertEquals(BLE_BATTERY_PERCENTAGE, lockStateStore.load(DEVICE_UUID)?.batteryPercentage)
        }

    @Test
    fun `a web api status fetch stores the battery and the route`() =
        runTest {
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            createExecutor().refreshStatus(DEVICE_UUID)

            val snapshot = lockStateStore.load(DEVICE_UUID)
            assertEquals(SesameStatusRoute.WEB_API, snapshot?.lastRoute)
            // statusJson の batteryVoltage は 5.8V → 91%。
            assertEquals(91, snapshot?.batteryPercentage)
            assertEquals(11, snapshot?.position)
        }

    @Test
    fun `the route log records which route was used without leaking credentials`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)

            createExecutor(bleAccess = bleAccess(reachability)).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(1, routeLogs.size)
            assertTrue(routeLogs.single().contains("route=BLE"))
            assertTrue(routeLogs.single().contains("op=LOCK"))
            assertFalse(routeLogs.single().contains(validCredentials.apiKey))
            assertFalse(routeLogs.single().contains(validCredentials.secretKeyHex))
        }

    @Test
    fun `the demo device never touches the ble route`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(SesameDemoMode.DEMO_DEVICE_UUID, now, reachable = true)

            createExecutor(bleAccess = bleAccess(reachability))
                .execute(SesameDemoMode.DEMO_DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(0, bleAttempts)
            assertEquals(0, probes)
        }
}

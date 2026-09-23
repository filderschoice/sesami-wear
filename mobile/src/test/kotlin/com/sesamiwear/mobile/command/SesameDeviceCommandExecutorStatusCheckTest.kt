package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleFailure
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「更新」（状態取得）で到達実績によらずBLEを試す動作の検証（BL-204）。
 * 土台は[SesameDeviceCommandExecutorTestFixture]が持つ。
 */
class SesameDeviceCommandExecutorStatusCheckTest : SesameDeviceCommandExecutorTestFixture() {
    @Test
    fun `a refresh tries ble even without a reachability history`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())

            val isLocked =
                createExecutor(bleAccess = bleAccess(reachability, bleStatus = true)).refreshStatus(DEVICE_UUID)

            assertEquals(true, isLocked)
            assertEquals(1, bleAttempts)
            // BLEで取れたためWeb APIは呼ばず、月間上限（BL-141）を消費しない。
            assertEquals(0, server.requestCount)
            assertEquals(0, apiCalls)
            assertEquals(SesameStatusRoute.BLE, lockStateStore.load(DEVICE_UUID)?.lastRoute)
            // 以降の施錠/解錠もBLEを先に試せるよう、到達実績が立つ。
            assertTrue(reachability.preferBle(DEVICE_UUID, now))
        }

    @Test
    fun `a refresh out of range falls back to the web api and probes regardless of the interval`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            // 直前に到達確認をしたばかり（通常なら間隔が空くまで到達確認をしない）。
            reachability.recordProbe(DEVICE_UUID, now, reachable = false)
            server.enqueue(MockResponse().setBody(statusJson("unlocked")).setResponseCode(HTTP_OK))

            val isLocked =
                createExecutor(
                    bleAccess =
                        bleAccess(
                            reachability,
                            bleStatus = null,
                            bleFailure = SesameBleFailure.NOT_REACHED,
                            probeFindsDevice = true,
                        ),
                ).refreshStatus(DEVICE_UUID)

            assertEquals(false, isLocked)
            assertEquals(1, bleAttempts)
            assertEquals(1, server.requestCount)
            assertEquals(SesameStatusRoute.WEB_API, lockStateStore.load(DEVICE_UUID)?.lastRoute)
            // 圏外と判定したときは、アドレスを覚え直すため到達確認を必ず行う。
            assertEquals(1, probes)
            assertTrue(reachability.preferBle(DEVICE_UUID, now))
        }

    @Test
    fun `a refresh that reached the device but failed does not force a probe`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.recordProbe(DEVICE_UUID, now, reachable = false)
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            val isLocked =
                createExecutor(
                    bleAccess =
                        bleAccess(reachability, bleStatus = null, bleFailure = SesameBleFailure.REACHED_BUT_FAILED),
                ).refreshStatus(DEVICE_UUID)

            assertEquals(true, isLocked)
            assertEquals(1, server.requestCount)
            // 圏内にいた証拠があるため、間隔を無視した到達確認はしない（従来の間隔判定に任せる）。
            assertEquals(0, probes)
        }

    @Test
    fun `a refresh does not try ble when bluetooth is unavailable`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            bleAvailable = false
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            val isLocked = createExecutor(bleAccess = bleAccess(reachability)).refreshStatus(DEVICE_UUID)

            assertEquals(true, isLocked)
            assertEquals(0, bleAttempts)
            assertEquals(1, server.requestCount)
            // 試していないため、フォールバックの通知も出さない。
            assertEquals(0, fallbackNotices)
        }

    @Test
    fun `a refresh does not try ble under the web api only policy`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            createExecutor(
                bleAccess = bleAccess(reachability, routePolicy = SesameRoutePolicy.WEB_API_ONLY),
            ).refreshStatus(DEVICE_UUID)

            assertEquals(0, bleAttempts)
            assertEquals(0, probes)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `locking without a reachability history still goes straight to the web api`() =
        runTest {
            // 到達実績を無視するのは「更新」だけ。施錠/解錠の経路選択は変えない（待ち時間を増やさない）。
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            createExecutor(bleAccess = bleAccess(reachability)).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(0, bleAttempts)
            assertEquals(1, server.requestCount)
            assertFalse(reachability.preferBle(DEVICE_UUID, now))
        }
}

package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.diagnostics.SesameDiagnosticsEntry
import com.sesamiwear.core.diagnostics.SesameDiagnosticsOutcome
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 施錠/解錠の送信後の状態照合（BL-293）の検証。
 * OFF（既定）では状態を取りに行かず現行の挙動のままで、ONのときだけWeb API経路で照合することを固定する。
 */
class SesameDeviceCommandExecutorVerificationTest : SesameDeviceCommandExecutorTestFixture() {
    private val waits = mutableListOf<Long>()
    private val diagnostics = mutableListOf<SesameDiagnosticsEntry>()

    private fun verifyingExecutor(
        enabled: Boolean,
        bleAccess: SesameBleAccess = SesameBleAccess(),
        pause: suspend (Long) -> Unit = { waits += it },
    ) = createExecutor(
        bleAccess = bleAccess,
        verification =
            SesameVerificationAccess(
                isEnabled = { enabled },
                waitMillis = WAIT_MILLIS,
                pause = pause,
            ),
        diagnosticsRecord = { diagnostics += it },
    )

    private fun statusBody(
        lockStatus: String,
        timestamp: Long?,
    ): String {
        val timestampField = timestamp?.let { ""","timestamp":$it""" }.orEmpty()
        return """{"batteryVoltage":5.8,"position":11,"CHSesame2Status":"$lockStatus"$timestampField}"""
    }

    @Test
    fun `off by default does not fetch the status and behaves as before`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome = verifyingExecutor(enabled = false).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(1, server.requestCount)
            assertEquals(1, apiCalls)
            assertTrue(waits.isEmpty())
            assertNull(lockStateStore.load(DEVICE_UUID)?.lastFailure)
        }

    @Test
    fun `on and matching status is a success after waiting once`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(statusBody("locked", FRESH_TIMESTAMP)))

            val outcome = verifyingExecutor(enabled = true).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(listOf(WAIT_MILLIS), waits)
            assertEquals(2, apiCalls)
            assertEquals("/$DEVICE_UUID/cmd", server.takeRequest().path)
            assertEquals("/$DEVICE_UUID", server.takeRequest().path)
            assertEquals(true, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertNull(lockStateStore.load(DEVICE_UUID)?.lastFailure)
        }

    @Test
    fun `on and mismatching fresh status saves the real state and reports not reflected`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(statusBody("unlocked", FRESH_TIMESTAMP)))

            val outcome = verifyingExecutor(enabled = true).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.FAILURE, outcome)
            val saved = lockStateStore.load(DEVICE_UUID)
            assertEquals(false, saved?.isLocked)
            assertEquals(SesameStatusFailure.NOT_REFLECTED, saved?.lastFailure)
            assertEquals(SesameStatusFailure.NOT_REFLECTED, watchSyncs.last().second.lastFailure)
            assertEquals(false, watchSyncs.last().second.isLocked)
            assertEquals(SesameDiagnosticsOutcome.FAILURE, diagnostics.single().outcome)
            assertEquals(SesameStatusFailure.NOT_REFLECTED.shortLabel, diagnostics.single().reason)
        }

    @Test
    fun `on and a stale status is not adopted and the command stays a success`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(statusBody("unlocked", STALE_TIMESTAMP)))

            val outcome = verifyingExecutor(enabled = true).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(true, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertNull(lockStateStore.load(DEVICE_UUID)?.lastFailure)
            assertEquals(SesameDiagnosticsOutcome.SUCCESS, diagnostics.single().outcome)
        }

    @Test
    fun `on and a failed status fetch keeps the command a success`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setResponseCode(HTTP_SERVER_ERROR))

            val outcome = verifyingExecutor(enabled = true).execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(false, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertNull(lockStateStore.load(DEVICE_UUID)?.lastFailure)
            assertEquals(1, failureLogs.size)
        }

    @Test
    fun `on does not verify when the command went over BLE`() =
        runTest {
            val reachability = SesameBleReachability(InMemoryKeyValueStore())
            reachability.record(DEVICE_UUID, now, reachable = true)

            val outcome =
                verifyingExecutor(enabled = true, bleAccess = bleAccess(reachability))
                    .execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(SesameStatusRoute.BLE, lockStateStore.load(DEVICE_UUID)?.lastRoute)
            assertEquals(0, server.requestCount)
            assertTrue(waits.isEmpty())
        }

    @Test
    fun `on and a failed command does not verify`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_FORBIDDEN))

            val outcome = verifyingExecutor(enabled = true).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.FAILURE, outcome)
            assertEquals(1, server.requestCount)
            assertTrue(waits.isEmpty())
        }

    @Test
    fun `off leaves results saves notifications and diagnostics exactly as before`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome = verifyingExecutor(enabled = false).execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            // 資格情報の検索は送信の1回だけ（照合のための再検索をしない）。
            assertEquals(1, credentialsLoads)
            assertEquals(1, watchSyncs.size)
            assertEquals(1, notifications.size)
            assertEquals(true, watchSyncs.single().second.isLocked)
            assertNull(watchSyncs.single().second.lastFailure)
            assertEquals(true, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(SesameStatusRoute.WEB_API, lockStateStore.load(DEVICE_UUID)?.lastRoute)
            assertEquals(SesameDiagnosticsOutcome.SUCCESS, diagnostics.single().outcome)
            assertTrue(responseLogs.none { it.startsWith("verify") })
        }

    @Test
    fun `on does not verify the demo device`() =
        runTest {
            val outcome = verifyingExecutor(enabled = true).execute(SesameDemoMode.DEMO_DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(0, server.requestCount)
            assertTrue(waits.isEmpty())
        }

    @Test
    fun `on and a cancelled wait leaves a cancelled log and rethrows`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            val executor = verifyingExecutor(enabled = true, pause = { throw CancellationException("cancelled") })

            val failure = runCatching { executor.execute(DEVICE_UUID, SesameCommand.LOCK) }.exceptionOrNull()

            assertTrue(failure is CancellationException)

            assertTrue(responseLogs.contains("verify LOCK: cancelled"))
            // 送信時点の状態は保存済みで、診断ログも欠けない。
            assertEquals(true, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(SesameDiagnosticsOutcome.SUCCESS, diagnostics.single().outcome)
            assertEquals(1, server.requestCount)
        }

    private companion object {
        const val WAIT_MILLIS = 4_000L

        // 固定の現在時刻は10,000ms（10秒）。それ以上は新しい値、未満は古い値のtimestamp（秒）。
        const val FRESH_TIMESTAMP = 20L
        const val STALE_TIMESTAMP = 5L
    }
}

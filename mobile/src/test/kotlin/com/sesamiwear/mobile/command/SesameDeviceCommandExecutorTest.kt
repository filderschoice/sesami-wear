package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.messaging.CommandDebouncer
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SesameDeviceCommandExecutorTest {
    private lateinit var server: MockWebServer
    private lateinit var lockStateStore: LockStateStore
    private val notifications = mutableListOf<Pair<String, Boolean>>()
    private var now = 10_000L
    private var credentialsList = listOf(validCredentials)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        lockStateStore = LockStateStore(InMemoryKeyValueStore())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createExecutor(debouncer: CommandDebouncer = CommandDebouncer { now }) =
        SesameDeviceCommandExecutor(
            loadCredentials = { credentialsList },
            lockStateStore = lockStateStore,
            listener = { uuid, isLocked -> notifications += uuid to isLocked },
            debouncer = debouncer,
            apiClientFactory = { credentials ->
                SesameApiClient(
                    uuid = credentials.uuid,
                    apiKey = credentials.apiKey,
                    httpClient = OkHttpClient(),
                    baseUrl = server.url("/").toString().trimEnd('/'),
                )
            },
            nowMillis = { now },
        )

    @Test
    fun `lock success sends the command, saves locked state and notifies`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome = createExecutor().execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals("/$DEVICE_UUID/cmd", server.takeRequest().path)
            assertEquals(true, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(now, lockStateStore.load(DEVICE_UUID)?.updatedAtEpochMillis)
            assertEquals(listOf(DEVICE_UUID to true), notifications)
        }

    @Test
    fun `unlock success saves unlocked state`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome = createExecutor().execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(false, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(listOf(DEVICE_UUID to false), notifications)
        }

    @Test
    fun `API error returns failure without saving or notifying`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_FORBIDDEN))

            val outcome = createExecutor().execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.FAILURE, outcome)
            assertNull(lockStateStore.load(DEVICE_UUID))
            assertTrue(notifications.isEmpty())
        }

    @Test
    fun `missing credentials returns failure without calling the API`() =
        runTest {
            credentialsList = emptyList()

            val outcome = createExecutor().execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.FAILURE, outcome)
            assertEquals(0, server.requestCount)
            assertTrue(notifications.isEmpty())
        }

    @Test
    fun `invalid secret key returns failure without calling the API`() =
        runTest {
            credentialsList = listOf(validCredentials.copy(secretKeyHex = "zz"))

            val outcome = createExecutor().execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.FAILURE, outcome)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `duplicate within the window is debounced across executors sharing a debouncer`() =
        runTest {
            // ウォッチ経由とウィジェット経由を、同じデバウンサーを共有する別々の実行口として表す。
            val sharedDebouncer = CommandDebouncer { now }
            val fromWatch = createExecutor(sharedDebouncer)
            val fromWidget = createExecutor(sharedDebouncer)
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            assertEquals(
                SesameDeviceCommandExecutor.Outcome.SUCCESS,
                fromWatch.execute(DEVICE_UUID, SesameCommand.LOCK),
            )
            now += 1_000L
            assertEquals(
                SesameDeviceCommandExecutor.Outcome.DEBOUNCED,
                fromWidget.execute(DEVICE_UUID, SesameCommand.LOCK),
            )

            assertEquals(1, server.requestCount)
            assertEquals(1, notifications.size)
        }

    @Test
    fun `command after the debounce window is executed again`() =
        runTest {
            val executor = createExecutor()
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            executor.execute(DEVICE_UUID, SesameCommand.LOCK)
            now += CommandDebouncer.DEFAULT_WINDOW_MILLIS
            val outcome = executor.execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(false, lockStateStore.load(DEVICE_UUID)?.isLocked)
        }

    @Test
    fun `refresh status saves and notifies the fetched state`() =
        runTest {
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))

            val isLocked = createExecutor().refreshStatus(DEVICE_UUID)

            assertEquals(true, isLocked)
            assertEquals("/$DEVICE_UUID", server.takeRequest().path)
            assertEquals(true, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(listOf(DEVICE_UUID to true), notifications)
        }

    @Test
    fun `refresh status is not debounced right after a command`() =
        runTest {
            val executor = createExecutor()
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setBody(statusJson("unlocked")).setResponseCode(HTTP_OK))

            executor.execute(DEVICE_UUID, SesameCommand.LOCK)
            val isLocked = executor.refreshStatus(DEVICE_UUID)

            assertEquals(false, isLocked)
            assertEquals(false, lockStateStore.load(DEVICE_UUID)?.isLocked)
        }

    @Test
    fun `refresh status API error returns null without saving or notifying`() =
        runTest {
            server.enqueue(MockResponse().setBody("{}").setResponseCode(HTTP_FORBIDDEN))

            val isLocked = createExecutor().refreshStatus(DEVICE_UUID)

            assertNull(isLocked)
            assertNull(lockStateStore.load(DEVICE_UUID))
            assertTrue(notifications.isEmpty())
        }

    @Test
    fun `refresh status without credentials returns null without calling the API`() =
        runTest {
            credentialsList = emptyList()

            assertNull(createExecutor().refreshStatus(DEVICE_UUID))
            assertEquals(0, server.requestCount)
        }

    private fun statusJson(lockStatus: String) =
        """{"batteryVoltage":5.8,"position":11,"CHSesame2Status":"$lockStatus"}"""

    private companion object {
        const val DEVICE_UUID = "test-uuid"
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403

        // RFC 4493のテストベクタ鍵（ダミー、実資格情報ではない）。
        val validCredentials =
            SesameCredentials(
                uuid = DEVICE_UUID,
                apiKey = "test-api-key",
                secretKeyHex = "2b7e151628aed2a6abf7158809cf4f3c",
                displayName = "テスト",
            )
    }
}

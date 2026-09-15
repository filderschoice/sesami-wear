package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor
import com.sesamiwear.mobile.messaging.CommandDebouncer
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetCommandRunnerTest {
    private lateinit var server: MockWebServer
    private val lockStateStore = LockStateStore(InMemoryKeyValueStore())
    private val tracker = WidgetInProgressTracker()
    private val debouncer = CommandDebouncer { 10_000L }
    private val redrawInProgress = mutableListOf<Boolean>()
    private val failingUuids = mutableSetOf<String>()

    private val credentials =
        listOf("uuid-front", "uuid-back").map {
            // RFC 4493のテストベクタ鍵（ダミー、実資格情報ではない）。
            SesameCredentials(uuid = it, apiKey = "test-api-key", secretKeyHex = "2b7e151628aed2a6abf7158809cf4f3c")
        }
    private val registered = credentials.map { SesameDeviceSummary(uuid = it.uuid, displayName = it.uuid) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val uuid = request.path.orEmpty().removePrefix("/").substringBefore("/")
                    return if (uuid in failingUuids) {
                        MockResponse().setResponseCode(HTTP_FORBIDDEN).setBody("{}")
                    } else if (request.method == "GET") {
                        MockResponse().setResponseCode(HTTP_OK)
                            .setBody("""{"batteryVoltage":5.8,"position":11,"CHSesame2Status":"unlocked"}""")
                    } else {
                        MockResponse().setResponseCode(HTTP_OK)
                    }
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun executor() =
        SesameDeviceCommandExecutor(
            loadCredentials = { credentials },
            lockStateStore = lockStateStore,
            listener = { _, _ -> },
            debouncer = debouncer,
            apiClientFactory = {
                SesameApiClient(it.uuid, it.apiKey, OkHttpClient(), server.url("/").toString().trimEnd('/'))
            },
            nowMillis = { 10_000L },
        )

    private fun runner(executor: SesameDeviceCommandExecutor = executor()) =
        WidgetCommandRunner(
            executor = executor,
            loadRegisteredDevices = { registered },
            inProgressTracker = tracker,
            requestRedraw = { redrawInProgress += tracker.isInProgress("uuid-front", registered) },
        )

    @Test
    fun `command shows in progress while running and clears it afterwards`() =
        runTest {
            val outcomes = runner().runCommand("uuid-front", SesameCommand.LOCK)

            assertEquals(listOf(SesameDeviceCommandExecutor.Outcome.SUCCESS), outcomes)
            assertEquals(listOf(true, false), redrawInProgress)
            assertEquals(true, lockStateStore.load("uuid-front")?.isLocked)
        }

    @Test
    fun `all devices sends the command to every registered device`() =
        runTest {
            val outcomes = runner().runCommand(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, SesameCommand.LOCK)

            assertEquals(2, outcomes.size)
            assertEquals(setOf("/uuid-front/cmd", "/uuid-back/cmd"), (1..2).map { server.takeRequest().path }.toSet())
            assertEquals(true, lockStateStore.load("uuid-front")?.isLocked)
            assertEquals(true, lockStateStore.load("uuid-back")?.isLocked)
            assertFalse(tracker.isInProgress(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, registered))
        }

    @Test
    fun `failure keeps the state from before the operation`() =
        runTest {
            lockStateStore.save("uuid-front", isLocked = true, updatedAtEpochMillis = 1L)
            failingUuids += "uuid-front"

            val outcomes = runner().runCommand("uuid-front", SesameCommand.UNLOCK)

            assertEquals(listOf(SesameDeviceCommandExecutor.Outcome.FAILURE), outcomes)
            assertEquals(true, lockStateStore.load("uuid-front")?.isLocked)
            assertEquals(listOf(true, false), redrawInProgress)
        }

    @Test
    fun `widget and watch commands to the same device within the window run once`() =
        runTest {
            val sharedExecutor = executor()
            // ウォッチ経由（SesameMessageListenerService）も同じデバウンサーを共有する実行口を使う。
            val fromWatch = executor()

            assertEquals(
                SesameDeviceCommandExecutor.Outcome.SUCCESS,
                fromWatch.execute("uuid-front", SesameCommand.LOCK),
            )
            val outcomes = runner(sharedExecutor).runCommand("uuid-front", SesameCommand.LOCK)

            assertEquals(listOf(SesameDeviceCommandExecutor.Outcome.DEBOUNCED), outcomes)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `refresh status only fetches the state`() =
        runTest {
            val states = runner().refreshStatus("uuid-front")

            assertEquals(listOf(false), states)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/uuid-front", request.path)
            assertTrue(redrawInProgress.first())
        }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
    }
}

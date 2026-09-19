package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusReading
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.messaging.CommandDebouncer
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SesameDeviceCommandExecutorTest {
    private lateinit var server: MockWebServer
    private lateinit var lockStateStore: LockStateStore
    private val notifications = mutableListOf<Pair<String, SesameStatusSnapshot>>()
    private val watchSyncs = mutableListOf<Pair<String, SesameStatusSnapshot>>()
    private var now = 10_000L
    private var credentialsList = listOf(validCredentials)
    private val failureLogs = mutableListOf<String>()
    private val routeLogs = mutableListOf<String>()
    private var apiCalls = 0
    private var bleAttempts = 0
    private var probes = 0
    private var fallbackNotices = 0

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

    private fun createExecutor(
        debouncer: CommandDebouncer = CommandDebouncer { now },
        bleAccess: SesameBleAccess = SesameBleAccess(),
    ) = SesameDeviceCommandExecutor(
        loadCredentials = { credentialsList },
        lockStateStore = lockStateStore,
        notifier =
            LockStateNotifier(
                local = { uuid, snapshot -> notifications += uuid to snapshot },
                watch = { uuid, snapshot -> watchSyncs += uuid to snapshot },
            ),
        routes =
            SesameRouteAccess(
                api =
                    SesameApiAccess(
                        clientFactory = { credentials ->
                            SesameApiClient(
                                uuid = credentials.uuid,
                                apiKey = credentials.apiKey,
                                httpClient = OkHttpClient(),
                                baseUrl = server.url("/").toString().trimEnd('/'),
                            )
                        },
                        logFailure = { message -> failureLogs += message },
                        recordApiCall = { apiCalls++ },
                    ),
                ble = bleAccess,
            ),
        debouncer = debouncer,
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
            assertEquals(listOf(DEVICE_UUID to true), lockStatesOf(notifications))
            assertEquals(listOf(DEVICE_UUID to true), lockStatesOf(watchSyncs))
        }

    @Test
    fun `demo device command changes only the local state without API or watch sync`() =
        runTest {
            credentialsList = emptyList()

            val outcome = createExecutor().execute(SesameDemoMode.DEMO_DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(0, server.requestCount)
            assertTrue(watchSyncs.isEmpty())
            assertEquals(false, lockStateStore.load(SesameDemoMode.DEMO_DEVICE_UUID)?.isLocked)
            assertEquals(listOf(SesameDemoMode.DEMO_DEVICE_UUID to false), lockStatesOf(notifications))
        }

    @Test
    fun `demo device status refresh returns the local state without API or notifications`() =
        runTest {
            val executor = createExecutor()

            assertEquals(SesameDemoMode.INITIAL_IS_LOCKED, executor.refreshStatus(SesameDemoMode.DEMO_DEVICE_UUID))
            now += CommandDebouncer.DEFAULT_WINDOW_MILLIS
            executor.execute(SesameDemoMode.DEMO_DEVICE_UUID, SesameCommand.UNLOCK)
            notifications.clear()

            assertEquals(false, executor.refreshStatus(SesameDemoMode.DEMO_DEVICE_UUID))
            assertEquals(0, server.requestCount)
            assertTrue(watchSyncs.isEmpty())
            assertTrue(notifications.isEmpty())
        }

    @Test
    fun `demo device commands are debounced like real devices`() =
        runTest {
            val executor = createExecutor()

            executor.execute(SesameDemoMode.DEMO_DEVICE_UUID, SesameCommand.UNLOCK)
            val outcome = executor.execute(SesameDemoMode.DEMO_DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.DEBOUNCED, outcome)
            assertEquals(false, lockStateStore.load(SesameDemoMode.DEMO_DEVICE_UUID)?.isLocked)
        }

    @Test
    fun `unlock success saves unlocked state`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val outcome = createExecutor().execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.SUCCESS, outcome)
            assertEquals(false, lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(listOf(DEVICE_UUID to false), lockStatesOf(notifications))
        }

    @Test
    fun `API error returns failure and records it without inventing a lock state`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_FORBIDDEN))

            val outcome = createExecutor().execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(SesameDeviceCommandExecutor.Outcome.FAILURE, outcome)
            // 施錠状態は分からないままだが、失敗したことは表示のために残す（BL-140）。
            assertNull(lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(SesameStatusFailure.AUTH_OR_QUOTA, lockStateStore.load(DEVICE_UUID)?.lastFailure)
            assertEquals(listOf(DEVICE_UUID to null), lockStatesOf(notifications))
            assertEquals(listOf(DEVICE_UUID to null), lockStatesOf(watchSyncs))
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
            assertEquals(listOf(DEVICE_UUID to true), lockStatesOf(notifications))
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
    fun `refresh status API error returns null and records the failure`() =
        runTest {
            server.enqueue(MockResponse().setBody("{}").setResponseCode(HTTP_FORBIDDEN))

            val isLocked = createExecutor().refreshStatus(DEVICE_UUID)

            assertNull(isLocked)
            assertNull(lockStateStore.load(DEVICE_UUID)?.isLocked)
            assertEquals(SesameStatusFailure.AUTH_OR_QUOTA, lockStateStore.load(DEVICE_UUID)?.lastFailure)
            assertEquals(listOf(DEVICE_UUID to null), lockStatesOf(notifications))
        }

    @Test
    fun `refresh status failure keeps the last known state and marks it as failed`() =
        runTest {
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val executor = createExecutor()

            executor.refreshStatus(DEVICE_UUID)
            val fetchedAt = lockStateStore.load(DEVICE_UUID)?.updatedAtEpochMillis
            now += 60_000L
            executor.refreshStatus(DEVICE_UUID)

            val snapshot = lockStateStore.load(DEVICE_UUID)
            assertEquals(true, snapshot?.isLocked)
            assertEquals(fetchedAt, snapshot?.updatedAtEpochMillis)
            // 通信そのものの失敗はHTTPステータスコードを持たないため通信エラーになる（BL-139 / BL-140）。
            assertEquals(SesameStatusFailure.COMMUNICATION, snapshot?.lastFailure)
        }

    @Test
    fun `a later success clears the recorded failure`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_FORBIDDEN))
            server.enqueue(MockResponse().setBody(statusJson("unlocked")).setResponseCode(HTTP_OK))
            val executor = createExecutor()

            executor.refreshStatus(DEVICE_UUID)
            // 連打の抑止（BL-148）に掛からないよう時間を進めてから取り直す。
            now += CommandDebouncer.DEFAULT_WINDOW_MILLIS
            executor.refreshStatus(DEVICE_UUID)

            val snapshot = lockStateStore.load(DEVICE_UUID)
            assertEquals(false, snapshot?.isLocked)
            assertNull(snapshot?.lastFailure)
        }

    @Test
    fun `refresh status failure logs the http status code`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody("""{"Message":"User is not authorized"}""")
                    .setResponseCode(HTTP_FORBIDDEN),
            )

            createExecutor().refreshStatus(DEVICE_UUID)

            assertEquals(listOf("status failed: HTTP 403"), failureLogs)
        }

    @Test
    fun `command failure logs the http status code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_FORBIDDEN))

            createExecutor().execute(DEVICE_UUID, SesameCommand.UNLOCK)

            assertEquals(listOf("unlock failed: HTTP 403"), failureLogs)
        }

    @Test
    fun `failure logs never contain credentials or the response body`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody("""{"Message":"User is not authorized"}""")
                    .setResponseCode(HTTP_FORBIDDEN),
            )
            server.enqueue(MockResponse().setResponseCode(HTTP_FORBIDDEN))
            val executor = createExecutor()

            executor.refreshStatus(DEVICE_UUID)
            executor.execute(DEVICE_UUID, SesameCommand.LOCK)

            assertEquals(2, failureLogs.size)
            val secrets =
                listOf(
                    DEVICE_UUID,
                    validCredentials.apiKey,
                    validCredentials.secretKeyHex,
                    "User is not authorized",
                    server.url("/").toString(),
                )
            failureLogs.forEach { log ->
                secrets.forEach { secret -> assertFalse(log, log.contains(secret)) }
            }
        }

    @Test
    fun `successful calls do not log anything`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))
            val executor = createExecutor()

            executor.execute(DEVICE_UUID, SesameCommand.LOCK)
            executor.refreshStatus(DEVICE_UUID)

            assertTrue(failureLogs.isEmpty())
        }

    @Test
    fun `repeated status refresh within the debounce window calls the API once`() =
        runTest {
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))
            val executor = createExecutor()

            assertEquals(true, executor.refreshStatus(DEVICE_UUID))
            now += 1_000L
            // 2回目は連打として無視し、APIを呼ばずに保存済みの状態を返す（BL-148）。
            assertEquals(true, executor.refreshStatus(DEVICE_UUID))

            assertEquals(1, server.requestCount)
            assertEquals(1, notifications.size)
        }

    @Test
    fun `status refresh after the debounce window calls the API again`() =
        runTest {
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setBody(statusJson("unlocked")).setResponseCode(HTTP_OK))
            val executor = createExecutor()

            executor.refreshStatus(DEVICE_UUID)
            now += CommandDebouncer.DEFAULT_WINDOW_MILLIS
            val isLocked = executor.refreshStatus(DEVICE_UUID)

            assertEquals(false, isLocked)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `status refresh of another device is not debounced`() =
        runTest {
            // 「全デバイス」対象のタップは登録台数ぶん飛ぶのが意図した動作（BL-148）。
            val otherUuid = "other-uuid"
            credentialsList = listOf(validCredentials, validCredentials.copy(uuid = otherUuid))
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))
            val executor = createExecutor()

            executor.refreshStatus(DEVICE_UUID)
            executor.refreshStatus(otherUuid)

            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a debounced status refresh does not record a failure`() =
        runTest {
            server.enqueue(MockResponse().setBody("{}").setResponseCode(HTTP_FORBIDDEN))
            server.enqueue(MockResponse().setBody(statusJson("locked")).setResponseCode(HTTP_OK))
            val executor = createExecutor()

            executor.refreshStatus(DEVICE_UUID)
            val recordedFailure = lockStateStore.load(DEVICE_UUID)?.lastFailure
            now += 1_000L
            executor.refreshStatus(DEVICE_UUID)

            // 2回目は抑止されたので、1回目の失敗の記録がそのまま残る（上書きも消去もしない）。
            assertEquals(SesameStatusFailure.AUTH_OR_QUOTA, recordedFailure)
            assertEquals(recordedFailure, lockStateStore.load(DEVICE_UUID)?.lastFailure)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `refresh status without credentials returns null without calling the API`() =
        runTest {
            credentialsList = emptyList()

            assertNull(createExecutor().refreshStatus(DEVICE_UUID))
            assertEquals(0, server.requestCount)
        }

    // --- BLE経路の選択（BL-152） ---

    /**
     * BLE経路を差し替えた[SesameBleAccess]を組み立てる。
     * [bleSucceeds]はBLEでの施錠/解錠の成否、[bleStatus]はBLEで取得できる施錠状態
     * （nullなら取得できなかった扱い）、[probeFindsDevice]は到達確認のスキャンで見つかるかを表す。
     */
    private fun bleAccess(
        reachability: SesameBleReachability,
        bleSucceeds: Boolean = true,
        bleStatus: Boolean? = true,
        probeFindsDevice: Boolean = false,
        routePolicy: SesameRoutePolicy = SesameRoutePolicy.AUTO,
    ) = SesameBleAccess(
        reachability = reachability,
        routePolicy = { routePolicy },
        operations =
            SesameBleOperations(
                execute = { _, _ ->
                    bleAttempts++
                    if (bleSucceeds) {
                        SesameStatusMeasurement(
                            batteryPercentage = BLE_BATTERY_PERCENTAGE,
                            route = SesameStatusRoute.BLE,
                        )
                    } else {
                        null
                    }
                },
                fetchStatus = { _ ->
                    bleAttempts++
                    bleStatus?.let {
                        SesameStatusReading(
                            isLocked = it,
                            measurement =
                                SesameStatusMeasurement(
                                    batteryPercentage = BLE_BATTERY_PERCENTAGE,
                                    position = BLE_POSITION,
                                    route = SesameStatusRoute.BLE,
                                ),
                        )
                    }
                },
                probeReachable = { _ ->
                    probes++
                    probeFindsDevice
                },
            ),
        logRoute = { message -> routeLogs += message },
        onFallbackToWebApi = { fallbackNotices++ },
    )

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
                createExecutor(bleAccess = bleAccess(reachability, bleSucceeds = false))
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

            createExecutor(bleAccess = bleAccess(reachability, bleSucceeds = false))
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

    private fun lockStatesOf(records: List<Pair<String, SesameStatusSnapshot>>) =
        records.map { (uuid, snapshot) -> uuid to snapshot.isLocked }

    private fun statusJson(lockStatus: String) =
        """{"batteryVoltage":5.8,"position":11,"CHSesame2Status":"$lockStatus"}"""

    private companion object {
        const val DEVICE_UUID = "test-uuid"
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val BLE_BATTERY_PERCENTAGE = 85
        const val BLE_POSITION = 42

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

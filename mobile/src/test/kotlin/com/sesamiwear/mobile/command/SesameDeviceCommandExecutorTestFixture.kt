package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusReading
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.mobile.ble.SesameBleAttempt
import com.sesamiwear.mobile.ble.SesameBleFailure
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.messaging.CommandDebouncer
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before

/**
 * [SesameDeviceCommandExecutor]のテストで共有する土台。
 *
 * モックのSesame API（[MockWebServer]）・ロック状態の保存先・BLE経路の差し替えをまとめて持つ。
 * 1つのテストクラスへ全ケースを置くとdetektの`LargeClass`に触れるため、
 * BLE経路の選択（[SesameDeviceCommandExecutorBleRouteTest]）、「更新」でのBLE確認
 * （[SesameDeviceCommandExecutorStatusCheckTest]）とそれ以外を別クラスへ分けている。
 */
abstract class SesameDeviceCommandExecutorTestFixture {
    protected lateinit var server: MockWebServer
    protected lateinit var lockStateStore: LockStateStore
    protected val notifications = mutableListOf<Pair<String, SesameStatusSnapshot>>()
    protected val watchSyncs = mutableListOf<Pair<String, SesameStatusSnapshot>>()
    protected var now = 10_000L
    protected var credentialsList = listOf(validCredentials)
    protected val failureLogs = mutableListOf<String>()
    protected val routeLogs = mutableListOf<String>()
    protected var apiCalls = 0
    protected var bleAttempts = 0
    protected var probes = 0
    protected var fallbackNotices = 0

    /** 端末の「バックグラウンドデータの制限」が掛かっているとして扱うか（BL-192）。 */
    protected var backgroundDataRestricted = false

    /** BLEをいま使えるか（権限とBluetoothの有効状態、BL-204）。[bleAccess]の`isAvailable`が返す。 */
    protected var bleAvailable = true

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

    protected fun createExecutor(
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
                        backgroundDataRestricted = { backgroundDataRestricted },
                    ),
                ble = bleAccess,
            ),
        guard = SesameCommandGuard(debouncer = debouncer),
        nowMillis = { now },
    )

    protected fun bleAccess(
        reachability: SesameBleReachability,
        bleFailure: SesameBleFailure? = null,
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
                    if (bleFailure == null) {
                        SesameBleAttempt.succeeded(
                            SesameStatusMeasurement(
                                batteryPercentage = BLE_BATTERY_PERCENTAGE,
                                route = SesameStatusRoute.BLE,
                            ),
                        )
                    } else {
                        SesameBleAttempt.failed(bleFailure)
                    }
                },
                fetchStatus = { _ ->
                    bleAttempts++
                    bleStatus?.let {
                        SesameBleAttempt.succeeded(
                            SesameStatusReading(
                                isLocked = it,
                                measurement =
                                    SesameStatusMeasurement(
                                        batteryPercentage = BLE_BATTERY_PERCENTAGE,
                                        position = BLE_POSITION,
                                        route = SesameStatusRoute.BLE,
                                    ),
                            ),
                        )
                    } ?: SesameBleAttempt.failed(bleFailure ?: SesameBleFailure.NOT_REACHED)
                },
                probeReachable = { _ ->
                    probes++
                    probeFindsDevice
                },
                isAvailable = { bleAvailable },
            ),
        logRoute = { message -> routeLogs += message },
        onFallbackToWebApi = { fallbackNotices++ },
    )

    protected fun lockStatesOf(records: List<Pair<String, SesameStatusSnapshot>>) =
        records.map { (uuid, snapshot) -> uuid to snapshot.isLocked }

    protected fun statusJson(lockStatus: String) =
        """{"batteryVoltage":5.8,"position":11,"CHSesame2Status":"$lockStatus"}"""

    companion object {
        const val DEVICE_UUID = "test-uuid"
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_SERVER_ERROR = 500
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

package com.sesamiwear.mobile.command

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusReading
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameApiFailureLog
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.mobile.ble.SesameBleAddressCache
import com.sesamiwear.mobile.ble.SesameBleClient
import com.sesamiwear.mobile.ble.SesameBlePermissions
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.ble.SesameRouteChangeTracker
import com.sesamiwear.mobile.ble.SesameRoutePolicyStore
import com.sesamiwear.mobile.ble.toAttempt
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import com.sesamiwear.mobile.diagnostics.DiagnosticsLogFactory
import com.sesamiwear.mobile.messaging.SesameStatusSyncer
import com.sesamiwear.mobile.network.BackgroundDataRestriction
import com.sesamiwear.mobile.notification.SesameRouteNotifier
import com.sesamiwear.mobile.showcase.ShowcaseMode
import com.sesamiwear.mobile.state.ApiUsageCounter
import com.sesamiwear.mobile.state.LockStateStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import com.sesamiwear.mobile.widget.SesameWidgetUpdater

/**
 * [SesameDeviceCommandExecutor]へAndroid依存の保存先と通知先をつないで生成する（BL-120）。
 * 資格情報は暗号化ストア、ロック状態は非暗号化SharedPreferencesから読み、状態変化はウォッチへ
 * DataItemとしてベストエフォートで同期し（BL-118）、ホーム画面ウィジェットの再描画を要求する（BL-122）。
 * ウォッチ経由・ウィジェット経由のどちらで成功しても、ウォッチのTileとウィジェットの双方が追随する。
 * 重複抑止は共有インスタンスを使う。Sesame APIの失敗はlogcatの`Log.w`へ1行だけ残し（BL-139）、
 * 呼び出し回数は[ApiUsageCounter]へ月単位で記録する（BL-147）。
 * 施錠/解錠・状態取得の成否は診断ログ（BL-188）へも残す（`SesameCommandDiagnostics`）。
 *
 * BLE直接操作（BL-152）もここで配線する。到達実績は非暗号化のSharedPreferencesへ保存し、
 * 実行時間の上限は[BLE_TIMEOUTS]で与える。BLEが使えない（権限が無い・圏外・Bluetoothオフ）場合は
 * 失敗を返すだけで、[SesameDeviceCommandExecutor]が従来どおりWeb APIへ倒す。
 *
 * 生成の配線だけを行う薄いアダプタのためユニットテスト対象外（本体は[SesameDeviceCommandExecutor]でテスト済み）。
 */
object SesameDeviceCommandExecutorFactory {
    /**
     * 施錠/解錠・状態取得の実行口を返す。デバッグ版の撮影モード中は、実物へ一切通信しない
     * 撮影用の実行口を返す（BL-212、[ShowcaseMode]）。リリース版では常に実物の実行口になる。
     */
    fun create(context: Context): SesameDeviceCommands =
        ShowcaseMode.commandsOrNull(context.applicationContext) ?: createExecutor(context.applicationContext)

    /** 実物の実行口。保存先は撮影モードに関わらず実物のものを直接開く（[SesameDeviceStores]を通さない）。 */
    private fun createExecutor(appContext: Context): SesameDeviceCommandExecutor {
        val credentialsStore = SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(appContext))
        val apiUsageCounter = ApiUsageCounter(SharedPreferencesKeyValueStore.forApiUsage(appContext))
        return SesameDeviceCommandExecutor(
            loadCredentials = credentialsStore::loadAll,
            lockStateStore = LockStateStore(SharedPreferencesKeyValueStore.forLockState(appContext)),
            notifier =
                LockStateNotifier(
                    local = { _, _ -> SesameWidgetUpdater.updateAll(appContext) },
                    watch = { uuid, snapshot -> SesameStatusSyncer(appContext).sync(uuid, snapshot) },
                ),
            routes =
                SesameRouteAccess(
                    api =
                        SesameApiAccess(
                            // リリースビルドでも残る`Log.w`へ出す（`Log.d` / `Log.v`は除去される、BL-083 / BL-139）。
                            logFailure = { message -> Log.w(SesameApiFailureLog.TAG, message) },
                            recordApiCall = { apiUsageCounter.record(System.currentTimeMillis()) },
                            // 失敗の文言を端末設定側へ寄せるかの判断に使う（BL-192）。操作のたびに読む。
                            backgroundDataRestricted = { BackgroundDataRestriction.isRestricted(appContext) },
                        ),
                    ble = createBleAccess(appContext),
                ),
            // 利用者が連携できる診断ログ（BL-188）。uuidは渡さず、表示名だけを記録する。
            guard =
                SesameCommandGuard(
                    diagnostics =
                        SesameCommandDiagnostics(
                            loadCredentials = credentialsStore::loadAll,
                            lockStateStore = LockStateStore(SharedPreferencesKeyValueStore.forLockState(appContext)),
                            record = DiagnosticsLogFactory.create(appContext)::record,
                        ),
                ),
        )
    }

    private fun createBleAccess(appContext: Context): SesameBleAccess {
        val bleStore = SharedPreferencesKeyValueStore.forBleReachability(appContext)
        val client =
            SesameBleClient(appContext, timeouts = BLE_TIMEOUTS, addressCache = SesameBleAddressCache(bleStore))
        val policyStore = SesameRoutePolicyStore(bleStore)
        val routeChanges = SesameRouteChangeTracker(bleStore)
        val notifier = SesameRouteNotifier(appContext)
        return SesameBleAccess(
            reachability = SesameBleReachability(bleStore),
            // 設定は操作のたびに読み直す（設定画面で変えた直後から効かせるため）。
            routePolicy = policyStore::load,
            operations =
                SesameBleOperations(
                    execute = { credentials, command ->
                        val outcome = client.execute(credentials, command)
                        // どの段階で駄目だったかはBLEの結果にしか出ない。経路の成否（route=...）だけでは
                        // 探索で見つからないのか繋げないのかを切り分けられないため、理由も1行残す（BL-189）。
                        Log.w(SesameApiFailureLog.TAG, "route=BLE op=${command.name} detail=${outcome.result}")
                        // 角度はコマンド送信前の値になるため使わない（BL-166、CommandOutcomeのKDoc）。
                        // 失敗したときは理由を圏内／圏外へ落として渡す（BL-191）。
                        outcome.result.toAttempt(
                            outcome.status?.let {
                                SesameStatusMeasurement(
                                    batteryPercentage = it.batteryPercentage,
                                    route = SesameStatusRoute.BLE,
                                )
                            },
                        )
                    },
                    fetchStatus = { credentials ->
                        val statusResult = client.fetchStatus(credentials)
                        Log.w(SesameApiFailureLog.TAG, "route=BLE op=STATUS detail=${statusResult.result}")
                        statusResult.result.toAttempt(
                            statusResult.status?.let {
                                SesameStatusReading(
                                    isLocked = it.isInLockRange,
                                    measurement =
                                        SesameStatusMeasurement(
                                            batteryPercentage = it.batteryPercentage,
                                            position = it.position,
                                            route = SesameStatusRoute.BLE,
                                        ),
                                )
                            },
                        )
                    },
                    probeReachable = { credentials -> client.probeReachable(credentials.uuid) },
                    isAvailable = { SesameBlePermissions.hasAll(appContext) && isBluetoothEnabled(appContext) },
                ),
            logRoute = { message -> Log.w(SesameApiFailureLog.TAG, message) },
            // トーストは前景でしか出ない（背景からのトーストは通知が無効な端末で抑止される、BL-190）。
            // 前景で操作しているときの即時性のために残し、本命の通知は[onRouteUsed]で出す。
            onFallbackToWebApi = { showToast(appContext, SesameRouteLabel.FALLBACK_MESSAGE) },
            onRouteUsed = { credentials, route ->
                if (routeChanges.onRouteUsed(credentials.uuid, route)) {
                    notifier.notifyRouteChange(credentials.displayName.ifBlank { UNNAMED_DEVICE }, route)
                }
            },
        )
    }

    /**
     * 経路が切り替わったことを利用者へ伝えるトースト（BL-168）。
     *
     * ウィジェットのタップもウォッチからのコマンドもバックグラウンドのコンポーネントで動くため、
     * メインスレッドへ渡し直してから表示する。通知（Notification）ではなくトーストにしているのは、
     * Android 13以降で`POST_NOTIFICATIONS`権限の要求が増え、データセーフティ申告（BL-154）も
     * 増えるため（トーストは追加の権限が要らない）。
     */
    private fun showToast(
        context: Context,
        message: String,
    ) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * BLEの各段階に与える上限（BL-152 / BL-189）。
     *
     * 2026-09-20の実機検証（BL-165、Pixel 8 Pro + Sesame 5）の実測で見直した。
     * 旧値（合計1,800ms / 探索900ms / 到達確認1,500ms）は実測に基づかない見積もりで、
     * **BLE1往復の実測2.0〜3.5秒に対して短すぎ、毎回打ち切られていた**
     * （`route=BLE op=STATUS result=NG`。到達確認もSesameの真横で6回中2回しか成功しなかった）。
     *
     * 支配的なのは探索（スキャン）なので、時間の与え方を作り直す。
     *
     * - 利用者を待たせる経路では**探索しない**。[SesameBleAddressCache]が覚えたアドレスへ
     *   直接接続するだけにし、接続・ログイン・コマンドへ合計2.6秒を与える。
     * - 探索は、Web APIの通信と並行して走る到達確認（[probeMillis]）が担う。並行して待つ相手が
     *   最大6秒（`SesameApiClient`のcallTimeout）あるため、4秒まで伸ばしても利用者の待ちは増えない。
     * - 到達確認は、探索が外れたときに最後に成功したアドレスへ直接つないで確かめる（BL-193）。
     *   その接続ぶん（[probeConnectMillis]）は探索から差し引き、到達確認全体は4秒のままにする
     *   （到達確認はWeb APIと並行するが完了を待つため、伸ばすと利用者の待ちも伸びる）。
     * - [scanMillis]は、保存済みアドレスが無い・古い場合の保険として残す（通常は到達確認が先に
     *   アドレスを用意するため、この経路は通らない）。
     *
     * ウィジェットのタップは`WidgetCommandReceiver`の上限（9秒、BroadcastReceiverの約10秒より内側）で
     * 打ち切られる。BLEを試して倒れても 3.0 + 6.0 = 9.0秒で、上限と同じところに収まる。
     * 2,600msでは接続・ログインが間に合わない試行があった（`detail=CONNECTION_FAILED` /
     * `detail=LOGIN_FAILED`、2026-09-20の再検証）ため、余裕をこちらへ寄せている。
     * 各段階の上限の合計は全体より大きいが、拘束力を持つのは全体の[SesameBleClient.Timeouts.totalMillis]。
     */
    private val BLE_TIMEOUTS =
        SesameBleClient.Timeouts(
            totalMillis = 3_000,
            scanMillis = 1_200,
            connectMillis = 1_600,
            loginMillis = 1_200,
            commandMillis = 800,
            probeMillis = 4_000,
            probeConnectMillis = 1_500,
        )

    /**
     * Bluetoothが有効か（BL-204）。無効な端末で「更新」のたびにBLEを試すと、必ず失敗するうえ
     * フォールバックの通知が出るため、試す前に確かめる。非対応の端末ではfalse。
     */
    private fun isBluetoothEnabled(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    /** 表示名が空のデバイスを通知で指すときの呼び名。 */
    private const val UNNAMED_DEVICE = "セサミ"
}

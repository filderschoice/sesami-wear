package com.sesamiwear.mobile.command

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
import com.sesamiwear.mobile.ble.SesameBleClient
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.ble.SesameRoutePolicyStore
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import com.sesamiwear.mobile.messaging.SesameStatusSyncer
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
 *
 * BLE直接操作（BL-152）もここで配線する。到達実績は非暗号化のSharedPreferencesへ保存し、
 * 実行時間の上限は[BLE_TIMEOUTS]で与える。BLEが使えない（権限が無い・圏外・Bluetoothオフ）場合は
 * 失敗を返すだけで、[SesameDeviceCommandExecutor]が従来どおりWeb APIへ倒す。
 *
 * 生成の配線だけを行う薄いアダプタのためユニットテスト対象外（本体は[SesameDeviceCommandExecutor]でテスト済み）。
 */
object SesameDeviceCommandExecutorFactory {
    fun create(context: Context): SesameDeviceCommandExecutor {
        val appContext = context.applicationContext
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
                        ),
                    ble = createBleAccess(appContext),
                ),
        )
    }

    private fun createBleAccess(appContext: Context): SesameBleAccess {
        val client = SesameBleClient(appContext, timeouts = BLE_TIMEOUTS)
        val policyStore = SesameRoutePolicyStore(SharedPreferencesKeyValueStore.forBleReachability(appContext))
        return SesameBleAccess(
            reachability = SesameBleReachability(SharedPreferencesKeyValueStore.forBleReachability(appContext)),
            // 設定は操作のたびに読み直す（設定画面で変えた直後から効かせるため）。
            routePolicy = policyStore::load,
            operations =
                SesameBleOperations(
                    execute = { credentials, command ->
                        val outcome = client.execute(credentials, command)
                        // 角度はコマンド送信前の値になるため使わない（BL-166、CommandOutcomeのKDoc）。
                        outcome.status
                            ?.takeIf { outcome.result == SesameBleClient.Result.SUCCESS }
                            ?.let {
                                SesameStatusMeasurement(
                                    batteryPercentage = it.batteryPercentage,
                                    route = SesameStatusRoute.BLE,
                                )
                            }
                    },
                    fetchStatus = { credentials ->
                        client.fetchStatus(credentials).status?.let {
                            SesameStatusReading(
                                isLocked = it.isInLockRange,
                                measurement =
                                    SesameStatusMeasurement(
                                        batteryPercentage = it.batteryPercentage,
                                        position = it.position,
                                        route = SesameStatusRoute.BLE,
                                    ),
                            )
                        }
                    },
                    probeReachable = { credentials -> client.probeReachable(credentials.uuid) },
                ),
            logRoute = { message -> Log.w(SesameApiFailureLog.TAG, message) },
            onFallbackToWebApi = { showToast(appContext, SesameRouteLabel.FALLBACK_MESSAGE) },
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
     * BLEの各段階に与える上限（BL-152）。
     *
     * ウィジェットのタップは`WidgetCommandReceiver`の8秒で打ち切られ、Web APIの呼び出しだけで
     * 最大6秒かかる（`SesameApiClient`のcallTimeout）。BLEを試してから倒れても8秒に収まるよう、
     * BLE側の合計を約1.8秒に抑える。DESIGN.mdの「探索・接続に与える上限は合計2秒程度」を満たす。
     * 各段階の上限の合計は全体より大きいが、拘束力を持つのは全体の[SesameBleClient.Timeouts.totalMillis]。
     * BLEを先に試すのは直近に到達できたデバイスだけのため、通常はこの上限に達しない。
     * 実測に基づく値ではないため、BL-165（人手検証）の所要時間の実測で見直す。
     */
    private val BLE_TIMEOUTS =
        SesameBleClient.Timeouts(
            totalMillis = 1_800,
            scanMillis = 900,
            connectMillis = 700,
            loginMillis = 700,
            commandMillis = 600,
            probeMillis = 1_500,
        )
}

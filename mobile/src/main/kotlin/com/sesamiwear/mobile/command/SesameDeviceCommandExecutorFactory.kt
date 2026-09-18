package com.sesamiwear.mobile.command

import android.content.Context
import android.util.Log
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.api.SesameApiFailureLog
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
            apiAccess =
                SesameApiAccess(
                    // リリースビルドでも残る`Log.w`へ出す（`Log.d` / `Log.v`は除去される、BL-083 / BL-139）。
                    logFailure = { message -> Log.w(SesameApiFailureLog.TAG, message) },
                    recordApiCall = { apiUsageCounter.record(System.currentTimeMillis()) },
                ),
        )
    }
}

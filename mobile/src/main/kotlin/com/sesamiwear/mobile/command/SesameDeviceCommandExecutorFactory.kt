package com.sesamiwear.mobile.command

import android.content.Context
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import com.sesamiwear.mobile.messaging.SesameStatusSyncer
import com.sesamiwear.mobile.state.LockStateStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * [SesameDeviceCommandExecutor]へAndroid依存の保存先と通知先をつないで生成する（BL-120）。
 * 資格情報は暗号化ストア、ロック状態は非暗号化SharedPreferencesから読み、状態変化はウォッチへ
 * DataItemとしてベストエフォートで同期する（BL-118）。重複抑止は共有インスタンスを使う。
 * 生成の配線だけを行う薄いアダプタのためユニットテスト対象外（本体は[SesameDeviceCommandExecutor]でテスト済み）。
 */
object SesameDeviceCommandExecutorFactory {
    fun create(context: Context): SesameDeviceCommandExecutor {
        val appContext = context.applicationContext
        val credentialsStore = SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(appContext))
        return SesameDeviceCommandExecutor(
            loadCredentials = credentialsStore::loadAll,
            lockStateStore = LockStateStore(SharedPreferencesKeyValueStore.forLockState(appContext)),
            listener = { uuid, isLocked -> SesameStatusSyncer(appContext).syncLocked(uuid, isLocked) },
        )
    }
}

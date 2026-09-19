package com.sesamiwear.mobile.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BLE直接操作を単体で試すための、**デバッグビルドにだけ存在する**隠し入口（BL-151）。
 *
 * BLEには`-PsesameApiBaseUrl`のようなモック差し替えが存在せず、検証は実機必須になる
 * （DESIGN.md「BLE直接操作の併用方針」段階2）。経路の自動切り替え（BL-152）を入れる前に
 * BLE単体の成否を切り分けられるよう、adbから直接叩ける入口を用意する。
 *
 * 本ファイルは`src/debug`にあるためリリースビルドへは一切含まれない（マニフェストの宣言も同様）。
 * 施錠/解錠を外部から起動できる入口のため、`src/main`へ移してはならない。
 *
 * 使い方（`<uuid>`は資格情報に登録済みのもの。secretKeyは端末内の暗号化ストアから読むため渡さない）:
 *
 * ```
 * adb shell am broadcast -a com.sesamiwear.mobile.debug.BLE \
 *   -n com.sesamiwear.mobile.debug/com.sesamiwear.mobile.ble.SesameBleDebugReceiver \
 *   --es uuid <uuid> --es op status
 * ```
 *
 * `--es op`は`status` / `lock` / `unlock`。結果は`adb logcat -s SesameBleDebug`で読む。
 * 資格情報・セッション鍵・BLEアドレスはログへ出さない（rules/guardrails-unified.v1.md 3.3 / 12.5）。
 */
class SesameBleDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val uuid = intent.getStringExtra(EXTRA_UUID)
        val operation = intent.getStringExtra(EXTRA_OPERATION) ?: OPERATION_STATUS
        if (uuid.isNullOrBlank()) {
            Log.w(TAG, "uuid extra is required")
            return
        }
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                run(appContext, uuid, operation)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun run(
        context: Context,
        uuid: String,
        operation: String,
    ) {
        val store = SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(context))
        val credentials = store.loadAll().find { it.uuid == uuid }
        if (credentials == null) {
            Log.w(TAG, "no credentials registered for the requested device")
            return
        }
        val client = SesameBleClient(context)
        when (operation) {
            OPERATION_LOCK -> Log.w(TAG, "lock result=${client.execute(credentials, SesameCommand.LOCK)}")
            OPERATION_UNLOCK -> Log.w(TAG, "unlock result=${client.execute(credentials, SesameCommand.UNLOCK)}")
            OPERATION_STATUS -> logStatus(client, credentials.uuid, client.fetchStatus(credentials))
            else -> Log.w(TAG, "unknown op=$operation (expected status/lock/unlock)")
        }
    }

    private fun logStatus(
        client: SesameBleClient,
        uuid: String,
        statusResult: SesameBleClient.StatusResult,
    ) {
        val status = statusResult.status
        Log.w(
            TAG,
            buildString {
                append("status result=${statusResult.result}")
                append(" uuidLength=${uuid.length}")
                append(" client=${client.javaClass.simpleName}")
                if (status != null) {
                    append(" isInLockRange=${status.isInLockRange}")
                    append(" isInUnlockRange=${status.isInUnlockRange}")
                    append(" position=${status.position}")
                    append(" batteryPercentage=${status.batteryPercentage}")
                }
            },
        )
    }

    private companion object {
        const val TAG = "SesameBleDebug"
        const val EXTRA_UUID = "uuid"
        const val EXTRA_OPERATION = "op"
        const val OPERATION_STATUS = "status"
        const val OPERATION_LOCK = "lock"
        const val OPERATION_UNLOCK = "unlock"
    }
}

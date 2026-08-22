package com.sesamiwear.mobile.messaging

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Wear側からのlock/unlockメッセージを受信し、[SesameCommandHandler]で実行して結果を返す。
 * 資格情報（BL-005で実装した[SesameCredentialsStore]）が未設定の場合は[SesameCommandResult.FAILURE]を返す。
 * MessageClient呼び出し以外のロジックを持たない薄いアダプタのためユニットテスト対象外
 * （ハンドラ本体は[SesameCommandHandler]でテスト済み、実際の送受信動作はBL-011で人手検証）。
 */
class SesameMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            val deviceUuid = SesameWearProtocol.decodeDeviceUuid(messageEvent.data)
            val handler = createHandler(applicationContext, deviceUuid)
            val result = handler?.handle(messageEvent.path) ?: SesameCommandResult.FAILURE
            if (result == SesameCommandResult.SUCCESS) {
                syncLockedStateFromPath(messageEvent.path, deviceUuid)
            }
            Wearable.getMessageClient(this@SesameMessageListenerService)
                .sendMessage(messageEvent.sourceNodeId, SesameWearProtocol.PATH_COMMAND_RESULT, result.toPayload())
                .await()
        }
    }

    private fun createHandler(
        context: Context,
        deviceUuid: String,
    ): SesameCommandHandler? {
        val credentialsStore = SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(context))
        val credentials = credentialsStore.loadAll().find { it.uuid == deviceUuid }
        // secretKeyBytesOrNullを使い、不正なBase64/鍵長の資格情報が保存されていても例外で
        // クラッシュせずFAILUREへフォールバックする（BL-026、コードレビューで発見）。
        val secretKeyBytes = credentials?.secretKeyBytesOrNull
        return if (credentials != null && secretKeyBytes != null) {
            val apiClient = SesameApiClient(uuid = credentials.uuid, apiKey = credentials.apiKey)
            SesameCommandHandler(apiClient, secretKeyBytes)
        } else {
            null
        }
    }

    private suspend fun syncLockedStateFromPath(
        path: String,
        deviceUuid: String,
    ) {
        val isLocked =
            when (path) {
                SesameWearProtocol.PATH_LOCK_REQUEST -> true
                SesameWearProtocol.PATH_UNLOCK_REQUEST -> false
                else -> return
            }
        SesameStatusSyncer(applicationContext).syncLocked(deviceUuid, isLocked)
    }
}

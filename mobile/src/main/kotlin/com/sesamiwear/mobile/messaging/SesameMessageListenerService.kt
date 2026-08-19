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
            val handler = createHandler(applicationContext)
            val result = handler?.handle(messageEvent.path) ?: SesameCommandResult.FAILURE
            Wearable.getMessageClient(this@SesameMessageListenerService)
                .sendMessage(messageEvent.sourceNodeId, SesameWearProtocol.PATH_COMMAND_RESULT, result.toPayload())
                .await()
        }
    }

    private fun createHandler(context: Context): SesameCommandHandler? {
        val credentialsStore = SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(context))
        val credentials = credentialsStore.load() ?: return null
        val apiClient = SesameApiClient(uuid = credentials.uuid, apiKey = credentials.apiKey)
        return SesameCommandHandler(apiClient, credentials.secretKeyBytes)
    }
}

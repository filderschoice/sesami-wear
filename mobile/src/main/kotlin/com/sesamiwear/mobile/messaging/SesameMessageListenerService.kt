package com.sesamiwear.mobile.messaging

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameApiException
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Wear側からのlock/unlock/状態取得リクエストを受信する。lock/unlockは[SesameCommandHandler]で
 * 実行して結果を返す。状態取得リクエスト（[SesameWearProtocol.PATH_STATUS_REQUEST]、BL-061）は
 * Sesame APIのGETを呼び、成功時に[SesameStatusSyncer]でDataItemへ同期するのみで
 * 結果をWear側へ返送しない（Tile/Complicationの初回「状態不明」表示を解消するための非同期更新）。
 * 資格情報（BL-005で実装した[SesameCredentialsStore]）が未設定の場合はlock/unlockでは
 * [SesameCommandResult.FAILURE]を返し、状態取得リクエストでは何もしない。
 * lock/unlockは[CommandDebouncer]で同一デバイスへの短時間内の重複を無視する（BL-062、
 * Tile連打による二重送信・ハプティクス連続再生の防止）。
 * MessageClient呼び出し以外のロジックを持たない薄いアダプタのためユニットテスト対象外
 * （ハンドラ本体は[SesameCommandHandler]でテスト済み、実際の送受信動作はBL-011で人手検証）。
 */
class SesameMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            if (messageEvent.path == SesameWearProtocol.PATH_STATUS_REQUEST) {
                handleStatusRequest(messageEvent)
            } else {
                handleCommandRequest(messageEvent)
            }
        }
    }

    private suspend fun handleCommandRequest(messageEvent: MessageEvent) {
        val deviceUuid = SesameWearProtocol.decodeDeviceUuid(messageEvent.data)
        if (!commandDebouncer.shouldProcess(deviceUuid)) return
        val handler = createCommandHandler(applicationContext, deviceUuid)
        val result = handler?.handle(messageEvent.path) ?: SesameCommandResult.FAILURE
        if (result == SesameCommandResult.SUCCESS) {
            syncLockedStateFromPath(messageEvent.path, deviceUuid)
        }
        Wearable.getMessageClient(this@SesameMessageListenerService)
            .sendMessage(messageEvent.sourceNodeId, SesameWearProtocol.PATH_COMMAND_RESULT, result.toPayload())
            .await()
    }

    private suspend fun handleStatusRequest(messageEvent: MessageEvent) {
        val deviceUuid = SesameWearProtocol.decodeDeviceUuid(messageEvent.data)
        val credentials = findCredentials(applicationContext, deviceUuid) ?: return
        val apiClient = SesameApiClient(uuid = credentials.uuid, apiKey = credentials.apiKey)
        val status =
            try {
                apiClient.getStatus()
            } catch (
                @Suppress("SwallowedException") e: SesameApiException,
            ) {
                return
            }
        SesameStatusSyncer(applicationContext).syncLocked(deviceUuid, status.isInLockRange)
    }

    private fun findCredentials(
        context: Context,
        deviceUuid: String,
    ): SesameCredentials? {
        val credentialsStore = SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(context))
        return credentialsStore.loadAll().find { it.uuid == deviceUuid }
    }

    private fun createCommandHandler(
        context: Context,
        deviceUuid: String,
    ): SesameCommandHandler? {
        val credentials = findCredentials(context, deviceUuid)
        // secretKeyBytesOrNullを使い、不正な16進数/鍵長の資格情報が保存されていても例外で
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

    private companion object {
        // Serviceインスタンスをまたいで連打を検知できるよう、companion objectで保持する
        // （BL-062、Tile連打による二重送信・ハプティクス連続再生の防止）。
        val commandDebouncer = CommandDebouncer()
    }
}

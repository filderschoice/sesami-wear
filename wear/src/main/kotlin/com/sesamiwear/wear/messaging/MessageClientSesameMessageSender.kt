package com.sesamiwear.wear.messaging

import com.google.android.gms.wearable.MessageClient
import com.sesamiwear.core.SesameMessageSender
import kotlinx.coroutines.tasks.await

/**
 * [SesameMessageSender] のGoogle Play Services Wearable API実装アダプタ。
 * MessageClient呼び出し以外のロジックを持たない薄いラッパーのため、ユニットテスト対象外。
 * 実際の送受信動作はBL-011（実機、Pixel Watch + Sesame 5）で人手検証する。
 */
class MessageClientSesameMessageSender(private val messageClient: MessageClient) : SesameMessageSender {
    override suspend fun send(
        nodeId: String,
        path: String,
        payload: ByteArray,
    ) {
        messageClient.sendMessage(nodeId, path, payload).await()
    }
}

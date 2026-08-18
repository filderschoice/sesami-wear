package com.sesamiwear.wear.messaging

import com.sesamiwear.core.SesameMessageSender
import com.sesamiwear.core.SesameWearProtocol

/**
 * Wear側からMobile側へ施錠/解錠の意図のみを送信する。
 * secretKeyは保持せず、コマンド種別（メッセージパス）のみを送る（PLAN.mdのアーキテクチャ方針）。
 */
class SesameCommandSender(private val messageSender: SesameMessageSender) {
    suspend fun requestLock(nodeId: String) {
        messageSender.send(nodeId, SesameWearProtocol.PATH_LOCK_REQUEST, ByteArray(0))
    }

    suspend fun requestUnlock(nodeId: String) {
        messageSender.send(nodeId, SesameWearProtocol.PATH_UNLOCK_REQUEST, ByteArray(0))
    }
}

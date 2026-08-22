package com.sesamiwear.wear.messaging

import com.sesamiwear.core.SesameMessageSender
import com.sesamiwear.core.SesameWearProtocol

/**
 * Wear側からMobile側へ施錠/解錠の意図のみを送信する。
 * secretKeyは保持せず、コマンド種別（メッセージパス）と対象デバイスのuuid（ペイロード、BL-053）
 * のみを送る（PLAN.mdのアーキテクチャ方針）。
 */
class SesameCommandSender(private val messageSender: SesameMessageSender) {
    suspend fun requestLock(
        nodeId: String,
        deviceUuid: String,
    ) {
        messageSender.send(
            nodeId,
            SesameWearProtocol.PATH_LOCK_REQUEST,
            SesameWearProtocol.encodeDeviceUuid(deviceUuid),
        )
    }

    suspend fun requestUnlock(
        nodeId: String,
        deviceUuid: String,
    ) {
        messageSender.send(
            nodeId,
            SesameWearProtocol.PATH_UNLOCK_REQUEST,
            SesameWearProtocol.encodeDeviceUuid(deviceUuid),
        )
    }
}

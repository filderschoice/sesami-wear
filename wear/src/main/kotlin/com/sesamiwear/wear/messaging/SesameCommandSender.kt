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

    /**
     * 対象デバイスの最新状態をSesame APIから取得しDataItemへ同期するようmobile側へ依頼する
     * （BL-061）。Fire-and-forgetで送信し、結果はDataItemの変更として非同期に届く
     * （[com.sesamiwear.wear.messaging.SesameStatusListenerService]参照）。
     */
    suspend fun requestStatus(
        nodeId: String,
        deviceUuid: String,
    ) {
        messageSender.send(
            nodeId,
            SesameWearProtocol.PATH_STATUS_REQUEST,
            SesameWearProtocol.encodeDeviceUuid(deviceUuid),
        )
    }

    /**
     * 「Bluetooth接続を確認」（BL-273）の開始・接続・停止をmobile側へ依頼する。ウォッチ自身はBLEを使わず、
     * スマホが探索・接続を実行して進捗をメッセージで返す（[SesameWearProtocol.PATH_BLE_PROBE_PROGRESS]）。
     * [path]は[SesameWearProtocol.PATH_BLE_PROBE_START] / [SesameWearProtocol.PATH_BLE_PROBE_CONNECT] /
     * [SesameWearProtocol.PATH_BLE_PROBE_STOP]のいずれか。
     */
    suspend fun requestBleProbe(
        nodeId: String,
        path: String,
        deviceUuid: String,
    ) {
        messageSender.send(nodeId, path, SesameWearProtocol.encodeDeviceUuid(deviceUuid))
    }
}

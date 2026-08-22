package com.sesamiwear.core

/**
 * Wear (MessageClient) 側と Mobile 側で共有するメッセージパスとペイロードキーの定義。
 * 実際の送受信処理は mobile / wear の各モジュールで実装する（BL-006）。
 */
object SesameWearProtocol {
    const val PATH_LOCK_REQUEST = "/sesami-wear/lock"
    const val PATH_UNLOCK_REQUEST = "/sesami-wear/unlock"
    const val PATH_COMMAND_RESULT = "/sesami-wear/result"

    /**
     * DataClient（DataItem）でMobile側からWear側へ最新のロック状態を同期するためのパス・キー（BL-015）。
     */
    const val STATUS_DATA_ITEM_PATH = "/sesami-wear/status"
    const val KEY_IS_LOCKED = "is_locked"
    const val KEY_UPDATED_AT_EPOCH_MILLIS = "updated_at_epoch_millis"

    /**
     * 施錠/解錠コマンドのメッセージペイロードへ、操作対象デバイスのuuid（[SesameCredentials.uuid]と
     * 対応する識別子）を載せるためのエンコード/デコード（BL-048、複数Sesameデバイス対応）。
     * uuidをそのままUTF-8バイト列化するだけの単純な形式で、JSON等は使わない。
     */
    fun encodeDeviceUuid(uuid: String): ByteArray = uuid.toByteArray(Charsets.UTF_8)

    fun decodeDeviceUuid(payload: ByteArray): String = String(payload, Charsets.UTF_8)

    /**
     * デバイスごとにロック状態を区別して同期するためのDataItemパス（BL-050）。
     * [STATUS_DATA_ITEM_PATH]をプレフィックスとし、対象デバイスのuuidを付与して一意にする。
     */
    fun statusDataItemPath(uuid: String): String = "$STATUS_DATA_ITEM_PATH/$uuid"
}

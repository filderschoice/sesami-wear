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
}

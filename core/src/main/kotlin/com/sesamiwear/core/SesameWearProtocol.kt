package com.sesamiwear.core

/**
 * Wear (MessageClient) 側と Mobile 側で共有するメッセージパスとペイロードキーの定義。
 * 実際の送受信処理は mobile / wear の各モジュールで実装する（BL-006）。
 */
object SesameWearProtocol {
    const val PATH_LOCK_REQUEST = "/sesami-wear/lock"
    const val PATH_UNLOCK_REQUEST = "/sesami-wear/unlock"
    const val PATH_COMMAND_RESULT = "/sesami-wear/result"
}

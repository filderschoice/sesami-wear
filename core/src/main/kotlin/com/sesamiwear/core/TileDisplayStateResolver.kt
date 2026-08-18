package com.sesamiwear.core

/**
 * スマホ接続状態・コマンド実行中フラグ・最後に取得したロック状態から、
 * Tileが表示すべき状態を決定する。
 */
object TileDisplayStateResolver {
    fun resolve(
        isPhoneConnected: Boolean,
        isCommandInProgress: Boolean,
        isLocked: Boolean?,
    ): TileDisplayState =
        when {
            !isPhoneConnected -> TileDisplayState.DISCONNECTED
            isCommandInProgress -> TileDisplayState.IN_PROGRESS
            isLocked == true -> TileDisplayState.LOCKED
            isLocked == false -> TileDisplayState.UNLOCKED
            else -> TileDisplayState.UNKNOWN
        }
}

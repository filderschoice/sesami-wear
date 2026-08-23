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

    /**
     * 複数デバイス一括操作（BL-071、「全デバイス」選択時）向けに、登録済み各デバイスの
     * ロック状態（[lockStates]、デバイスごとにnullは未取得を表す）から集約状態を決定する。
     * 1台でも未取得（null）があれば安全側に倒しUNKNOWNとする（実際には解錠中なのに
     * 「全施錠中」と誤表示することを避けるため）。
     */
    fun resolveAggregate(
        isPhoneConnected: Boolean,
        isCommandInProgress: Boolean,
        lockStates: List<Boolean?>,
    ): TileDisplayState =
        when {
            !isPhoneConnected -> TileDisplayState.DISCONNECTED
            isCommandInProgress -> TileDisplayState.IN_PROGRESS
            lockStates.isEmpty() || lockStates.any { it == null } -> TileDisplayState.UNKNOWN
            lockStates.all { it == true } -> TileDisplayState.LOCKED
            lockStates.all { it == false } -> TileDisplayState.UNLOCKED
            else -> TileDisplayState.MIXED
        }
}

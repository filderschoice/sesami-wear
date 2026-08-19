package com.sesamiwear.wear.tile

import com.sesamiwear.core.TileDisplayState

/**
 * [TileDisplayState] からTileの表示文言を決定する。Android非依存のためユニットテスト対象。
 */
object SesameTileContent {
    fun statusLabel(state: TileDisplayState): String =
        when (state) {
            TileDisplayState.LOCKED -> "施錠中"
            TileDisplayState.UNLOCKED -> "解錠中"
            TileDisplayState.IN_PROGRESS -> "通信中..."
            TileDisplayState.DISCONNECTED -> "スマホ未接続"
            TileDisplayState.UNKNOWN -> "状態不明"
        }

    fun actionLabel(state: TileDisplayState): String? =
        when (state) {
            TileDisplayState.LOCKED -> "タップで解錠"
            TileDisplayState.UNLOCKED -> "タップで施錠"
            else -> null
        }

    /**
     * 状態をひと目で区別できる背景色（ARGB）。施錠中=緑、解錠中=赤、通信中=黄、
     * それ以外（未接続・不明）=グレー。
     */
    fun backgroundColorArgb(state: TileDisplayState): Int =
        when (state) {
            TileDisplayState.LOCKED -> COLOR_LOCKED_GREEN
            TileDisplayState.UNLOCKED -> COLOR_UNLOCKED_RED
            TileDisplayState.IN_PROGRESS -> COLOR_IN_PROGRESS_AMBER
            TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN -> COLOR_NEUTRAL_GRAY
        }

    private const val COLOR_LOCKED_GREEN = 0xFF4CAF50.toInt()
    private const val COLOR_UNLOCKED_RED = 0xFFF44336.toInt()
    private const val COLOR_IN_PROGRESS_AMBER = 0xFFFFC107.toInt()
    private const val COLOR_NEUTRAL_GRAY = 0xFF9E9E9E.toInt()
}

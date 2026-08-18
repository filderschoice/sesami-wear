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
}

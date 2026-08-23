package com.sesamiwear.wear.complication

import com.sesamiwear.core.TileDisplayState

/**
 * [TileDisplayState] からComplication（文字盤表示、表示領域が非常に小さい）向けの短い文言を決定する。
 * Android非依存のためユニットテスト対象。
 */
object SesameComplicationContent {
    fun shortText(state: TileDisplayState): String =
        when (state) {
            TileDisplayState.LOCKED -> "施錠"
            TileDisplayState.UNLOCKED -> "解錠"
            TileDisplayState.MIXED -> "混在"
            TileDisplayState.IN_PROGRESS -> "通信中"
            TileDisplayState.DISCONNECTED -> "未接続"
            TileDisplayState.UNKNOWN -> "不明"
        }
}

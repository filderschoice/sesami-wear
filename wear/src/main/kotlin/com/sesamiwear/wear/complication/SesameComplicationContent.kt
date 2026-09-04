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

    /**
     * `LONG_TEXT`枠向けの文言（BL-072）。`SHORT_TEXT`枠しか受け付けない文字盤で本データソースが
     * 選べない・表示されない事象を避けるため、`LONG_TEXT`もサポート対象に加えた際に追加した。
     * 表示領域に余裕があるため、状態文言に加えて対象デバイス名も併記する。
     */
    fun longText(
        displayName: String,
        state: TileDisplayState,
    ): String = "$displayName ${shortText(state)}"
}

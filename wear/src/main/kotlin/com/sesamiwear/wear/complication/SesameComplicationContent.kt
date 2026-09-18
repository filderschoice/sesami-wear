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
     *
     * [detailLabel]（`core.display.SesameStatusDetail`が組み立てた「3分前」「認証エラー」等）を
     * 渡すと括弧付きで末尾へ添える（BL-142 / BL-140）。Complicationは自動での状態取得を
     * 行わなくなったため、表示がどれだけ古いか・直近の取得が失敗していないかを利用者が判断
     * できるようにする。`SHORT_TEXT`枠は表示できる文字数が非常に少ないため対象外とし、
     * [shortText]は状態文言のみのままにする。
     */
    fun longText(
        displayName: String,
        state: TileDisplayState,
        detailLabel: String? = null,
    ): String {
        val base = "$displayName ${shortText(state)}"
        return if (detailLabel == null) base else "$base（$detailLabel）"
    }
}

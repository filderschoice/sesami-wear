package com.sesamiwear.wear.ui

/**
 * [DeviceSelectionScreen] の固定文言（BL-114）。Android非依存のためユニットテスト対象。
 *
 * デモモード（BL-109）の説明文は、以前は32文字の1文をそのまま1つの[androidx.wear.compose.material.Text]
 * へ渡していたため、円形画面の左右の縁で行頭・行末の文字が見切れていた。文言側であらかじめ
 * [MAX_LINE_CHARS]以内の行へ分割し、折り返し位置を画面幅に委ねないようにする。
 */
object DeviceSelectionContent {
    /**
     * 1行に収めてよい文字数の上限。最小構成の円形Wear OS端末（幅192dp）で、
     * ScalingLazyColumnの既定の水平パディング（10dp）と本画面で追加する水平パディング
     * （[HINT_HORIZONTAL_PADDING_DP]）を引いた残り幅に、全角12dp（caption2 = 12sp）の文字が
     * 何文字並ぶかから決めている（192 - 2 * (10 + 12) = 148dp、148 / 12 = 12.3文字）。
     */
    const val MAX_LINE_CHARS = 11

    /** 説明文の左右パディング（dp）。[MAX_LINE_CHARS]の算出根拠と対応させる。 */
    const val HINT_HORIZONTAL_PADDING_DP = 12

    /** デモモード時の見出し。1行目で「実物の鍵ではない」ことを伝える。 */
    const val DEMO_HINT_TITLE = "デモモード"

    /** デモモード時の説明。行ごとに意味が切れる位置で分割する。 */
    val DEMO_HINT_LINES =
        listOf(
            "スマホで登録すると",
            "実際の鍵を操作できます",
        )

    /** [DEMO_HINT_LINES]を改行で連結した表示用の文字列。 */
    val demoHintBody: String
        get() = DEMO_HINT_LINES.joinToString(separator = "\n")
}

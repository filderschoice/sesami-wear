package com.sesamiwear.mobile.widget

/**
 * 2マス×1マス相当の表示（[SesameWidgetLayout.MEDIUM]）の横幅の配分（BL-233）。Android非依存。
 *
 * BL-174〜BL-232の間は左1マスの幅を72dpに固定していた。2マスの実幅を約182dp（横4列のグリッド）と
 * 見込んでいたが、横5列のグリッドでは2マスが約145dp前後になり、右側の状態表示が「施…」と省略された
 * （公開版での報告）。**状態表示の幅を優先し、左1マス（デバイス名と「◀ ▶」）を狭める。**
 * デバイス名は省略（…）を許す。「◀ ▶」は押せる大きさを下回らないよう[LEFT_COLUMN_MIN_DP]で止める。
 *
 * Glanceの`SizeMode.Responsive`では`LocalSize`が実寸ではなく**選ばれた候補の大きさ**になるため、
 * 2マス幅の候補を[CANDIDATE_WIDTHS_DP]のように刻んで提示し、候補の幅から配分を決める。
 * 候補は実寸以下の大きさが選ばれるため、ここで求めた状態表示の幅は実際にはそれ以上ある。
 */
object SesameWidgetMediumMetrics {
    /**
     * 2マス×1マス相当として提示する候補の幅。先頭は[SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP]（2マス分の最小幅）、
     * 末尾は左1マスが[LEFT_COLUMN_MAX_DP]に達する幅を超える値にする。
     */
    val CANDIDATE_WIDTHS_DP = listOf(SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP, 130, 150, 170)

    /** 左1マスの幅（dp）。状態表示へ[STATUS_PREFERRED_WIDTH_DP]を残し、残りを左1マスへ回す。 */
    fun leftColumnWidthDp(widthDp: Int): Int =
        (widthDp - HORIZONTAL_CHROME_DP - STATUS_PREFERRED_WIDTH_DP).coerceIn(LEFT_COLUMN_MIN_DP, LEFT_COLUMN_MAX_DP)

    /**
     * 状態文言の文字サイズ（sp）。状態表示の内側の幅に4文字（「一部解錠」「全施錠中」）が
     * [LABEL_SP]で入れば[LABEL_SP]、入らなければ[NARROW_LABEL_SP]にする。
     */
    fun statusLabelSp(widthDp: Int): Int {
        val innerWidth = widthDp - HORIZONTAL_CHROME_DP - leftColumnWidthDp(widthDp) - STATUS_INNER_PADDING_DP * 2
        return if (innerWidth >= LABEL_SP * LABEL_FIT_CHARS) LABEL_SP else NARROW_LABEL_SP
    }

    /** 左1マスの上限。「全デバイス」（11sp×全角5文字＝55dp）に内側パディング6dp×2を足した67dpが収まる値（BL-184）。 */
    const val LEFT_COLUMN_MAX_DP = 72

    /** 左1マスの下限。「◀」「▶」のチップ（11sp＋内側パディング6dp×2＝23dp）2つと間隔4dpが収まる値。 */
    const val LEFT_COLUMN_MIN_DP = 50

    /** 状態表示に残したい幅。状態文言4文字（13sp×4＝52dp）と内側パディング6dp×2。 */
    const val STATUS_PREFERRED_WIDTH_DP = 64

    // 外周パディング8dp×2と、左1マスと状態表示の間隔6dp（SesameWidget の CONTAINER_PADDING_DP / SPACING_DP）。
    private const val HORIZONTAL_CHROME_DP = 8 * 2 + 6

    // 状態表示の内側パディング（SesameWidgetChips の CHIP_INNER_PADDING_DP）。
    private const val STATUS_INNER_PADDING_DP = 6

    // 状態文言の文字サイズ（SesameWidgetChips の CAPTION_SP / FOOTNOTE_SP）と、入れたい文字数。
    const val LABEL_SP = 13
    const val NARROW_LABEL_SP = 11
    private const val LABEL_FIT_CHARS = 4
}

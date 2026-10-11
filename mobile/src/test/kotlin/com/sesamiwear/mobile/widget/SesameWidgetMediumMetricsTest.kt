package com.sesamiwear.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2マス×1マス表示の横幅の配分（BL-233）。横5列のグリッドで2マスが約145dpになり、
 * 左1マスを72dpに固定していた頃は右側の状態文言が「施…」と省略されていた。
 */
class SesameWidgetMediumMetricsTest {
    @Test
    fun `the left column keeps its former width when two cells are wide`() {
        // 横4列のグリッド（2マスで約182dp）では、BL-184で決めた72dp（「全デバイス」が収まる幅）のまま。
        assertEquals(SesameWidgetMediumMetrics.LEFT_COLUMN_MAX_DP, SesameWidgetMediumMetrics.leftColumnWidthDp(182))
        assertEquals(SesameWidgetMediumMetrics.LEFT_COLUMN_MAX_DP, SesameWidgetMediumMetrics.leftColumnWidthDp(170))
    }

    @Test
    fun `the left column narrows so that the status keeps its width`() {
        // 2マスで約145dp（横5列）。左1マスを詰め、状態表示へ4文字分の幅を残す。
        val width = 150
        val left = SesameWidgetMediumMetrics.leftColumnWidthDp(width)
        assertTrue(left < SesameWidgetMediumMetrics.LEFT_COLUMN_MAX_DP)
        assertTrue(statusWidth(width, left) >= SesameWidgetMediumMetrics.STATUS_PREFERRED_WIDTH_DP)
    }

    @Test
    fun `the left column never becomes narrower than the cycle buttons`() {
        assertEquals(
            SesameWidgetMediumMetrics.LEFT_COLUMN_MIN_DP,
            SesameWidgetMediumMetrics.leftColumnWidthDp(SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP),
        )
    }

    @Test
    fun `every candidate leaves room for a three letter status at the chosen size`() {
        // 「施錠中」「解錠中」（全角3文字）が、各候補の幅で選んだ文字サイズのまま状態表示に収まる。
        // 最小の候補（110dp）だけは、実寸が候補より広い前提（約117dp以上）で収まる。
        SesameWidgetMediumMetrics.CANDIDATE_WIDTHS_DP.drop(1).forEach { width ->
            val left = SesameWidgetMediumMetrics.leftColumnWidthDp(width)
            val innerWidth = statusWidth(width, left) - STATUS_INNER_PADDING_DP * 2
            assertTrue("width=$width", innerWidth >= SesameWidgetMediumMetrics.statusLabelSp(width) * 3)
        }
    }

    @Test
    fun `the status label shrinks only when four letters do not fit`() {
        assertEquals(SesameWidgetMediumMetrics.LABEL_SP, SesameWidgetMediumMetrics.statusLabelSp(170))
        assertEquals(SesameWidgetMediumMetrics.LABEL_SP, SesameWidgetMediumMetrics.statusLabelSp(150))
        assertEquals(SesameWidgetMediumMetrics.NARROW_LABEL_SP, SesameWidgetMediumMetrics.statusLabelSp(130))
        assertEquals(SesameWidgetMediumMetrics.NARROW_LABEL_SP, SesameWidgetMediumMetrics.statusLabelSp(110))
    }

    @Test
    fun `the candidates start at two cells and reach the widest split`() {
        val candidates = SesameWidgetMediumMetrics.CANDIDATE_WIDTHS_DP
        assertEquals(SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP, candidates.first())
        assertEquals(candidates.sorted(), candidates)
        assertTrue(candidates.last() < SesameWidgetLayout.FULL_MIN_WIDTH_DP)
        assertEquals(
            SesameWidgetMediumMetrics.LEFT_COLUMN_MAX_DP,
            SesameWidgetMediumMetrics.leftColumnWidthDp(candidates.last()),
        )
        candidates.forEach {
            assertEquals(
                SesameWidgetLayout.MEDIUM,
                SesameWidgetLayout.of(widthDp = it, heightDp = 40),
            )
        }
    }

    @Test
    fun `the label sizes match the widget text sizes`() {
        assertEquals(CAPTION_SP, SesameWidgetMediumMetrics.LABEL_SP)
        assertEquals(FOOTNOTE_SP, SesameWidgetMediumMetrics.NARROW_LABEL_SP)
    }

    private fun statusWidth(
        widthDp: Int,
        leftDp: Int,
    ): Int = widthDp - HORIZONTAL_CHROME_DP - leftDp

    private companion object {
        // 外周パディング8dp×2と、左1マスと状態表示の間隔6dp。
        const val HORIZONTAL_CHROME_DP = 8 * 2 + 6
        const val STATUS_INNER_PADDING_DP = 6
    }
}

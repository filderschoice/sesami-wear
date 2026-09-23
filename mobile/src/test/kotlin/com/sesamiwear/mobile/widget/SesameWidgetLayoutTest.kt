package com.sesamiwear.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SesameWidgetLayoutTest {
    @Test
    fun `tile sized widget uses the full layout`() {
        // 既定サイズ（4x2相当。高さは1マス分の約2倍で、しきい値の172dpを上回る）は従来どおりの表示（BL-121）。
        assertEquals(SesameWidgetLayout.FULL, SesameWidgetLayout.of(widthDp = 250, heightDp = 256))
    }

    @Test
    fun `two cells wide and one cell tall uses the medium layout`() {
        // 縮小の下限（2x1、BL-174）。デバイス名・「◀ ▶」と状態表示を横に並べられる。
        assertEquals(
            SesameWidgetLayout.MEDIUM,
            SesameWidgetLayout.of(widthDp = SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP, heightDp = 50),
        )
    }

    @Test
    fun `one cell widget uses the compact layout`() {
        // 1x1相当（おおむね57x102dp、端末によって前後する）。BL-174で下限を2x1へ上げたため、
        // 通常はここへ来ないが保険として残している。
        assertEquals(SesameWidgetLayout.COMPACT, SesameWidgetLayout.of(widthDp = 57, heightDp = 102))
    }

    @Test
    fun `a wide but short widget uses the medium layout`() {
        // 4x1相当。左右の分割は成立するが、4行を縦に並べる高さが無い（BL-174でMEDIUMになる）。
        assertEquals(SesameWidgetLayout.MEDIUM, SesameWidgetLayout.of(widthDp = 250, heightDp = 50))
    }

    @Test
    fun `a wide widget one cell tall uses the medium layout`() {
        // 4x1の実測（Pixel 8 Pro + Nova Launcherで約128dp）。4要素が収まらないため[FULL]にしない（BL-158）。
        assertEquals(SesameWidgetLayout.MEDIUM, SesameWidgetLayout.of(widthDp = 250, heightDp = 128))
    }

    @Test
    fun `a narrow but tall widget falls back to compact`() {
        // 1x2相当。高さはあるが、左列と状態表示を横に並べる幅が無い。
        assertEquals(SesameWidgetLayout.COMPACT, SesameWidgetLayout.of(widthDp = 57, heightDp = 210))
    }

    @Test
    fun `the thresholds are inclusive`() {
        assertEquals(
            SesameWidgetLayout.FULL,
            SesameWidgetLayout.of(
                widthDp = SesameWidgetLayout.FULL_MIN_WIDTH_DP,
                heightDp = SesameWidgetLayout.FULL_MIN_HEIGHT_DP,
            ),
        )
        assertEquals(
            SesameWidgetLayout.MEDIUM,
            SesameWidgetLayout.of(
                widthDp = SesameWidgetLayout.FULL_MIN_WIDTH_DP - 1,
                heightDp = SesameWidgetLayout.FULL_MIN_HEIGHT_DP,
            ),
        )
        assertEquals(
            SesameWidgetLayout.MEDIUM,
            SesameWidgetLayout.of(
                widthDp = SesameWidgetLayout.FULL_MIN_WIDTH_DP,
                heightDp = SesameWidgetLayout.FULL_MIN_HEIGHT_DP - 1,
            ),
        )
        assertEquals(
            SesameWidgetLayout.COMPACT,
            SesameWidgetLayout.of(widthDp = SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP - 1, heightDp = 50),
        )
    }
}

package com.sesamiwear.mobile.showcase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 撮影モードの目印をしばらく隠す判定（BL-249）。 */
class ShowcaseMarkHidingTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `未設定なら隠さない`() {
        assertFalse(ShowcaseMarkHiding.isHidden(0L, now))
    }

    @Test
    fun `隠してから10分の間は隠し、10分たてば戻る`() {
        val until = ShowcaseMarkHiding.hiddenUntil(now)

        assertTrue(ShowcaseMarkHiding.isHidden(until, now))
        assertTrue(ShowcaseMarkHiding.isHidden(until, now + ShowcaseMarkHiding.HIDE_MILLIS - 1))
        assertFalse(ShowcaseMarkHiding.isHidden(until, now + ShowcaseMarkHiding.HIDE_MILLIS))
    }

    @Test
    fun `時計を戻して終了時刻が10分より先になった場合は隠さない`() {
        val until = ShowcaseMarkHiding.hiddenUntil(now)

        assertFalse(ShowcaseMarkHiding.isHidden(until, now - 1))
    }
}

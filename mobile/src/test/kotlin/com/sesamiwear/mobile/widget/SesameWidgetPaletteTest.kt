package com.sesamiwear.mobile.widget

import com.sesamiwear.core.display.SesameTileContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SesameWidgetPaletteTest {
    private val schemes = mapOf("day" to SesameWidgetPalette.DAY, "night" to SesameWidgetPalette.NIGHT)

    @Test
    fun `night scheme keeps the colors shared with the watch tile`() {
        assertEquals(0xFF121212.toInt(), SesameWidgetPalette.NIGHT.background)
        assertEquals(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB, SesameWidgetPalette.NIGHT.chip)
        assertEquals(SesameTileContent.NAME_HEADER_COLOR_ARGB, SesameWidgetPalette.NIGHT.nameHeader)
    }

    @Test
    fun `text is readable on every neutral surface`() {
        // WCAG 2.1 の通常の文字の基準（4.5:1）。チップの文字は13sp・11spのため大きい文字の緩和は使わない。
        schemes.forEach { (name, scheme) ->
            listOf(scheme.background, scheme.chip, scheme.nameHeader).forEach { surface ->
                val ratio = contrastRatio(scheme.text, surface)
                assertTrue("$name: ${"%.2f".format(ratio)}", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `chip stands out from the background more than the display-only name header`() {
        // 表示専用の帯をボタンと区別する明暗の関係（BL-210）を、ライトでも保つ。
        schemes.forEach { (name, scheme) ->
            val chip = contrastRatio(scheme.chip, scheme.background)
            val header = contrastRatio(scheme.nameHeader, scheme.background)
            assertTrue(name, chip > header)
            assertTrue(name, header > 1.0)
        }
    }

    private fun contrastRatio(
        a: Int,
        b: Int,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}

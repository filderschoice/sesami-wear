package com.sesamiwear.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * `appwidget-provider`の寸法が[SesameWidgetLayout]の定数と食い違っていないことを確かめる（BL-223）。
 * XMLとKotlinの片方だけを直すと、ランチャーが許す縮小の下限と、表示の出し分けの前提がずれるため。
 * Gradleの単体テストは作業ディレクトリがモジュール直下になるため、相対パスでリソースを読む。
 */
class SesameWidgetProviderInfoTest {
    @Test
    fun `both providers allow shrinking to two cells by one cell`() {
        listOf(LARGE_INFO, SMALL_INFO).forEach { path ->
            val attributes = providerAttributes(path)
            assertEquals(path, "${SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP}dp", attributes["minResizeWidth"])
            assertEquals(path, "${SesameWidgetLayout.ONE_CELL_MIN_DP}dp", attributes["minResizeHeight"])
            assertEquals(path, "horizontal|vertical", attributes["resizeMode"])
        }
    }

    @Test
    fun `the small provider starts at two cells by one cell`() {
        val attributes = providerAttributes(SMALL_INFO)
        assertEquals("${SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP}dp", attributes["minWidth"])
        assertEquals("${SesameWidgetLayout.ONE_CELL_MIN_DP}dp", attributes["minHeight"])
        assertEquals("2", attributes["targetCellWidth"])
        assertEquals("1", attributes["targetCellHeight"])
    }

    private fun providerAttributes(path: String): Map<String, String> =
        ATTRIBUTE_PATTERN.findAll(File(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private companion object {
        const val LARGE_INFO = "src/main/res/xml/sesame_widget_info.xml"
        const val SMALL_INFO = "src/main/res/xml/sesame_widget_small_info.xml"
        val ATTRIBUTE_PATTERN = Regex("""android:(\w+)="([^"]*)"""")
    }
}

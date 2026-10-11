package com.sesamiwear.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `appwidget-provider`の寸法が[SesameWidgetLayout]の定数と食い違っていないこと（BL-223 / BL-232）と、
 * ウィジェット一覧のプレビュー画像が指定されていること（BL-226）を確かめる。
 * XMLとKotlinの片方だけを直すと、ランチャーが許す縮小の下限と、表示の出し分けの前提がずれるため。
 * Gradleの単体テストは作業ディレクトリがモジュール直下になるため、相対パスでリソースを読む。
 */
class SesameWidgetProviderInfoTest {
    @Test
    fun `both providers allow shrinking to one cell by one cell`() {
        // BL-232。BL-174〜BL-231の間は幅の下限を2マス分（110dp）にしていた。
        listOf(LARGE_INFO, SMALL_INFO).forEach { path ->
            val attributes = providerAttributes(path)
            assertEquals(path, "${SesameWidgetLayout.ONE_CELL_MIN_DP}dp", attributes["minResizeWidth"])
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

    @Test
    fun `both providers show their own preview image in the widget picker`() {
        // ウィジェット一覧のプレビュー（BL-226）。画像は scripts/make-widget-previews.py で作る。
        mapOf(LARGE_INFO to "widget_preview", SMALL_INFO to "widget_preview_small").forEach { (path, image) ->
            assertEquals(path, "@drawable/$image", providerAttributes(path)["previewImage"])
            assertTrue(image, File("$PREVIEW_DIR/$image.png").isFile)
        }
    }

    private fun providerAttributes(path: String): Map<String, String> =
        ATTRIBUTE_PATTERN.findAll(File(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private companion object {
        const val LARGE_INFO = "src/main/res/xml/sesame_widget_info.xml"
        const val SMALL_INFO = "src/main/res/xml/sesame_widget_small_info.xml"
        const val PREVIEW_DIR = "src/main/res/drawable-nodpi"
        val ATTRIBUTE_PATTERN = Regex("""android:(\w+)="([^"]*)"""")
    }
}

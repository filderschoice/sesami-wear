package com.sesamiwear.core.display

import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.SesameStatusRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 経路と経路の方針の表示文言の検証（BL-167 / BL-168）。
 *
 * 表示できる面積が大きく違う面（Tile・ウィジェット / スマートフォンの画面）で使い分けるため、
 * アイコンが1文字に収まること・行を増やさない形で前置できることを固定する。
 */
class SesameRouteLabelTest {
    @Test
    fun `each route has its own icon`() {
        assertEquals(SesameRouteLabel.ICON_BLE, SesameRouteLabel.icon(SesameStatusRoute.BLE))
        assertEquals(SesameRouteLabel.ICON_WEB_API, SesameRouteLabel.icon(SesameStatusRoute.WEB_API))
    }

    @Test
    fun `an unknown route has no icon so nothing is prefixed`() {
        assertNull(SesameRouteLabel.icon(null))
        assertEquals("3分前", SesameRouteLabel.withIcon(null, "3分前"))
    }

    @Test
    fun `the icon is prefixed without a separator to save width`() {
        assertEquals("🔗3分前", SesameRouteLabel.withIcon(SesameStatusRoute.BLE, "3分前"))
        assertEquals("🌐認証エラー", SesameRouteLabel.withIcon(SesameStatusRoute.WEB_API, "認証エラー"))
    }

    @Test
    fun `icons stay a single code point so they occupy one character cell`() {
        // Tileとウィジェットは表示余白がほとんど無い（BL-102 / BL-104 / BL-158）。
        // 絵文字が異体字セレクタ等で長くなると、そのぶん文言が押し出される。
        listOf(SesameRouteLabel.ICON_BLE, SesameRouteLabel.ICON_WEB_API).forEach {
            assertEquals(it, 1, it.codePointCount(0, it.length))
        }
    }

    @Test
    fun `the phone screen uses words instead of icons`() {
        assertEquals("Bluetooth", SesameRouteLabel.name(SesameStatusRoute.BLE))
        assertEquals("インターネット", SesameRouteLabel.name(SesameStatusRoute.WEB_API))
        assertEquals(SesameRouteLabel.UNKNOWN_ROUTE_NAME, SesameRouteLabel.name(null))
    }

    @Test
    fun `every policy has a label and a description`() {
        SesameRoutePolicy.entries.forEach { policy ->
            assertTrue(policy.name, SesameRouteLabel.policyLabel(policy).isNotBlank())
            assertTrue(policy.name, SesameRouteLabel.policyDescription(policy).isNotBlank())
        }
    }

    @Test
    fun `the automatic policy description mentions the fallback and the request count`() {
        val description = SesameRouteLabel.policyDescription(SesameRoutePolicy.AUTO)
        assertTrue(description.contains("切り替え"))
        assertTrue(description.contains("リクエスト回数"))
    }

    @Test
    fun `the web api only policy description warns that requests are consumed`() {
        val description = SesameRouteLabel.policyDescription(SesameRoutePolicy.WEB_API_ONLY)
        assertTrue(description.contains("リクエスト回数を消費"))
    }
}

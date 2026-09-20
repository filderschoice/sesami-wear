package com.sesamiwear.mobile.ui

import androidx.annotation.DrawableRes
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.mobile.R

/**
 * スマートフォン側（アプリ画面のデバイスカードとホーム画面ウィジェット）で使う経路のアイコン（BL-176）。
 *
 * ウォッチ側（Tile・Complication）は絵文字（`core.display.SesameRouteLabel.icon`、🔗 / 🌐）のままで、
 * ここだけMaterial Symbolsのベクタードローアブルを使う。Complicationの`SHORT_TEXT`/`LONG_TEXT`は
 * テキストしか持てず画像を埋め込めないため、**全面を画像で統一することは仕様上できない**
 * （2026-09-20、ユーザー確認済み）。意味（Bluetooth直結／インターネット経由）は両者で揃えている。
 *
 * 経路が分からない（一度も操作・取得していない）場合はnullを返し、呼び出し側は何も描かない。
 */
object SesameRouteIcon {
    @DrawableRes
    fun drawableResOrNull(route: SesameStatusRoute?): Int? =
        when (route) {
            SesameStatusRoute.BLE -> R.drawable.ic_route_bluetooth
            SesameStatusRoute.WEB_API -> R.drawable.ic_route_internet
            null -> null
        }
}

package com.sesamiwear.mobile.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.mobile.ui.SesameRouteIcon

/**
 * ホーム画面ウィジェットの部品と寸法のうち、[SesameWidget]（Tile相当の表示）と
 * [MediumLeftColumn]（2マス×1マスの表示、BL-174）で共用するもの。
 *
 * もとは[SesameWidget]に同居していたが、サイズ別レイアウトが3段階になって1ファイルの関数数が
 * detektの上限（`TooManyFunctions`）に達したため分離した。表示内容の決定は
 * [SesameWidgetModelResolver]（Android非依存）が行い、ここはGlanceで並べるだけ。
 *
 * @param fillWidth 横幅いっぱいに広げるか。**`Row`の中で`defaultWeight()`と併用するときはfalse。**
 * Glanceでは幅の指定が競合し、先頭のチップが全幅を占めて後続が押し出される（BL-183で
 * 「▶」が描画されなくなっていた）。
 */
@Composable
internal fun NeutralChip(
    text: String,
    modifier: GlanceModifier,
    sizeSp: Int = CAPTION_SP,
    maxLines: Int = 2,
    fillWidth: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .let { if (fillWidth) it.fillMaxWidth() else it }
                .background(ColorProvider(Color(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB)))
                .cornerRadius(CHIP_CORNER_RADIUS_DP.dp)
                .padding(CHIP_INNER_PADDING_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = widgetTextStyle(NEUTRAL_TEXT_ARGB, sizeSp), maxLines = maxLines)
    }
}

/**
 * 状態アイコンと状態文言だけの表示。1マス（[SesameWidgetLayout.COMPACT]）と
 * 2マス×1マス（[SesameWidgetLayout.MEDIUM]）の右1マスで共用する。
 * 最終取得時刻・操作文言は高さが足りないため出さない。
 */
@Composable
internal fun StatusIconAndLabel(model: SesameWidgetModel.Configured) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = model.statusIcon, style = widgetTextStyle(model.textColorArgb, COMPACT_ICON_SP))
        Text(
            text = model.statusLabel,
            style = widgetTextStyle(model.textColorArgb, CAPTION_SP, bold = true),
            maxLines = 1,
        )
    }
}

/**
 * 4x2表示の右上に置くデバイス名の帯（BL-205）。中立色の背景に、経路のベクターアイコン（BL-176）と
 * デバイス名を横に並べる（BL-209でアイコンを名前の前へ移した）。タップは「更新」と同じ状態取得。
 *
 * 経路アイコンは、BL-176では状態表示の最終取得時刻の行へ置いていたが、状態色（施錠中＝緑・
 * 解錠中＝赤）の上では背景に埋もれて見分けにくかった（2026-09-23のユーザー指摘）。
 * 状態によって色の変わらない中立色の帯へ移し、常に同じコントラストで見えるようにしている。
 * 経路が分からない場合はアイコンを出さず、デバイス名だけを出す。
 */
@Composable
internal fun NameHeader(
    displayName: String,
    route: SesameStatusRoute?,
    onClick: Action?,
) {
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .clickableOrSelf(onClick)
                .background(ColorProvider(Color(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB)))
                .cornerRadius(CHIP_CORNER_RADIUS_DP.dp)
                .padding(horizontal = CHIP_INNER_PADDING_DP.dp, vertical = NAME_HEADER_VERTICAL_PADDING_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 経路アイコンは名前の前に置く（BL-209、Tileと揃える）。Rowは子を先頭から順に測り、後ろの子には
        // 残りの幅しか渡さないため、名前を先に置くと長い名前でアイコンが右端から押し出される。
        SesameRouteIcon.drawableResOrNull(route)?.let { iconRes ->
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = SesameRouteLabel.name(route),
                colorFilter = ColorFilter.tint(ColorProvider(Color(NEUTRAL_TEXT_ARGB))),
                modifier = GlanceModifier.size(ROUTE_ICON_SIZE_DP.dp),
            )
            Spacer(modifier = GlanceModifier.width(ROUTE_ICON_GAP_DP.dp))
        }
        Text(text = displayName, style = widgetTextStyle(NEUTRAL_TEXT_ARGB, CAPTION_SP), maxLines = 1)
    }
}

internal fun GlanceModifier.clickableOrSelf(action: Action?): GlanceModifier = action?.let { clickable(it) } ?: this

internal fun widgetTextStyle(
    argb: Int,
    sizeSp: Int,
    bold: Boolean = false,
) = TextStyle(
    color = ColorProvider(Color(argb)),
    fontSize = sizeSp.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textAlign = TextAlign.Center,
)

/** チップの文字色（暗い背景に白）。 */
internal const val NEUTRAL_TEXT_ARGB = 0xFFFFFFFF.toInt()

// 寸法はwearのTile（チップ角丸12dp・内側パディング6dp）に合わせている。
internal const val CHIP_CORNER_RADIUS_DP = 12
internal const val CHIP_INNER_PADDING_DP = 6

internal const val ICON_SP = 28
internal const val BODY_SP = 16
internal const val CAPTION_SP = 13
internal const val FOOTNOTE_SP = 11
internal const val COMPACT_ICON_SP = 24

/** 経路アイコンの大きさと、デバイス名との間隔（BL-176 / BL-205）。13spの文言と並べて浮かない値にしている。 */
private const val ROUTE_ICON_SIZE_DP = 14
private const val ROUTE_ICON_GAP_DP = 4

/**
 * デバイス名の帯の上下の内側余白（BL-205）。状態表示の高さを削らないよう、他のチップ（6dp）より詰めている。
 * 帯の高さは 13sp≒18dp＋4dp×2＝約26dp（[SesameWidgetLayout.FULL_MIN_HEIGHT_DP]の内訳を参照）。
 */
private const val NAME_HEADER_VERTICAL_PADDING_DP = 4

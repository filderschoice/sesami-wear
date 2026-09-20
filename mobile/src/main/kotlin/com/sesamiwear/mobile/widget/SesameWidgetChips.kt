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
 * 最終取得時刻（と電池残量）の行（BL-142 / BL-171）。先頭へ経路のベクターアイコンを置く（BL-176）。
 *
 * **行は増やさない。** ウィジェットの高さ予算は既に埋まっており（[SesameWidgetLayout]のKDoc）、
 * 行を足すと最小サイズで操作文言が見切れる（BL-158と同じ事故）。そのため横並びにしている。
 * 経路が分からない場合はアイコンを出さず、文言だけを従来どおり中央へ置く。
 */
@Composable
internal fun DetailRow(
    route: SesameStatusRoute?,
    text: String,
    textColorArgb: Int,
) {
    val iconRes = SesameRouteIcon.drawableResOrNull(route)
    if (iconRes == null) {
        Text(text = text, style = widgetTextStyle(textColorArgb, FOOTNOTE_SP), maxLines = 2)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color(textColorArgb))),
                modifier = GlanceModifier.size(ROUTE_ICON_SIZE_DP.dp),
            )
            Spacer(modifier = GlanceModifier.width(ROUTE_ICON_GAP_DP.dp))
            Text(text = text, style = widgetTextStyle(textColorArgb, FOOTNOTE_SP), maxLines = 2)
        }
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

/** 経路アイコンの大きさと、文言との間隔（BL-176）。11spの文言と並べて浮かない値にしている。 */
private const val ROUTE_ICON_SIZE_DP = 12
private const val ROUTE_ICON_GAP_DP = 2

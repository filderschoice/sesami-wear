package com.sesamiwear.mobile.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sesamiwear.core.display.SesameTileContent

/**
 * ホーム画面ウィジェットの部品と寸法のうち、[SesameWidget]（Tile相当の表示）と
 * [MediumLeftColumn]（2マス×1マスの表示、BL-174）で共用するもの。
 *
 * もとは[SesameWidget]に同居していたが、サイズ別レイアウトが3段階になって1ファイルの関数数が
 * detektの上限（`TooManyFunctions`）に達したため分離した。表示内容の決定は
 * [SesameWidgetModelResolver]（Android非依存）が行い、ここはGlanceで並べるだけ。
 */
@Composable
internal fun NeutralChip(
    text: String,
    modifier: GlanceModifier,
    sizeSp: Int = CAPTION_SP,
    maxLines: Int = 2,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
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

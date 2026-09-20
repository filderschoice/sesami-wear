package com.sesamiwear.mobile.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider

/**
 * 2マス×1マス相当の表示（[SesameWidgetLayout.MEDIUM]、BL-174）の左1マス。
 *
 * 上がデバイス名（タップで状態取得）、下が「◀ ▶」（対象デバイスの順送り、BL-175）。
 * 高さ1マスには選択画面を開くチップを置く余地が無いため、ここでは「変更」の代わりに順送りを置く
 * （[SesameWidgetLayout.FULL]は従来どおり「変更」）。選択画面は、ウィジェットの長押しメニュー
 * （`widgetFeatures="reconfigurable"`）から開ける。
 */
@Composable
internal fun MediumLeftColumn(
    displayName: String,
    actions: WidgetActions,
) {
    Column(modifier = GlanceModifier.width(MEDIUM_LEFT_COLUMN_WIDTH_DP.dp).fillMaxHeight()) {
        NeutralChip(
            text = displayName,
            modifier = GlanceModifier.defaultWeight().clickableOrSelf(actions.refresh),
            sizeSp = FOOTNOTE_SP,
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(MEDIUM_SPACING_DP.dp))
        Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            // fillWidth = false。defaultWeight() と fillMaxWidth() を併用すると幅の指定が競合し、
            // 先頭のチップが全幅を占めて「▶」が押し出される（BL-183）。
            NeutralChip(
                text = CYCLE_BACKWARD_LABEL,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickableOrSelf(actions.cycleBackward),
                sizeSp = FOOTNOTE_SP,
                maxLines = 1,
                fillWidth = false,
            )
            Spacer(modifier = GlanceModifier.width(MEDIUM_SPACING_DP.dp))
            NeutralChip(
                text = CYCLE_FORWARD_LABEL,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickableOrSelf(actions.cycleForward),
                sizeSp = FOOTNOTE_SP,
                maxLines = 1,
                fillWidth = false,
            )
        }
    }
}

/**
 * 2マス×1マス相当の右1マス（BL-174）。1マス表示と同じく状態アイコンと状態文言だけを出す
 * （[StatusIconAndLabel]）。タップの挙動はTile相当の表示と同じ（[WidgetTapAction]の判定どおり）。
 */
@Composable
internal fun RowScope.MediumStatusBox(
    model: SesameWidgetModel.Configured,
    onClick: Action?,
) {
    Box(
        modifier =
            GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickableOrSelf(onClick)
                .background(ColorProvider(Color(model.backgroundColorArgb)))
                .cornerRadius(CHIP_CORNER_RADIUS_DP.dp)
                .padding(CHIP_INNER_PADDING_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        StatusIconAndLabel(model)
    }
}

// 2マス×1マス表示での対象デバイスの順送り（BL-175）。文字1つで向きが分かるものを選んでいる。
private const val CYCLE_BACKWARD_LABEL = "◀"
private const val CYCLE_FORWARD_LABEL = "▶"

// 左1マスの幅（BL-174 / BL-184）。本アプリが出す最長の文言「全デバイス」（11sp×全角5文字＝55dp）に
// チップの内側パディング（6dp×2）を足した67dpが収まる値にする。60dpでは「全デバ…」と省略されていた。
// 右1マスは残り全域（defaultWeight）で、実サイズの2マス幅（Pixel 6相当で約182dp）なら
// 「施錠中」（13sp×3文字＋パディング＝約51dp）に足りる。
private const val MEDIUM_LEFT_COLUMN_WIDTH_DP = 72
private const val MEDIUM_SPACING_DP = 4

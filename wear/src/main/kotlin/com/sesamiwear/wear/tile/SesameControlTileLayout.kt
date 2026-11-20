package com.sesamiwear.wear.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.display.SesameControlTileContent
import com.sesamiwear.core.display.SesameTileContent

// 操作専用タイル（SesameControlTileService、BL-298）のレイアウト部品。detektのTooManyFunctions回避のため
// サービス本体から分けている。
// 寸法は円形192dpの画面を前提にSesameControlTileContent（core）が決める（段の高さ48dp・段間4dp・段ごとの可用幅）。
// ここでは渡された値をそのままレイアウトへ反映する。
internal const val TEXT_COLOR_ARGB = 0xFFFFFFFF.toInt()

// 横並びのチップは幅が約50dpのため、内側の余白を詰めて文字の幅を確保する。
private const val NARROW_CHIP_PADDING_DP = 2f

// 全体ボタンの段の幅がこの値以上なら大きめの文字（TITLE3）、未満なら一段小さい文字にする。
private const val WIDE_BUTTON_ROW_DP = 140

internal fun buildControlTileContent(
    context: Context,
    content: SesameControlTileContent.Content,
): LayoutElementBuilders.LayoutElement {
    val column =
        LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.wrap())
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(buildButtonRow(context, content))
    content.chipRows.forEachIndexed { index, row ->
        column.addContent(gap()).addContent(buildChipRow(context, row, isLastRow = index == content.chipRows.lastIndex))
    }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(column.build())
        .build()
}

private fun gap(): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Spacer.Builder()
        .setHeight(DimensionBuilders.dp(SesameControlTileContent.GAP_DP.toFloat()))
        .build()

private fun hGap(): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Spacer.Builder()
        .setWidth(DimensionBuilders.dp(SesameControlTileContent.GAP_DP.toFloat()))
        .build()

private fun tierRow(widthDp: Int): LayoutElementBuilders.Row.Builder =
    LayoutElementBuilders.Row.Builder()
        .setWidth(DimensionBuilders.dp(widthDp.toFloat()))
        .setHeight(DimensionBuilders.dp(SesameControlTileContent.TIER_HEIGHT_DP.toFloat()))

/** 上段の「全施錠」「全解錠」。スマホ未接続のときは中立色にしてタップ不可にする。 */
private fun buildButtonRow(
    context: Context,
    content: SesameControlTileContent.Content,
): LayoutElementBuilders.LayoutElement {
    val wide = content.buttonRowWidthDp >= WIDE_BUTTON_ROW_DP
    val typography = if (wide) Typography.TYPOGRAPHY_TITLE3 else Typography.TYPOGRAPHY_CAPTION1
    val pkg = context.packageName
    val enabled = content.buttonsEnabled
    val lock =
        BigButton(
            content.lockLabel,
            content.lockDescription,
            TileDisplayState.LOCKED,
            if (enabled) buildActionClickable(pkg, SesameCommand.LOCK, content.targetUuid, "lock-all") else null,
        )
    // 解錠はタップだけでは実行しない。選ぶ画面（SesameActionActivityの判定）で明示的に押して初めて実行する。
    val unlock =
        BigButton(
            content.unlockLabel,
            content.unlockDescription,
            TileDisplayState.UNLOCKED,
            if (enabled) buildActionClickable(pkg, SesameCommand.UNLOCK, content.targetUuid, "unlock-all") else null,
        )
    return tierRow(content.buttonRowWidthDp)
        .addContent(buildBigButton(context, lock, typography))
        .addContent(hGap())
        .addContent(buildBigButton(context, unlock, typography))
        .build()
}

private class BigButton(
    val label: String,
    val description: String,
    val resultingState: TileDisplayState,
    val clickable: ModifiersBuilders.Clickable?,
)

private fun buildBigButton(
    context: Context,
    button: BigButton,
    typography: Int,
): LayoutElementBuilders.LayoutElement {
    val enabled = button.clickable != null
    val background =
        if (enabled) {
            SesameTileContent.backgroundColorArgb(button.resultingState)
        } else {
            SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB
        }
    val textColor = if (enabled) SesameTileContent.statusTextColorArgb(button.resultingState) else TEXT_COLOR_ARGB
    val modifiers =
        chipModifiers(background, NARROW_CHIP_PADDING_DP)
            .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(button.description).build())
    button.clickable?.let { modifiers.setClickable(it) }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers.build())
        .addContent(
            Text.Builder(context, button.label)
                .setTypography(typography)
                .setColor(ColorBuilders.argb(textColor))
                .setMaxLines(1)
                .build(),
        )
        .build()
}

/** チップの1段。台のチップを横に並べ、最後の段で登録が上限を超えるときは末尾へ「他N台」のチップを置く。 */
private fun buildChipRow(
    context: Context,
    row: SesameControlTileContent.ChipRow,
    isLastRow: Boolean,
): LayoutElementBuilders.LayoutElement {
    val builder = tierRow(row.widthDp)
    var first = true
    row.chips.forEach { chip ->
        if (!first) builder.addContent(hGap())
        first = false
        builder.addContent(buildDeviceChip(context, chip))
    }
    row.overflowText?.takeIf { isLastRow }?.let {
        if (!first) builder.addContent(hGap())
        builder.addContent(buildOverflowChip(context, it))
    }
    return builder.build()
}

/** 台ごとのチップ。背景は状態色、上が名前（短縮）、下が状態アイコン。タップでその台の施錠・解錠を選ぶ画面を開く。 */
private fun buildDeviceChip(
    context: Context,
    chip: SesameControlTileContent.DeviceChip,
): LayoutElementBuilders.LayoutElement {
    val textColor = ColorBuilders.argb(SesameTileContent.statusTextColorArgb(chip.state))
    val modifiers =
        chipModifiers(SesameTileContent.backgroundColorArgb(chip.state), NARROW_CHIP_PADDING_DP)
            .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(chip.description).build())
    if (chip.tappable) {
        modifiers.setClickable(buildActionClickable(context.packageName, null, chip.uuid, "choose-${chip.uuid}"))
    }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers.build())
        .addContent(
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(chipText(context, chip.name, Typography.TYPOGRAPHY_CAPTION3, textColor))
                .addContent(chipText(context, chip.icon, Typography.TYPOGRAPHY_CAPTION1, textColor))
                .build(),
        )
        .build()
}

/** 登録が上限を超えたときの「他N台」のチップ。タップでアプリ本体の状態一覧を開く。 */
private fun buildOverflowChip(
    context: Context,
    text: String,
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(
            chipModifiers(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB, NARROW_CHIP_PADDING_DP)
                .setClickable(buildOpenAppClickable(context.packageName, "open-list"))
                .setSemantics(
                    ModifiersBuilders.Semantics.Builder().setContentDescription("$text タップしてアプリを開く").build(),
                )
                .build(),
        )
        .addContent(chipText(context, text, Typography.TYPOGRAPHY_CAPTION2, ColorBuilders.argb(TEXT_COLOR_ARGB)))
        .build()

private fun chipText(
    context: Context,
    text: String,
    typography: Int,
    color: ColorBuilders.ColorProp,
): LayoutElementBuilders.LayoutElement =
    Text.Builder(context, text)
        .setTypography(typography)
        .setColor(color)
        .setMaxLines(1)
        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
        .build()

package com.sesamiwear.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.ModifiersBuilders
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.wear.action.SesameActionActivity
import com.sesamiwear.wear.action.SesameActionCommandParser

// 既存のタイル（SesameTileService）と操作専用タイル（SesameControlTileService、BL-298）で共用する
// レイアウト部品。もとはSesameTileServiceの非公開メソッドだったものを、挙動を変えずに切り出した。

/** チップの角丸の半径（dp）。 */
internal const val CHIP_CORNER_RADIUS_DP = 12f

/** チップの内側の余白（dp）。 */
internal const val CHIP_INNER_PADDING_DP = 6f

/**
 * チップ（角丸の四角ボタン風のBox）に共通の背景色・角丸・内側パディングを設定した
 * Modifiersビルダーを返す（BL-063）。呼び出し側でsetClickable/setSemantics等を追加できるよう
 * ビルダーのまま返す。
 */
internal fun chipModifiers(backgroundColorArgb: Int): ModifiersBuilders.Modifiers.Builder =
    ModifiersBuilders.Modifiers.Builder()
        .setBackground(
            ModifiersBuilders.Background.Builder()
                .setColor(ColorBuilders.argb(backgroundColorArgb))
                .setCorner(
                    ModifiersBuilders.Corner.Builder()
                        .setRadius(DimensionBuilders.dp(CHIP_CORNER_RADIUS_DP))
                        .build(),
                )
                .build(),
        )
        .setPadding(
            ModifiersBuilders.Padding.Builder()
                .setAll(DimensionBuilders.dp(CHIP_INNER_PADDING_DP))
                .build(),
        )

/**
 * SesameActionActivityを開くClickable。[command]が施錠なら即実行、解錠なら確認を兼ねる選ぶ画面
 * （[SesameActionActivity]側の判定）、nullなら施錠・解錠を選ぶ画面を開く（BL-295）。
 * [clickableId]は同じレイアウト内で複数並べるときに区別するためのID（省略時はコマンド名、選ぶ画面は`choose`）。
 */
internal fun buildActionClickable(
    packageName: String,
    command: SesameCommand?,
    deviceUuid: String,
    clickableId: String? = null,
): ModifiersBuilders.Clickable {
    val activity =
        ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(SesameActionActivity::class.java.name)
            .addKeyToExtraMapping(
                SesameActionCommandParser.EXTRA_DEVICE_UUID,
                ActionBuilders.AndroidStringExtra.Builder().setValue(deviceUuid).build(),
            )
    if (command == null) {
        activity.addKeyToExtraMapping(
            SesameActionCommandParser.EXTRA_CHOOSE,
            ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
        )
    } else {
        activity.addKeyToExtraMapping(
            SesameActionCommandParser.EXTRA_COMMAND,
            ActionBuilders.AndroidStringExtra.Builder().setValue(command.name).build(),
        )
    }
    return ModifiersBuilders.Clickable.Builder()
        .setId(clickableId ?: command?.name ?: "choose")
        .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
        .build()
}

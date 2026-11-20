package com.sesamiwear.wear.tile

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.display.SesameControlTileContent
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.wear.MainActivity
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.messaging.SesameDeviceListReader
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 操作専用のWear OS Tile（BL-298）。[SesameTileService]（1台ごとの状態表示と操作）とは別のタイルとして、
 * タイル追加の一覧へ並ぶ。登録済みの全デバイスが対象で、デバイスの割り当て（[TileDeviceAssignmentStore]）は
 * 持たない（tileIdの保存もしない）。
 *
 * 上段に大きな「全施錠」「全解錠」（1台だけのときは「施錠」「解錠」）、下段に台ごとの状態の行を並べる。
 * 「全施錠」は1タップで全台へ送る。「全解錠」と台の行のタップは、施錠・解錠を選ぶ画面
 * （[com.sesamiwear.wear.action.SesameActionActivity]）を開き、そこで押した方を実行する
 * （解錠がタップだけでは実行されない）。表示の規則は[SesameControlTileContent]が持つ。
 *
 * 状態は最後に同期された値を読むだけで、状態取得のリクエストは送らない（BL-142）。操作が完了すると
 * mobile側が結果とDataItemを返し、そのたびに[com.sesamiwear.wear.display.SesameDisplayUpdateRequester]等が
 * このタイルの再描画を依頼する（施錠して成功なら施錠状態の表示になる）。
 * Tiles APIへの依存のためビルド確認までとし、実機での見え方はBL-297の人手検証で確認する。
 */
class SesameControlTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        CoroutineScope(Dispatchers.IO).launch {
            val context = applicationContext
            val devices = SesameDeviceListReader.readLatest(context)
            val snapshots = devices.associate { it.uuid to SesameStatusSnapshotReader.readLatest(context, it.uuid) }
            val connected = SesameConnectedNodeProvider.firstConnectedNodeId(context) != null
            val content = SesameControlTileContent.build(devices, { snapshots[it]?.isLocked }, connected)
            Log.d(TAG, "onTileRequest tileId=${requestParams.tileId} devices=${devices.size} connected=$connected")
            future.set(buildTile(if (content == null) buildEmptyBox() else buildContent(context, content)))
        }
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun buildTile(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                            .build(),
                    )
                    .build(),
            )
            .build()

    /** 0台のとき。タップでアプリ本体（状態一覧と案内）を開く。 */
    private fun buildEmptyBox(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(buildOpenAppClickable(packageName, "open-app"))
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription("セサミが未登録 タップしてアプリを開く")
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Text.Builder(this, SesameControlTileContent.EMPTY_MESSAGE)
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setColor(ColorBuilders.argb(TEXT_COLOR_ARGB))
                    .setMaxLines(2)
                    .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                    .build(),
            )
            .build()

    private companion object {
        const val TAG = "SesameControlTile"

        // 表示の構造を変えても画像リソースは使わないが、ホストの古いキャッシュを避けるため版を持つ。
        const val RESOURCES_VERSION = "1"
    }
}

// 円形192dpで縦に「全体ボタン1段＋行」を3段、各段48dp以上で収める寸法（SesameControlTileContent.MAX_ROWS）。
// 縦の余白8dp×2＋48dp×3＋段間4dp×2＝168dp。左右は円の弦に収まるよう広めに取る。
private const val ROW_HEIGHT_DP = 48f
private const val GAP_DP = 4f
private const val PADDING_VERTICAL_DP = 8f
private const val PADDING_HORIZONTAL_DP = 28f
private const val TEXT_COLOR_ARGB = 0xFFFFFFFF.toInt()

private fun buildContent(
    context: Context,
    content: SesameControlTileContent.Content,
): LayoutElementBuilders.LayoutElement {
    val column =
        LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.wrap())
            .addContent(buildButtonRow(context, content))
    content.rows.forEach { row ->
        column.addContent(gap()).addContent(buildDeviceRow(context, row))
    }
    content.overflowText?.let { column.addContent(gap()).addContent(buildOverflowRow(context, it)) }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setPadding(
                    ModifiersBuilders.Padding.Builder()
                        .setTop(DimensionBuilders.dp(PADDING_VERTICAL_DP))
                        .setBottom(DimensionBuilders.dp(PADDING_VERTICAL_DP))
                        .setStart(DimensionBuilders.dp(PADDING_HORIZONTAL_DP))
                        .setEnd(DimensionBuilders.dp(PADDING_HORIZONTAL_DP))
                        .build(),
                )
                .build(),
        )
        .addContent(column.build())
        .build()
}

private fun gap(): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(GAP_DP)).build()

/** 上段の「全施錠」「全解錠」。スマホ未接続のときは中立色にしてタップ不可にする。 */
private fun buildButtonRow(
    context: Context,
    content: SesameControlTileContent.Content,
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Row.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.dp(ROW_HEIGHT_DP))
        .addContent(
            buildBigButton(
                context,
                label = content.lockLabel,
                description = content.lockDescription,
                resultingState = TileDisplayState.LOCKED,
                clickable =
                    if (content.buttonsEnabled) {
                        buildActionClickable(context.packageName, SesameCommand.LOCK, content.targetUuid, "lock-all")
                    } else {
                        null
                    },
            ),
        )
        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(GAP_DP)).build())
        .addContent(
            buildBigButton(
                context,
                label = content.unlockLabel,
                description = content.unlockDescription,
                resultingState = TileDisplayState.UNLOCKED,
                // 解錠はタップだけでは実行しない。選ぶ画面（SesameActionActivityの判定）で明示的に押して初めて実行する。
                clickable =
                    if (content.buttonsEnabled) {
                        buildActionClickable(
                            context.packageName,
                            SesameCommand.UNLOCK,
                            content.targetUuid,
                            "unlock-all",
                        )
                    } else {
                        null
                    },
            ),
        )
        .build()

private fun buildBigButton(
    context: Context,
    label: String,
    description: String,
    resultingState: TileDisplayState,
    clickable: ModifiersBuilders.Clickable?,
): LayoutElementBuilders.LayoutElement {
    val enabled = clickable != null
    val background =
        if (enabled) {
            SesameTileContent.backgroundColorArgb(resultingState)
        } else {
            SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB
        }
    val textColor = if (enabled) SesameTileContent.statusTextColorArgb(resultingState) else TEXT_COLOR_ARGB
    val modifiers =
        chipModifiers(background)
            .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build())
    clickable?.let { modifiers.setClickable(it) }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers.build())
        .addContent(
            Text.Builder(context, label)
                .setTypography(Typography.TYPOGRAPHY_TITLE3)
                .setColor(ColorBuilders.argb(textColor))
                .setMaxLines(1)
                .build(),
        )
        .build()
}

/** 台ごとの行。背景は状態色、1行目が名前、2行目が状態。タップでその台の施錠・解錠を選ぶ画面を開く。 */
private fun buildDeviceRow(
    context: Context,
    row: SesameControlTileContent.DeviceRow,
): LayoutElementBuilders.LayoutElement {
    val textColor = ColorBuilders.argb(SesameTileContent.statusTextColorArgb(row.state))
    val modifiers =
        chipModifiers(SesameTileContent.backgroundColorArgb(row.state))
            .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(row.description).build())
    if (row.tappable) {
        modifiers.setClickable(buildActionClickable(context.packageName, null, row.uuid, "choose-${row.uuid}"))
    }
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.dp(ROW_HEIGHT_DP))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers.build())
        .addContent(
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(rowText(context, row.name, Typography.TYPOGRAPHY_CAPTION1, textColor))
                .addContent(rowText(context, row.statusText, Typography.TYPOGRAPHY_CAPTION2, textColor))
                .build(),
        )
        .build()
}

/** 登録が行の上限を超えたときの「他N台」の行。タップでアプリ本体の状態一覧を開く。 */
private fun buildOverflowRow(
    context: Context,
    text: String,
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.dp(ROW_HEIGHT_DP))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(
            chipModifiers(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB)
                .setClickable(buildOpenAppClickable(context.packageName, "open-list"))
                .setSemantics(
                    ModifiersBuilders.Semantics.Builder().setContentDescription("$text タップして一覧を開く").build(),
                )
                .build(),
        )
        .addContent(rowText(context, "$text ›", Typography.TYPOGRAPHY_CAPTION1, ColorBuilders.argb(TEXT_COLOR_ARGB)))
        .build()

private fun rowText(
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

/** アプリ本体（[MainActivity]、状態一覧）を開くClickable。 */
private fun buildOpenAppClickable(
    packageName: String,
    clickableId: String,
): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setId(clickableId)
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build(),
                )
                .build(),
        )
        .build()

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
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.core.display.SesameTileActions
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.wear.R
import com.sesamiwear.wear.action.SesameActionActivity
import com.sesamiwear.wear.action.SesameActionCommandParser
import com.sesamiwear.wear.action.SesameStatusRefreshActivity
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sesame 5のロック状態を表示するWear OS Tile。1つのTileインスタンス（[tileId]）が1台の
 * Sesameデバイスに対応する「複数Tileインスタンス方式」を採る（BL-053）。対象デバイスは
 * [TileDeviceAssignmentStore]でtileIdごとに永続化され、未設定の場合はタップで
 * [TileConfigurationActivity]へ誘導するタイルを表示する。
 * スマホ接続状態は[SesameConnectedNodeProvider]、ロック状態はMobile側がDataClient経由で同期した
 * DataItemの結果から[SesameTileStateResolver]が算出する（BL-015）。対象デバイスuuidが
 * `SesameWearProtocol.ALL_DEVICES_TARGET_UUID`（「全デバイス」選択）の場合は登録済み全デバイスの
 * 状態を集約表示する（BL-071、複数デバイス一括操作）。
 * タイルを左右2分割し、「左上＝更新」「左下＝デバイス変更」「右上＝デバイス名と経路アイコンの帯」
 * 「右下＝状態アイコン・操作（拡大表示）」の4領域を、それぞれ独立した角丸の背景を持つ「チップ」として
 * 表現する（BL-063 / BL-206）。状態色（施錠中=緑/解錠中=赤等）は右下のチップにのみ適用し、それ以外は
 * 中立色にすることで、領域の区切りとステータス色の意味をひと目で区別できるようにする。
 * BL-206より前は左上がデバイス名（タップで状態取得）で、経路は状態チップの中へ絵文字で出していた。全体をタイル端から一定の
 * パディングで内側へ寄せ、各チップ間にも隙間を設けることで、円形画面のセーフエリア（内接正方形）
 * からのテキストのはみ出し・欠けを防ぐ。
 * TileServiceはビルド確認までとする
 * （Android Tiles APIへの依存のためユニットテスト対象外、実機での表示・デバイス切り替えは
 * BL-055の人手検証で2026-09-05に確認済み）。
 */
class SesameTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        val tileId = requestParams.tileId
        Log.d(TAG, "onTileRequest tileId=$tileId")
        CoroutineScope(Dispatchers.IO).launch {
            val deviceUuid = TileDeviceAssignmentStore(applicationContext).assignedDeviceUuid(tileId)
            Log.d(TAG, "onTileRequest tileId=$tileId deviceAssigned=${deviceUuid != null}")
            future.set(
                if (deviceUuid == null) {
                    buildTile(buildUnconfiguredBox(tileId))
                } else {
                    buildConfiguredTile(deviceUuid, tileId)
                },
            )
        }
        return future
    }

    private suspend fun buildConfiguredTile(
        deviceUuid: String,
        tileId: Int,
    ): TileBuilders.Tile {
        val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(applicationContext)
        val displayName = SesameTileStateResolver.resolveDisplayName(applicationContext, deviceUuid)
        val status = SesameTileStateResolver.resolveStatus(applicationContext, deviceUuid, nodeId)
        Log.d(TAG, "buildConfiguredTile tileId=$tileId nodeId=${nodeId != null} state=${status.state}")

        val leftColumn = buildLeftColumn(displayName, deviceUuid, tileId)
        // 右列（BL-206）。上がデバイス名と経路アイコンの帯、下が状態チップ。
        val rightColumn =
            LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .addContent(
                    buildNameHeader(this, displayName, status.route, buildRefreshClickable(packageName, deviceUuid)),
                )
                .addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(CHIP_SPACING_DP)).build(),
                )
                .addContent(buildStatusBox(status, deviceUuid, displayName))
                .build()
        // セーフエリア（内接正方形）からチップがはみ出さないよう、タイル端から内側へ寄せる。
        val root =
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setAll(DimensionBuilders.dp(CONTAINER_PADDING_DP))
                                .build(),
                        )
                        .build(),
                )
                .addContent(
                    LayoutElementBuilders.Row.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.expand())
                        .addContent(leftColumn)
                        .addContent(
                            LayoutElementBuilders.Spacer.Builder()
                                .setWidth(DimensionBuilders.dp(CHIP_SPACING_DP))
                                .build(),
                        )
                        .addContent(rightColumn)
                        .build(),
                )
                .build()
        return buildTile(root)
    }

    /**
     * 左列（「更新」チップ・デバイス変更チップを縦に並べたColumn）を構築する（BL-063 / BL-206）。
     * 2チップの高さは[DimensionBuilders.weight]で均等分割することで、円形画面の上下端に
     * 寄りすぎずセーフエリア内に収まる位置（列の中央寄り）に配置される。状態色とは無関係な
     * 中立色の角丸背景を持たせ、右側のステータスチップと視覚的に区別する。「更新」チップは
     * タップで[SesameStatusRefreshActivity]を起動し、ユーザー契機での状態更新を可能にする。
     * スマホ側は到達実績によらずBLEで取得を試み、届かなければWeb APIへ倒す（BL-204）。
     * BL-206より前はここがデバイス名チップだった（デバイス名は右上の帯へ移した）。
     */
    private fun buildLeftColumn(
        displayName: String,
        deviceUuid: String,
        tileId: Int,
    ): LayoutElementBuilders.LayoutElement {
        val refreshBox =
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.weight(1f))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    buildChipModifiers(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB)
                        .setClickable(buildRefreshClickable(packageName, deviceUuid))
                        .setSemantics(
                            ModifiersBuilders.Semantics.Builder()
                                .setContentDescription("$displayName タップして状態を更新")
                                .build(),
                        )
                        .build(),
                )
                .addContent(
                    Text.Builder(this, REFRESH_LABEL)
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(ColorBuilders.argb(CHIP_NEUTRAL_TEXT_COLOR_ARGB))
                        .setMaxLines(2)
                        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                        .build(),
                )
                .build()
        return LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.dp(LEFT_COLUMN_WIDTH_DP))
            .setHeight(DimensionBuilders.expand())
            .addContent(refreshBox)
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(CHIP_SPACING_DP)).build(),
            )
            .addContent(buildChangeDeviceBox(tileId))
            .build()
    }

    private fun buildTile(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile {
        val layout = LayoutElementBuilders.Layout.Builder().setRoot(root).build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(layout)
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                // 経路アイコン（BL-206）。スマホのウィジェット・アプリ画面と同じMaterialのベクターアイコン。
                .addIdToImageMapping(ROUTE_ICON_BLE_ID, androidImageResource(R.drawable.ic_route_bluetooth))
                .addIdToImageMapping(ROUTE_ICON_WEB_API_ID, androidImageResource(R.drawable.ic_route_internet))
                .build(),
        )

    /**
     * タイル右下（左列とデバイス名の帯を除いた残り全域）を占める角丸のステータスチップ（クリックで施錠/解錠）を
     * 構築する（BL-063）。状態色（施錠中=緑/解錠中=赤等）はこのチップの背景にのみ適用し、
     * 左列（[buildLeftColumn]）とは独立したBoxにすることで、
     * 施錠/解錠のクリック領域とデバイス変更のクリック領域が競合しないようにする。
     */
    private fun buildStatusBox(
        status: SesameTileStatus,
        deviceUuid: String,
        displayName: String,
    ): LayoutElementBuilders.LayoutElement {
        val state = status.state
        val isAllDevices = deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID
        val modifiersBuilder =
            buildChipModifiers(SesameTileContent.backgroundColorArgb(state))
                .setSemantics(
                    ModifiersBuilders.Semantics.Builder()
                        .setContentDescription("$displayName ${SesameTileContent.statusLabel(state, isAllDevices)}")
                        .build(),
                )

        val command = SesameTileActions.commandForState(state)
        if (command != null) {
            modifiersBuilder.setClickable(buildCommandClickable(command, deviceUuid))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            // 右列の中で、デバイス名の帯を除いた残りの高さを占める（BL-206）。
            .setHeight(DimensionBuilders.weight(1f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(buildStatusColumn(this, status, isAllDevices))
            .setModifiers(modifiersBuilder.build())
            .build()
    }

    /**
     * 左列下半分の「デバイス変更」チップ（BL-063）。左上のデバイス名チップと高さを均等分割し、
     * タップでTileConfigurationActivityを再度開けるようにする。状態色とは無関係な中立色の
     * 角丸背景を持たせ、右側のステータスチップと視覚的に区別する。
     */
    private fun buildChangeDeviceBox(tileId: Int): LayoutElementBuilders.LayoutElement {
        val launchAction =
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(TileConfigurationActivity::class.java.name)
                        .addKeyToExtraMapping(
                            TileConfigurationActivity.EXTRA_TILE_ID,
                            ActionBuilders.AndroidStringExtra.Builder().setValue(tileId.toString()).build(),
                        )
                        .build(),
                )
                .build()
        val clickable =
            ModifiersBuilders.Clickable.Builder()
                .setId("change-device")
                .setOnClick(launchAction)
                .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.weight(1f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(buildChipModifiers(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB).setClickable(clickable).build())
            .addContent(
                // 「デバイス変更」の6文字はチップ幅に収まらず「デバイス変／更」と不自然な位置で
                // 折り返していた（BL-102）。上のデバイス名チップ（「全デバイス」5文字）が1行に
                // 収まっていることから、このチップに収まるのは5文字程度と判断し、直上に対象
                // デバイス名が表示されている文脈で意味が通る「変更」へ短縮する。
                Text.Builder(this, "変更")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(ColorBuilders.argb(CHIP_NEUTRAL_TEXT_COLOR_ARGB))
                    .setMaxLines(2)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                    .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                    .build(),
            )
            .build()
    }

    /**
     * チップ（角丸の四角ボタン風のBox）に共通の背景色・角丸・内側パディングを設定した
     * Modifiersビルダーを返す（BL-063）。呼び出し側でsetClickable/setSemantics等を追加できるよう
     * ビルダーのまま返す。
     */
    private fun buildChipModifiers(backgroundColorArgb: Int): ModifiersBuilders.Modifiers.Builder =
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

    private fun buildCommandClickable(
        command: SesameCommand,
        deviceUuid: String,
    ): ModifiersBuilders.Clickable {
        val launchAction =
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(SesameActionActivity::class.java.name)
                        .addKeyToExtraMapping(
                            SesameActionCommandParser.EXTRA_COMMAND,
                            ActionBuilders.AndroidStringExtra.Builder().setValue(command.name).build(),
                        )
                        .addKeyToExtraMapping(
                            SesameActionCommandParser.EXTRA_DEVICE_UUID,
                            ActionBuilders.AndroidStringExtra.Builder().setValue(deviceUuid).build(),
                        )
                        .build(),
                )
                .build()
        return ModifiersBuilders.Clickable.Builder()
            .setId(command.name)
            .setOnClick(launchAction)
            .build()
    }

    private fun buildUnconfiguredBox(tileId: Int): LayoutElementBuilders.LayoutElement {
        val launchAction =
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(TileConfigurationActivity::class.java.name)
                        .addKeyToExtraMapping(
                            TileConfigurationActivity.EXTRA_TILE_ID,
                            ActionBuilders.AndroidStringExtra.Builder().setValue(tileId.toString()).build(),
                        )
                        .build(),
                )
                .build()
        val clickable =
            ModifiersBuilders.Clickable.Builder()
                .setId("configure")
                .setOnClick(launchAction)
                .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, "タップして設定")
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setColor(ColorBuilders.argb(CHIP_NEUTRAL_TEXT_COLOR_ARGB))
                    .build(),
            )
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .build()
    }

    private companion object {
        // 画像リソースを足したら上げる（Tileのホストが古いリソースを使い続けないように）。BL-206で経路アイコンを追加。
        const val RESOURCES_VERSION = "2"

        // 「更新」チップの文言（BL-206）。「変更」と同じく2文字にして左列の幅へ確実に収める。
        const val REFRESH_LABEL = "更新"

        // タイル端からチップ全体を内側へ寄せ、円形画面のセーフエリア（内接正方形）からの
        // はみ出しを防ぐための全体パディング（BL-063）。角丸の一部が見切れるとの指摘を受け、
        // 12f→13f→16fの順に増やした。
        const val CONTAINER_PADDING_DP = 16f

        // チップ同士の隙間（左右2チップ間・左列上下2チップ間で共用）。
        const val CHIP_SPACING_DP = 6f
        const val CHIP_CORNER_RADIUS_DP = 12f
        const val CHIP_INNER_PADDING_DP = 6f
        const val LEFT_COLUMN_WIDTH_DP = 76f

        // 左側2チップの中立色（ダークグレー）に対してコントラストを確保する白系テキスト色。
        const val CHIP_NEUTRAL_TEXT_COLOR_ARGB = 0xFFFFFFFF.toInt()
        const val TAG = "SesameTileService"
    }
}

/**
 * ステータスチップの中身（アイコン・状態文言・最終取得時刻または失敗理由・操作文言を縦に並べたColumn）。
 * 文字サイズはアイコン > 状態文言 > 操作文言 > 最終取得時刻・失敗理由の順に小さくし、
 * 円形画面でも4行が収まるようにする。
 *
 * クラス内のメソッド数がdetektの`TooManyFunctions`閾値（11）に達したため、
 * Contextを引数で受けるトップレベル関数として切り出している。
 */
private fun buildStatusColumn(
    context: Context,
    status: SesameTileStatus,
    isAllDevices: Boolean,
): LayoutElementBuilders.LayoutElement {
    val state = status.state
    val textColor = ColorBuilders.argb(SesameTileContent.statusTextColorArgb(state))
    val statusColumnBuilder =
        LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                // BL-206でデバイス名の帯（約24dp）が上に入り状態チップの高さが減ったため、
                // 4行（アイコン・状態・時刻・操作）が円形画面で見切れないよう、DISPLAY1から一段小さくした。
                Text.Builder(context, SesameTileContent.statusIcon(state))
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                    .setColor(textColor)
                    .build(),
            )
            .addContent(
                // 操作ラベルと同様、既定の1行では長い状態文言が末尾で省略される（BL-104）。
                // 「施錠/解錠混在」は文言側を短縮したが、今後文言を増やしたときに同じ事故が
                // 起きないよう、折り返しを許可して末尾省略を避ける安全網も入れておく。
                Text.Builder(context, SesameTileContent.statusLabel(state, isAllDevices))
                    .setTypography(Typography.TYPOGRAPHY_TITLE2)
                    .setColor(textColor)
                    .setMaxLines(2)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                    .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                    .build(),
            )
    // 最後に状態を取得した時刻、または直近の失敗の理由（BL-142 / BL-140）。自動取得を廃止した
    // ため、表示がどれだけ古いかを利用者が判断できるよう状態文言のすぐ下へ添える。
    // 文言は最長でも「23時間前」「認証エラー」の5〜6文字。経路の絵文字はここへは付けない
    // （緑・赤の上で見分けにくかったため、デバイス名の帯へベクターアイコンで出す。BL-206）。
    status.detailLabel?.let { detailLabel ->
        statusColumnBuilder.addContent(
            Text.Builder(context, detailLabel)
                .setTypography(Typography.TYPOGRAPHY_CAPTION3)
                .setColor(textColor)
                .setMaxLines(1)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                .build(),
        )
    }

    val actionLabel = SesameTileContent.actionLabel(state, isAllDevices)
    if (actionLabel != null) {
        statusColumnBuilder.addContent(
            // ProtoLayoutのTextは既定で1行のため、指定がないと「タップで全解錠」のような
            // 7文字の操作ラベルが「タップで全解…」と末尾で省略されていた（BL-102）。
            // 状態ラベルより一段小さいCAPTION2にしたうえで2行までの折り返しを許可する。
            Text.Builder(context, actionLabel)
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .setColor(textColor)
                .setMaxLines(2)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                .build(),
        )
    }

    return statusColumnBuilder.build()
}

/**
 * 右上のデバイス名の帯（BL-206）。中立色の背景に、デバイス名と経路のベクターアイコンを横に並べる。
 * タップは左上の「更新」と同じ状態取得（BL-206より前のデバイス名タップを引き継ぐ）。
 *
 * 経路アイコンは、BL-168 / BL-173では状態チップの最終取得時刻の行へ絵文字（🔗 / 🌐）で前置していたが、
 * 施錠中＝緑・解錠中＝赤の背景の上では見分けにくかった（2026-09-23のユーザー指摘）。状態によって色の
 * 変わらない中立色の帯へ移し、スマホのウィジェットと同じMaterialのアイコンを白で描く。
 * 経路が分からない場合はアイコンを出さず、デバイス名だけを出す。
 *
 * クラス内のメソッド数がdetektの`TooManyFunctions`閾値に近いため、トップレベル関数にしている。
 */
private fun buildNameHeader(
    context: Context,
    displayName: String,
    route: SesameStatusRoute?,
    clickable: ModifiersBuilders.Clickable,
): LayoutElementBuilders.LayoutElement {
    val row =
        LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(context, displayName)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(ColorBuilders.argb(NAME_HEADER_TEXT_COLOR_ARGB))
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                    .build(),
            )
    routeIconIdOf(route)?.let { iconId ->
        row.addContent(
            LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(NAME_HEADER_ICON_GAP_DP)).build(),
        )
        row.addContent(buildRouteIcon(iconId))
    }
    val description =
        if (route == null) displayName else "$displayName 経路 ${SesameRouteLabel.name(route)}"
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB))
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(DimensionBuilders.dp(NAME_HEADER_CORNER_RADIUS_DP))
                                .build(),
                        )
                        .build(),
                )
                .setPadding(
                    ModifiersBuilders.Padding.Builder()
                        .setStart(DimensionBuilders.dp(NAME_HEADER_HORIZONTAL_PADDING_DP))
                        .setEnd(DimensionBuilders.dp(NAME_HEADER_HORIZONTAL_PADDING_DP))
                        .setTop(DimensionBuilders.dp(NAME_HEADER_VERTICAL_PADDING_DP))
                        .setBottom(DimensionBuilders.dp(NAME_HEADER_VERTICAL_PADDING_DP))
                        .build(),
                )
                .setClickable(clickable)
                .setSemantics(
                    ModifiersBuilders.Semantics.Builder().setContentDescription(description).build(),
                )
                .build(),
        )
        .addContent(row.build())
        .build()
}

/**
 * 状態取得（[SesameStatusRefreshActivity]の起動）のクリック（BL-206）。左上の「更新」と右上の
 * デバイス名の帯で共用する。クラス内のメソッド数を増やさないためトップレベルに置く。
 */
private fun buildRefreshClickable(
    packageName: String,
    deviceUuid: String,
): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setId("refresh-status")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(SesameStatusRefreshActivity::class.java.name)
                        .addKeyToExtraMapping(
                            SesameActionCommandParser.EXTRA_DEVICE_UUID,
                            ActionBuilders.AndroidStringExtra.Builder().setValue(deviceUuid).build(),
                        )
                        .build(),
                )
                .build(),
        )
        .build()

/** 帯の中の経路アイコン（BL-206）。ドローアブルは白だが、明示的に白で着色して色を固定する。 */
private fun buildRouteIcon(iconId: String): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Image.Builder()
        .setResourceId(iconId)
        .setWidth(DimensionBuilders.dp(NAME_HEADER_ICON_SIZE_DP))
        .setHeight(DimensionBuilders.dp(NAME_HEADER_ICON_SIZE_DP))
        .setColorFilter(
            LayoutElementBuilders.ColorFilter.Builder()
                .setTint(ColorBuilders.argb(NAME_HEADER_TEXT_COLOR_ARGB))
                .build(),
        )
        .build()

/** 経路アイコンのリソースID（[SesameTileService.onTileResourcesRequest]で登録するもの）。経路不明ならnull。 */
private fun routeIconIdOf(route: SesameStatusRoute?): String? =
    when (route) {
        SesameStatusRoute.BLE -> ROUTE_ICON_BLE_ID
        SesameStatusRoute.WEB_API -> ROUTE_ICON_WEB_API_ID
        null -> null
    }

private fun androidImageResource(resId: Int): ResourceBuilders.ImageResource =
    ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(
            ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(resId).build(),
        )
        .build()

private const val ROUTE_ICON_BLE_ID = "route_ble"
private const val ROUTE_ICON_WEB_API_ID = "route_web_api"

// デバイス名の帯の寸法（BL-206）。状態チップの高さを削りすぎないよう、上下の余白を他のチップ（6dp）より詰める。
// 帯の高さは CAPTION2（12sp≒16dp）＋3dp×2＝約22dp。
private const val NAME_HEADER_VERTICAL_PADDING_DP = 3f
private const val NAME_HEADER_HORIZONTAL_PADDING_DP = 6f
private const val NAME_HEADER_CORNER_RADIUS_DP = 12f
private const val NAME_HEADER_ICON_SIZE_DP = 12f
private const val NAME_HEADER_ICON_GAP_DP = 3f
private const val NAME_HEADER_TEXT_COLOR_ARGB = 0xFFFFFFFF.toInt()

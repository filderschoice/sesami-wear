package com.sesamiwear.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
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
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.wear.action.SesameActionActivity
import com.sesamiwear.wear.action.SesameActionCommandParser
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sesame 5のロック状態を表示するWear OS Tile。1つのTileインスタンス（[tileId]）が1台の
 * Sesameデバイスに対応する「複数Tileインスタンス方式」を採る（BL-053）。対象デバイスは
 * [TileDeviceAssignmentStore]でtileIdごとに永続化され、未設定の場合はタップで
 * [TileConfigurationActivity]へ誘導するタイルを表示する。
 * スマホ接続状態は[SesameConnectedNodeProvider]、ロック状態はMobile側がDataClient経由で同期した
 * [SesameStatusSnapshotReader]の結果から算出する（BL-015）。
 * TileServiceはビルド確認までとする
 * （Android Tiles APIへの依存のためユニットテスト対象外、プレビュー確認は自動実行不可のためBL-055で人手検証）。
 */
class SesameTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        val tileId = requestParams.tileId
        CoroutineScope(Dispatchers.IO).launch {
            val deviceUuid = TileDeviceAssignmentStore(applicationContext).assignedDeviceUuid(tileId)
            future.set(if (deviceUuid == null) buildUnconfiguredTile(tileId) else buildConfiguredTile(deviceUuid))
        }
        return future
    }

    private suspend fun buildConfiguredTile(deviceUuid: String): TileBuilders.Tile {
        val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(applicationContext)
        // Tile表示のたびにmobile側へ最新状態の取得を依頼する（BL-061）。レスポンスを待たず、
        // 今回は既存のDataItemスナップショットで即座にTileを構築する（Tilesのレスポンス
        // タイムアウト制約を避けるため）。取得結果はDataItem変更として非同期に届き、
        // SesameStatusListenerServiceがTileの再描画をリクエストする。
        if (nodeId != null) {
            SesameCommandSenderProvider.create(applicationContext).requestStatus(nodeId, deviceUuid)
        }
        val snapshot = SesameStatusSnapshotReader.readLatest(applicationContext, deviceUuid)
        val state =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = nodeId != null,
                isCommandInProgress = false,
                isLocked = snapshot?.isLocked,
            )
        return buildTile(buildStatusBox(state, deviceUuid))
    }

    private fun buildUnconfiguredTile(tileId: Int): TileBuilders.Tile = buildTile(buildUnconfiguredBox(tileId))

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
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun buildStatusBox(
        state: TileDisplayState,
        deviceUuid: String,
    ): LayoutElementBuilders.LayoutElement {
        val modifiersBuilder =
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(SesameTileContent.backgroundColorArgb(state)))
                        .build(),
                )
                .setSemantics(
                    ModifiersBuilders.Semantics.Builder()
                        .setContentDescription(SesameTileContent.statusLabel(state))
                        .build(),
                )

        val command = SesameTileActions.commandForState(state)
        if (command != null) {
            modifiersBuilder.setClickable(buildCommandClickable(command, deviceUuid))
        }

        return LayoutElementBuilders.Box.Builder()
            .addContent(
                Text.Builder(this, SesameTileContent.statusLabel(state))
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .build(),
            )
            .setModifiers(modifiersBuilder.build())
            .build()
    }

    private fun buildUnconfiguredBox(tileId: Int): LayoutElementBuilders.LayoutElement {
        val clickable =
            ModifiersBuilders.Clickable.Builder()
                .setId("configure")
                .setOnClick(buildConfigurationLaunchAction(tileId))
                .build()
        return LayoutElementBuilders.Box.Builder()
            .addContent(
                Text.Builder(this, "タップして設定")
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .build(),
            )
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
            .build()
    }

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

    private fun buildConfigurationLaunchAction(tileId: Int): ActionBuilders.LaunchAction =
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

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}

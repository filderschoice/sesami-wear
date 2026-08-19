package com.sesamiwear.wear.tile

import androidx.wear.protolayout.ActionBuilders
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
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sesame 5のロック状態を表示するWear OS Tile。
 * スマホ接続状態は[SesameConnectedNodeProvider]、ロック状態はMobile側がDataClient経由で同期した
 * [SesameStatusSnapshotReader]の結果から算出する（BL-015）。
 * TileServiceはビルド確認までとする
 * （Android Tiles APIへの依存のためユニットテスト対象外、プレビュー確認は自動実行不可のためBL-011で人手検証）。
 */
class SesameTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        CoroutineScope(Dispatchers.IO).launch {
            val isPhoneConnected = SesameConnectedNodeProvider.firstConnectedNodeId(applicationContext) != null
            val snapshot = SesameStatusSnapshotReader.readLatest(applicationContext)
            val state =
                TileDisplayStateResolver.resolve(
                    isPhoneConnected = isPhoneConnected,
                    isCommandInProgress = false,
                    isLocked = snapshot?.isLocked,
                )
            future.set(buildTile(state))
        }
        return future
    }

    private fun buildTile(state: TileDisplayState): TileBuilders.Tile {
        val layout =
            LayoutElementBuilders.Layout.Builder()
                .setRoot(buildStatusBox(state))
                .build()

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

    private fun buildStatusBox(state: TileDisplayState): LayoutElementBuilders.LayoutElement {
        val boxBuilder =
            LayoutElementBuilders.Box.Builder()
                .addContent(
                    Text.Builder(this, SesameTileContent.statusLabel(state))
                        .setTypography(Typography.TYPOGRAPHY_BODY1)
                        .build(),
                )

        val command = SesameTileActions.commandForState(state)
        if (command != null) {
            boxBuilder.setModifiers(clickableModifiers(command))
        }
        return boxBuilder.build()
    }

    private fun clickableModifiers(command: SesameCommand): ModifiersBuilders.Modifiers {
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
                        .build(),
                )
                .build()
        return ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(command.name)
                    .setOnClick(launchAction)
                    .build(),
            )
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}

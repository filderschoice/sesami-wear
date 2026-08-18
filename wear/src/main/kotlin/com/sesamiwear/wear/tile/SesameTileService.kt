package com.sesamiwear.wear.tile

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver

/**
 * Sesame 5のロック状態を表示するWear OS Tile。
 * 実データ（スマホ接続状態・ロック状態）との結線、施錠/解錠アクションの実装はBL-013/BL-014で行う。
 * 本タスク（BL-007）では表示ロジックのみを実装し、TileServiceはビルド確認までとする
 * （Android Tiles APIへの依存のためユニットテスト対象外、プレビュー確認は自動実行不可のためBL-011で人手検証）。
 */
class SesameTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val state =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = false,
                isCommandInProgress = false,
                isLocked = null,
            )

        val layout =
            LayoutElementBuilders.Layout.Builder()
                .setRoot(buildStatusBox(state))
                .build()

        val tile =
            TileBuilders.Tile.Builder()
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

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun buildStatusBox(state: TileDisplayState): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .addContent(
                Text.Builder(this, SesameTileContent.statusLabel(state))
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .build(),
            )
            .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}

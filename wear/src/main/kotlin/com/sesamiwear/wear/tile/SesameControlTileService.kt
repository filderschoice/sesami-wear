package com.sesamiwear.wear.tile

import android.content.Context
import android.util.Log
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
import com.sesamiwear.core.display.SesameControlTileContent
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.messaging.SesameDeviceListReader
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 操作専用のWear OS Tile（BL-298）。[SesameTileService]（1台ごとの状態表示と操作）とは別のタイルとして、
 * タイル追加の一覧へ並ぶ。登録済みの全デバイスが対象で、デバイスの割り当て（[TileDeviceAssignmentStore]）は
 * 持たない（tileIdの保存もしない）。
 *
 * 上段に大きな「全台の鍵を操作」（1台だけのときは「鍵を操作」）のボタン1つ、下段に台ごとの状態チップを横に並べる
 * （3台までは1段、それを超えると2段。さらに超えると末尾のチップが「他N台」）。
 * 上段のボタンと台のチップのタップは、どちらも施錠・解錠を選ぶ画面
 * （[com.sesamiwear.wear.action.SesameActionActivity]）を開き、そこで押した方を実行する（タップだけでは何も送らない）。
 * 上段はもとは「全施錠」（1タップで実行）と「全解錠」の2ボタンだったが、BL-307で1つにまとめた。表示の規則は[SesameControlTileContent]が持つ。
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
            val content =
                try {
                    loadContent(context, requestParams.tileId)
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
                ) {
                    // 読み取りの失敗でfutureが未完了のまま描画が止まらないよう、未接続表示（ボタン無効）で完了させる。
                    // 例外の本文は出さず、固定文だけを記録する。
                    Log.w(TAG, "onTileRequest 状態の読み取りに失敗したため未接続表示にします")
                    fallbackContent(context)
                }
            future.set(buildTile(if (content == null) buildEmptyBox() else buildControlTileContent(context, content)))
        }
        return future
    }

    private suspend fun loadContent(
        context: Context,
        tileId: Int,
    ): SesameControlTileContent.Content? {
        val devices = SesameDeviceListReader.readLatest(context)
        val snapshots = devices.associate { it.uuid to SesameStatusSnapshotReader.readLatest(context, it.uuid) }
        val connected = SesameConnectedNodeProvider.firstConnectedNodeId(context) != null
        Log.d(TAG, "onTileRequest tileId=$tileId devices=${devices.size} connected=$connected")
        return SesameControlTileContent.build(devices, { snapshots[it]?.isLocked }, connected)
    }

    /** 読み取りに失敗したときの表示。台の一覧が読めれば、状態不明・未接続（全ボタン無効）で出す。 */
    private suspend fun fallbackContent(context: Context): SesameControlTileContent.Content? {
        val devices =
            try {
                SesameDeviceListReader.readLatest(context)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                emptyList()
            }
        return SesameControlTileContent.build(devices, { null }, phoneConnected = false)
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

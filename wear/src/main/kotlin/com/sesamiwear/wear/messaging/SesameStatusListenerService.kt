package com.sesamiwear.wear.messaging

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.wear.complication.SesameComplicationDataSourceService
import com.sesamiwear.wear.tile.SesameTileService

/**
 * mobile側が状態取得リクエスト（[SesameWearProtocol.PATH_STATUS_REQUEST]、BL-061）に応じて
 * [SesameWearProtocol.STATUS_DATA_ITEM_PATH]配下のDataItemを更新した際、
 * Tile/Complicationの再描画をリクエストし最新状態を反映させる。
 */
class SesameStatusListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val statusChanged =
            dataEvents.any {
                it.dataItem.uri.path?.startsWith(SesameWearProtocol.STATUS_DATA_ITEM_PATH) == true
            }
        dataEvents.release()
        if (!statusChanged) return

        TileService.getUpdater(applicationContext).requestUpdate(SesameTileService::class.java)
        ComplicationDataSourceUpdateRequester.create(
            applicationContext,
            ComponentName(applicationContext, SesameComplicationDataSourceService::class.java),
        ).requestUpdateAll()
    }
}

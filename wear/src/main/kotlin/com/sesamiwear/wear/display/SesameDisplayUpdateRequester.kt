package com.sesamiwear.wear.display

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.sesamiwear.wear.complication.SesameComplicationDataSourceService
import com.sesamiwear.wear.tile.SesameTileService

/**
 * Tile・Complicationの再描画をまとめてリクエストする。
 * mobile側からのDataItem更新を受けたとき（[com.sesamiwear.wear.messaging.SesameStatusListenerService]）と、
 * デモモードでwear単体のダミー状態を書き換えたとき（BL-109、[com.sesamiwear.wear.demo.DemoLockStateStore]）の
 * 双方から呼ぶため、共通化している。
 * Android Tiles / Complications APIへの依存のためユニットテスト対象外。
 */
object SesameDisplayUpdateRequester {
    fun requestUpdateAll(context: Context) {
        TileService.getUpdater(context).requestUpdate(SesameTileService::class.java)
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, SesameComplicationDataSourceService::class.java),
        ).requestUpdateAll()
    }
}

package com.sesamiwear.wear.messaging

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.wear.display.SesameDisplayUpdateRequester

/**
 * mobile側が状態取得リクエスト（[SesameWearProtocol.PATH_STATUS_REQUEST]、BL-061）に応じて
 * [SesameWearProtocol.STATUS_DATA_ITEM_PATH]配下のDataItemを更新した際、
 * Tile/Complicationの再描画をリクエストし最新状態を反映させる。登録デバイス一覧
 * （[SesameWearProtocol.DEVICE_LIST_DATA_ITEM_PATH]）の更新でも再描画する。デモの割り当てのまま
 * 実デバイスが登録されたTile・Complicationを未設定へ戻すため（BL-267）。
 */
class SesameStatusListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val statusChanged =
            dataEvents.any {
                val path = it.dataItem.uri.path
                path?.startsWith(SesameWearProtocol.STATUS_DATA_ITEM_PATH) == true ||
                    path == SesameWearProtocol.DEVICE_LIST_DATA_ITEM_PATH
            }
        dataEvents.release()
        if (!statusChanged) return

        SesameDisplayUpdateRequester.requestUpdateAll(applicationContext)
    }
}

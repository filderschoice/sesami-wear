package com.sesamiwear.wear.messaging

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * DataClient（DataItem）からMobile側が同期した登録済みデバイス一覧を読み取る（BL-052）。
 * Tile Configuration Activityでのデバイス選択肢表示に用いる。
 * Android Google Play Services依存の薄いアダプタのためユニットテスト対象外
 * （実際の同期動作はBL-055の人手検証で2026-09-05に実機確認済み）。
 */
object SesameDeviceListReader {
    suspend fun readLatest(context: Context): List<SesameDeviceSummary> {
        val dataItems = Wearable.getDataClient(context).dataItems.await()
        try {
            val item =
                (0 until dataItems.count)
                    .map { dataItems[it] }
                    .find { it.uri.path == SesameWearProtocol.DEVICE_LIST_DATA_ITEM_PATH }
            val json =
                item?.let { DataMapItem.fromDataItem(it).dataMap.getString(SesameWearProtocol.KEY_DEVICE_LIST_JSON) }
            return json?.let { decode(it) } ?: emptyList()
        } finally {
            dataItems.release()
        }
    }

    private fun decode(json: String): List<SesameDeviceSummary> =
        try {
            Json.decodeFromString(json)
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            emptyList()
        }
}

package com.sesamiwear.wear.messaging

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.SesameStatusSnapshotFactory
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.tasks.await

/**
 * DataClient（DataItem）からMobile側が同期した最新のロック状態を読み取る。
 * Android Google Play Services依存の薄いアダプタのためユニットテスト対象外
 * （実際の同期動作はBL-011で人手検証）。
 */
object SesameStatusSnapshotReader {
    suspend fun readLatest(context: Context): SesameStatusSnapshot? {
        val dataItems = Wearable.getDataClient(context).dataItems.await()
        try {
            for (i in 0 until dataItems.count) {
                val item = dataItems[i]
                if (item.uri.path != SesameWearProtocol.STATUS_DATA_ITEM_PATH) continue
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                return SesameStatusSnapshotFactory.create(
                    hasIsLockedKey = dataMap.containsKey(SesameWearProtocol.KEY_IS_LOCKED),
                    isLocked = dataMap.getBoolean(SesameWearProtocol.KEY_IS_LOCKED),
                    updatedAtEpochMillis = dataMap.getLong(SesameWearProtocol.KEY_UPDATED_AT_EPOCH_MILLIS),
                )
            }
            return null
        } finally {
            dataItems.release()
        }
    }
}

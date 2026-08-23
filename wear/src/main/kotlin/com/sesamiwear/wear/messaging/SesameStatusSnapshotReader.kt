package com.sesamiwear.wear.messaging

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.SesameStatusSnapshotFactory
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.tasks.await

/**
 * DataClient（DataItem）からMobile側が同期した、指定デバイス（[uuid]）の最新のロック状態を
 * 読み取る（BL-050/BL-053、[SesameWearProtocol.statusDataItemPath]でデバイスごとに区別）。
 * Android Google Play Services依存の薄いアダプタのためユニットテスト対象外
 * （実際の同期動作はBL-055で人手検証）。
 */
object SesameStatusSnapshotReader {
    suspend fun readLatest(
        context: Context,
        uuid: String,
    ): SesameStatusSnapshot? {
        val dataItems = Wearable.getDataClient(context).dataItems.await()
        try {
            val path = SesameWearProtocol.statusDataItemPath(uuid)
            val item = (0 until dataItems.count).map { dataItems[it] }.find { it.uri.path == path } ?: return null
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            return SesameStatusSnapshotFactory.create(
                hasIsLockedKey = dataMap.containsKey(SesameWearProtocol.KEY_IS_LOCKED),
                isLocked = dataMap.getBoolean(SesameWearProtocol.KEY_IS_LOCKED),
                updatedAtEpochMillis = dataMap.getLong(SesameWearProtocol.KEY_UPDATED_AT_EPOCH_MILLIS),
            )
        } finally {
            dataItems.release()
        }
    }
}

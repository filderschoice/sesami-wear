package com.sesamiwear.mobile.messaging

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.tasks.await

/**
 * コマンド実行結果に基づくロック状態をDataClient（DataItem）経由でWear側へ同期する。
 * Android依存の薄いアダプタのためユニットテスト対象外。
 */
class SesameStatusSyncer(private val context: Context) {
    suspend fun syncLocked(isLocked: Boolean) {
        val request = PutDataMapRequest.create(SesameWearProtocol.STATUS_DATA_ITEM_PATH)
        request.dataMap.putBoolean(SesameWearProtocol.KEY_IS_LOCKED, isLocked)
        request.dataMap.putLong(SesameWearProtocol.KEY_UPDATED_AT_EPOCH_MILLIS, System.currentTimeMillis())
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }
}

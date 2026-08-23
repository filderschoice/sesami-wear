package com.sesamiwear.mobile.messaging

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.tasks.await

/**
 * コマンド実行結果に基づくロック状態をDataClient（DataItem）経由でWear側へ同期する。
 * デバイスごとにDataItemパスを分ける（[SesameWearProtocol.statusDataItemPath]、BL-050）ため、
 * 複数デバイスの状態が同一DataItemで上書き衝突しない。
 * Android依存の薄いアダプタのためユニットテスト対象外。
 */
class SesameStatusSyncer(private val context: Context) {
    suspend fun syncLocked(
        uuid: String,
        isLocked: Boolean,
    ) {
        val request = PutDataMapRequest.create(SesameWearProtocol.statusDataItemPath(uuid))
        request.dataMap.putBoolean(SesameWearProtocol.KEY_IS_LOCKED, isLocked)
        request.dataMap.putLong(SesameWearProtocol.KEY_UPDATED_AT_EPOCH_MILLIS, System.currentTimeMillis())
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }
}

package com.sesamiwear.mobile.messaging

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 登録済みSesameデバイスの一覧（uuid/displayNameのみ、apiKey/secretKeyは含めない）を
 * DataClient（DataItem）経由でWear側へ同期する（BL-052）。wear側のTile Configuration
 * Activityでのデバイス選択肢表示に用いる。Android依存の薄いアダプタのためユニットテスト対象外。
 */
class SesameDeviceListSyncer(private val context: Context) {
    suspend fun sync(credentialsList: List<SesameCredentials>) {
        val summaries = credentialsList.map { SesameDeviceSummary(uuid = it.uuid, displayName = it.displayName) }
        val request = PutDataMapRequest.create(SesameWearProtocol.DEVICE_LIST_DATA_ITEM_PATH)
        request.dataMap.putString(SesameWearProtocol.KEY_DEVICE_LIST_JSON, Json.encodeToString(summaries))
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }
}

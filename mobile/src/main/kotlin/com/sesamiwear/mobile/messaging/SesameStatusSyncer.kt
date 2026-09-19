package com.sesamiwear.mobile.messaging

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.tasks.await

/**
 * コマンド実行結果に基づくロック状態をDataClient（DataItem）経由でWear側へ同期する。
 * デバイスごとにDataItemパスを分ける（[SesameWearProtocol.statusDataItemPath]、BL-050）ため、
 * 複数デバイスの状態が同一DataItemで上書き衝突しない。
 * Wearable APIが使えない端末（Wear OSのコンパニオンアプリ未導入）でも例外を外へ送出しない
 * （[DataLayerBestEffort]、BL-118）。
 * Android依存の薄いアダプタのためユニットテスト対象外。
 */
class SesameStatusSyncer(private val context: Context) {
    /**
     * [snapshot]（最後に分かった施錠状態と、直近の失敗、BL-140）をそのままDataItemへ載せる。
     * 施錠状態が未取得、または直近の取得・操作が成功した場合は、対応するキーを載せない
     * （wear側は[com.sesamiwear.core.SesameStatusSnapshotFactory]でキーの有無から復元する）。
     */
    suspend fun sync(
        uuid: String,
        snapshot: SesameStatusSnapshot,
    ) {
        DataLayerBestEffort.run(onFailure = { Log.w(TAG, "sync skipped: statusCode=$it") }) {
            val request = PutDataMapRequest.create(SesameWearProtocol.statusDataItemPath(uuid))
            snapshot.isLocked?.let { request.dataMap.putBoolean(SesameWearProtocol.KEY_IS_LOCKED, it) }
            snapshot.updatedAtEpochMillis?.let {
                request.dataMap.putLong(SesameWearProtocol.KEY_UPDATED_AT_EPOCH_MILLIS, it)
            }
            snapshot.lastFailure?.let { request.dataMap.putString(SesameWearProtocol.KEY_LAST_FAILURE, it.name) }
            // 電池残量・角度・経路（BL-166）。分かっていない項目はキー自体を載せない。
            snapshot.batteryPercentage?.let { request.dataMap.putInt(SesameWearProtocol.KEY_BATTERY_PERCENTAGE, it) }
            snapshot.position?.let { request.dataMap.putInt(SesameWearProtocol.KEY_POSITION, it) }
            snapshot.lastRoute?.let { request.dataMap.putString(SesameWearProtocol.KEY_LAST_ROUTE, it.name) }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
        }
    }

    private companion object {
        const val TAG = "SesameStatusSyncer"
    }
}

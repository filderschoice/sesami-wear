package com.sesamiwear.wear.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.wear.display.SesameDisplayUpdateRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ウォッチ単独で表示を検証するための入口（BL-187）。**デバッグビルドにだけマージされる。**
 *
 * Tile・Complication・状態一覧は、スマートフォンが同期したDataItemを読んで描画する。
 * エミュレータではスマートフォンとのペア設定ができない（Wear OSコンパニオンアプリがPlayストア入りの
 * システムイメージとGoogleアカウントのサインインを要する）ため、**ウォッチ自身が同じパス・同じキーの
 * DataItemを書いて**、読み出し側（`SesameDeviceListReader` / `SesameStatusSnapshotReader`）へ
 * 実機と同じ入力を与える。DataClientはパスで引くため、書き手がどのノードでも読み出し側は変わらない。
 *
 * 実資格情報は扱わない。uuidは検証用のダミー値を引数で受け取るだけで、secretKey・apikeyは
 * この経路に一切現れない（`rules/guardrails-unified.v1.md` 12.5）。
 *
 * ```
 * # 登録済みデバイス一覧を注入する（uuid:表示名 のカンマ区切り）
 * adb shell am broadcast -a com.sesamiwear.wear.debug.STATE \
 *   -n com.sesamiwear.mobile.debug/com.sesamiwear.wear.debug.SesameWearDebugReceiver \
 *   --es devices "aaaa1111:Entrance,bbbb2222:Garage"
 *
 * # 1台の状態を注入する（route は BLE / WEB_API、failure は AUTH_OR_QUOTA / COMMUNICATION）
 * adb shell am broadcast -a com.sesamiwear.wear.debug.STATE \
 *   -n com.sesamiwear.mobile.debug/com.sesamiwear.wear.debug.SesameWearDebugReceiver \
 *   --es uuid aaaa1111 --ez locked true --ei battery 85 --ei position 0 --es route BLE
 * ```
 *
 * 結果は`adb logcat -s SesameWearDebug`で確認できる。
 */
class SesameWearDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                intent.getStringExtra(EXTRA_DEVICES)?.let { putDeviceList(appContext, it) }
                intent.getStringExtra(EXTRA_UUID)?.let { putStatus(appContext, it, intent) }
                SesameDisplayUpdateRequester.requestUpdateAll(appContext)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w(TAG, "inject failed: ${e.javaClass.simpleName}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** `SesameDeviceListSyncer`（mobile側）と同じパス・キーで登録済みデバイス一覧を書く。 */
    private suspend fun putDeviceList(
        context: Context,
        spec: String,
    ) {
        val summaries =
            spec.split(",").filter { it.isNotBlank() }.map { entry ->
                val parts = entry.split(":")
                SesameDeviceSummary(uuid = parts.first().trim(), displayName = parts.getOrNull(1)?.trim().orEmpty())
            }
        val request = PutDataMapRequest.create(SesameWearProtocol.DEVICE_LIST_DATA_ITEM_PATH)
        request.dataMap.putString(SesameWearProtocol.KEY_DEVICE_LIST_JSON, Json.encodeToString(summaries))
        putDataItem(context, request)
        Log.w(TAG, "devices injected: count=${summaries.size}")
    }

    /** `SesameStatusSyncer`（mobile側）と同じパス・キーで1台分の状態を書く。 */
    private suspend fun putStatus(
        context: Context,
        uuid: String,
        intent: Intent,
    ) {
        val request = PutDataMapRequest.create(SesameWearProtocol.statusDataItemPath(uuid))
        if (intent.hasExtra(EXTRA_LOCKED)) {
            request.dataMap.putBoolean(SesameWearProtocol.KEY_IS_LOCKED, intent.getBooleanExtra(EXTRA_LOCKED, true))
        }
        request.dataMap.putLong(
            SesameWearProtocol.KEY_UPDATED_AT_EPOCH_MILLIS,
            intent.getLongExtra(EXTRA_UPDATED_AT, System.currentTimeMillis()),
        )
        if (intent.hasExtra(EXTRA_BATTERY)) {
            request.dataMap.putInt(SesameWearProtocol.KEY_BATTERY_PERCENTAGE, intent.getIntExtra(EXTRA_BATTERY, 0))
        }
        if (intent.hasExtra(EXTRA_POSITION)) {
            request.dataMap.putInt(SesameWearProtocol.KEY_POSITION, intent.getIntExtra(EXTRA_POSITION, 0))
        }
        SesameStatusRoute.ofNameOrNull(intent.getStringExtra(EXTRA_ROUTE))?.let {
            request.dataMap.putString(SesameWearProtocol.KEY_LAST_ROUTE, it.name)
        }
        SesameStatusFailure.ofNameOrNull(intent.getStringExtra(EXTRA_FAILURE))?.let {
            request.dataMap.putString(SesameWearProtocol.KEY_LAST_FAILURE, it.name)
        }
        putDataItem(context, request)
        Log.w(TAG, "status injected: path=${SesameWearProtocol.statusDataItemPath(uuid)}")
    }

    private suspend fun putDataItem(
        context: Context,
        request: PutDataMapRequest,
    ) {
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    private companion object {
        const val TAG = "SesameWearDebug"
        const val EXTRA_DEVICES = "devices"
        const val EXTRA_UUID = "uuid"
        const val EXTRA_LOCKED = "locked"
        const val EXTRA_UPDATED_AT = "updated_at"
        const val EXTRA_BATTERY = "battery"
        const val EXTRA_POSITION = "position"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_FAILURE = "failure"
    }
}

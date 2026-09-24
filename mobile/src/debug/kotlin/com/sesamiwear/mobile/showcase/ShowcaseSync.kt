package com.sesamiwear.mobile.showcase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.mobile.messaging.DataLayerBestEffort
import com.sesamiwear.mobile.messaging.SesameDeviceListSyncer
import com.sesamiwear.mobile.messaging.SesameStatusSyncer
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.widget.SesameWidgetUpdater
import kotlinx.coroutines.tasks.await

/**
 * 撮影モード（BL-212）の切り替えと撮影用の状態の書き換え。**デバッグ版にだけ含まれる。**
 *
 * ウォッチはスマートフォンから届いたDataItemだけを表示するため、モードを切り替えたら、
 * そのときのデバイス一覧と各デバイスの状態をウォッチへ同期し直す。ウィジェットも描き直す。
 * Data Layerの失敗は[DataLayerBestEffort]で握り、呼び出し元の画面を止めない。
 */
object ShowcaseSync {
    private const val TAG = "SesameShowcase"

    /**
     * 撮影モードを切り替え、ウォッチとウィジェットを切り替え後の内容で描き直す。
     * 撮影モードを抜けるときは、撮影用デバイスの状態のDataItemをウォッチから消す
     * （戻したあとの実物の一覧・状態は、実物の保存値からそのまま同期し直す）。
     */
    suspend fun setActive(
        context: Context,
        active: Boolean,
    ) {
        val appContext = context.applicationContext
        val showcaseUuids = ShowcaseMode.showcaseDevices(appContext).loadAll().map { it.uuid }
        ShowcaseMode.writeActive(appContext, active)
        Log.w(TAG, "showcase mode active=$active")
        if (!active) showcaseUuids.forEach { deleteSyncedStatus(appContext, it) }
        resync(appContext)
    }

    /** 撮影用デバイスを見本（[ShowcasePresets]）へ置き換える。撮影モード中でなくても撮影用の保存先へ書く。 */
    suspend fun loadPresets(context: Context) {
        val appContext = context.applicationContext
        val credentialsStore = ShowcaseMode.showcaseDevices(appContext)
        val lockStateStore = ShowcaseMode.showcaseLockStateStore(appContext)
        val previous = credentialsStore.loadAll()
        previous.forEach { lockStateStore.remove(it.uuid) }
        credentialsStore.saveAll(ShowcasePresets.devices.map { it.first })
        val now = System.currentTimeMillis()
        ShowcasePresets.devices.forEach { (credentials, state) -> state.writeTo(lockStateStore, credentials.uuid, now) }
        if (ShowcaseMode.isActive(appContext)) {
            previous.filterNot { old -> ShowcasePresets.devices.any { it.first.uuid == old.uuid } }
                .forEach { deleteSyncedStatus(appContext, it.uuid) }
            resync(appContext)
        }
    }

    /** 撮影用デバイス1台の状態を[state]へ置き換え、撮影モード中ならウォッチとウィジェットへ知らせる。 */
    suspend fun applyState(
        context: Context,
        uuid: String,
        state: ShowcaseDeviceState,
    ) {
        val appContext = context.applicationContext
        val store = ShowcaseMode.showcaseLockStateStore(appContext)
        state.writeTo(store, uuid, System.currentTimeMillis())
        if (ShowcaseMode.isActive(appContext)) {
            store.load(uuid)?.let { SesameStatusSyncer(appContext).sync(uuid, it) }
                ?: deleteSyncedStatus(appContext, uuid)
            SesameWidgetUpdater.updateAll(appContext)
        }
    }

    /** いまのモードに応じたデバイス一覧と状態を、ウォッチとウィジェットへ出し直す。 */
    private suspend fun resync(appContext: Context) {
        val devices = SesameDeviceStores.credentials(appContext).loadAll()
        val lockStateStore = SesameDeviceStores.lockState(appContext)
        SesameDeviceListSyncer(appContext).sync(devices)
        devices.forEach { device ->
            lockStateStore.load(device.uuid)?.let { SesameStatusSyncer(appContext).sync(device.uuid, it) }
        }
        SesameWidgetUpdater.updateAll(appContext)
    }

    private suspend fun deleteSyncedStatus(
        appContext: Context,
        uuid: String,
    ) {
        DataLayerBestEffort.run(onFailure = { Log.w(TAG, "status delete skipped: statusCode=$it") }) {
            val uri = Uri.Builder().scheme("wear").path(SesameWearProtocol.statusDataItemPath(uuid)).build()
            Wearable.getDataClient(appContext).deleteDataItems(uri).await()
        }
    }
}

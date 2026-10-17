package com.sesamiwear.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.mobile.EntryPointGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 設定メニューから要求したウィジェットの配置（[WidgetPinRequester]、BL-262）の完了通知を受ける。
 * `exported="false"`で、ランチャーが自アプリのPendingIntentを送ったときだけ呼ばれる。
 *
 * 選択肢が1つなら対象を割り当てて再描画する（判定は[WidgetPinPlacement]）。選択画面をここから開かないのは、
 * バックグラウンドからのActivity起動が制限されるうえ、ランチャーが自ら選択画面を開く場合に二重に開くため。
 * Intentからの値の取り出しと配線だけの薄いアダプタのためユニットテスト対象外。
 */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                EntryPointGuard.run(onFailure = { Log.w(TAG, "pinned widget setup failed: $it") }) {
                    assignIfSingleChoice(appContext, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun assignIfSingleChoice(
        context: Context,
        appWidgetId: Int,
    ) {
        val store = SesameWidgetRepository.assignmentStore(context)
        val choices = SesameDeviceTargets.choices(SesameWidgetRepository.loadRegisteredDevices(context))
        val uuid = WidgetPinPlacement.autoAssignUuid(choices, store.assignedDeviceUuid(appWidgetId)) ?: return
        store.assign(appWidgetId, uuid)
        SesameWidgetUpdater.update(context, appWidgetId)
    }

    private companion object {
        const val TAG = "SesameWidgetPin"
    }
}

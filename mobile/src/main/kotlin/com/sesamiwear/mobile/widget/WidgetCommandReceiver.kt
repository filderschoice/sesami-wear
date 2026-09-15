package com.sesamiwear.mobile.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ウィジェットのタップ（施錠の即時実行・デバイス名の状態取得）と、解錠確認画面での確定を受けて
 * [WidgetCommandRunner]を実行する（BL-122）。`exported="false"`で、自アプリのPendingIntentと
 * [WidgetUnlockConfirmActivity]からのみ呼ばれる。
 *
 * バックグラウンド実行は`goAsync`で行う（GlanceのActionCallbackと同じ仕組み）。解錠確認画面からも同じ経路で
 * 実行するため、Glanceの`actionRunCallback`ではなく自前のBroadcastReceiverにしている。BroadcastReceiverには
 * 実行時間の制約があるため、全デバイスの操作は[WidgetCommandRunner]が並行して呼び、所要時間を1台分に
 * 近づけている（実機で制約に抵触しないかはBL-126で確認する）。
 * Intentからの値の取り出しと配線だけの薄いアダプタのためユニットテスト対象外。
 */
class WidgetCommandReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val deviceUuid = intent.getStringExtra(EXTRA_DEVICE_UUID)
        if (deviceUuid.isNullOrEmpty()) return
        val action = intent.action
        val command = parseCommand(intent.getStringExtra(EXTRA_COMMAND))
        if (action == ACTION_RUN_COMMAND && command == null) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val runner = createRunner(appContext)
                when (action) {
                    ACTION_RUN_COMMAND -> command?.let { runner.runCommand(deviceUuid, it) }
                    ACTION_REFRESH_STATUS -> runner.refreshStatus(deviceUuid)
                    else -> Log.w(TAG, "unknown action")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SesameWidgetCommand"
        private const val ACTION_RUN_COMMAND = "com.sesamiwear.mobile.widget.RUN_COMMAND"
        private const val ACTION_REFRESH_STATUS = "com.sesamiwear.mobile.widget.REFRESH_STATUS"
        private const val EXTRA_DEVICE_UUID = "device_uuid"
        private const val EXTRA_COMMAND = "command"

        /** Intentで受け取ったコマンド名を、施錠・解錠のいずれかに限って解釈する（それ以外はnull）。 */
        fun parseCommand(value: String?): SesameCommand? = SesameCommand.entries.find { it.name == value }

        fun runCommandIntent(
            context: Context,
            appWidgetId: Int,
            deviceUuid: String,
            command: SesameCommand,
        ): Intent =
            baseIntent(context, ACTION_RUN_COMMAND, appWidgetId, deviceUuid)
                .putExtra(EXTRA_COMMAND, command.name)

        fun refreshStatusIntent(
            context: Context,
            appWidgetId: Int,
            deviceUuid: String,
        ): Intent = baseIntent(context, ACTION_REFRESH_STATUS, appWidgetId, deviceUuid)

        private fun baseIntent(
            context: Context,
            action: String,
            appWidgetId: Int,
            deviceUuid: String,
        ): Intent =
            Intent(context, WidgetCommandReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_DEVICE_UUID, deviceUuid)
                // PendingIntentがインスタンス・操作の種類をまたいで共有されないよう、dataで区別する。
                .setData(Uri.parse("sesamiwear://widget/$action/$appWidgetId"))

        private fun createRunner(context: Context) =
            WidgetCommandRunner(
                executor = SesameDeviceCommandExecutorFactory.create(context),
                loadRegisteredDevices = { SesameWidgetRepository.loadRegisteredDevices(context) },
                inProgressTracker = WidgetInProgressTracker.shared,
                requestRedraw = { SesameWidgetUpdater.updateAll(context) },
            )
    }
}

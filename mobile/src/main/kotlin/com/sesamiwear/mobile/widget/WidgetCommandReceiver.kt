package com.sesamiwear.mobile.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.EntryPointGuard
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorFactory
import com.sesamiwear.mobile.haptics.SesameHapticPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ウィジェットのタップ（施錠の即時実行・デバイス名の状態取得）と、解錠確認画面での確定を受けて
 * [WidgetCommandRunner]を実行する（BL-122）。`exported="false"`で、自アプリのPendingIntentと
 * [WidgetUnlockConfirmActivity]からのみ呼ばれる。
 *
 * 実行中に例外が漏れるとプロセスごと落ち、ウィジェットが「通信中...」のまま取り残されるため、
 * [EntryPointGuard]で捕捉してログのみに留める（BL-134）。
 *
 * バックグラウンド実行は`goAsync`で行う（GlanceのActionCallbackと同じ仕組み）。解錠確認画面からも同じ経路で
 * 実行するため、Glanceの`actionRunCallback`ではなく自前のBroadcastReceiverにしている。
 * `actionSendBroadcast`は`FLAG_RECEIVER_FOREGROUND`を付けるため実行時間の制限は約10秒で、
 * 超えるとプロセスごとANRで強制終了される（実機で確認、BL-137）。全デバイスの操作は
 * [WidgetCommandRunner]が並行して呼んで所要時間を1台分に近づけ、さらに[WORK_TIMEOUT_MILLIS]で
 * 制限より手前から自分で打ち切る。
 * 施錠/解錠の結果は振動でも伝える（BL-129、判定は[WidgetHapticResolver]）。
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
                // 例外をここで止める（BL-134）。落ちるとウィジェットが「通信中...」のまま取り残される。
                EntryPointGuard.run(onFailure = { Log.w(TAG, "command failed: $it") }) {
                    val finished =
                        withTimeoutOrNull(WORK_TIMEOUT_MILLIS) {
                            val runner = createRunner(appContext)
                            when (action) {
                                ACTION_RUN_COMMAND ->
                                    command?.let { playHaptic(appContext, runner.runCommand(deviceUuid, it)) }
                                // 状態取得は結果を振動で伝えない（wear側のFire-and-forgetと揃える、BL-129）。
                                ACTION_REFRESH_STATUS -> runner.refreshStatus(deviceUuid)
                                else -> Log.w(TAG, "unknown action")
                            }
                            true
                        }
                    if (finished == null) Log.w(TAG, "command timed out before the broadcast deadline")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 施錠/解錠の結果を振動で伝える（BL-129）。画面を見ていなくても成否が分かるようにするもので、
     * 鳴らすかどうかと種類の判定は[WidgetHapticResolver]が持つ。
     * 端末が振動に対応していない・設定で切られている場合は何も起きない（例外にはならない）。
     */
    private fun playHaptic(
        context: Context,
        outcomes: List<SesameDeviceCommandExecutor.Outcome>,
    ) {
        WidgetHapticResolver.resolve(outcomes)?.let { SesameHapticPlayer(context).play(it) }
    }

    companion object {
        private const val TAG = "SesameWidgetCommand"

        /**
         * 受信1回に与える上限（BL-137）。Glanceの`actionSendBroadcast`は`FLAG_RECEIVER_FOREGROUND`を
         * 付けるため、ブロードキャストの実行時間制限は約10秒で、超えるとプロセスごとANRで強制終了される。
         * 強制終了されると`WidgetCommandRunner`の解除と再描画が行われず「通信中...」が固着するため、
         * 制限より手前で自分から打ち切る。打ち切りは取り消し（キャンセル）として伝わり、
         * `WidgetCommandRunner`の`NonCancellable`な再描画が状態を戻す。
         */
        private const val WORK_TIMEOUT_MILLIS = 8_000L
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

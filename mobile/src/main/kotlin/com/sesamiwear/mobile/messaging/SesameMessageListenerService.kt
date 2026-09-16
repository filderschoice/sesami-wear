package com.sesamiwear.mobile.messaging

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.EntryPointGuard
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Wear側からのlock/unlock/状態取得リクエストを受信し、[SesameDeviceCommandExecutor]（BL-120）へ渡す。
 * lock/unlockは実行結果をWear側へ返す。重複として無視された場合（[CommandDebouncer]、BL-062）は
 * 結果を返さない。状態取得リクエスト（[SesameWearProtocol.PATH_STATUS_REQUEST]、BL-061）は
 * 結果をWear側へ返送せず、取得できた状態は実行口の通知でDataItemへ同期される
 * （Tile/Complicationの初回「状態不明」表示を解消するための非同期更新）。
 * 資格情報が未設定の場合、lock/unlockでは[SesameCommandResult.FAILURE]を返し、状態取得では何もしない。
 * Sesame APIの呼び出し・状態保存・DataItem同期のロジックは実行口側にあり、本クラスはメッセージの
 * 変換と結果返送のみを持つ薄いアダプタのためユニットテスト対象外（実際の送受信動作はBL-011で人手検証）。
 */
class SesameMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            // 例外をここで止める（BL-134）。落とすとウォッチからの操作でスマホ側アプリが終了する。
            EntryPointGuard.run(onFailure = { Log.w(TAG, "onMessageReceived failed: $it") }) {
                val executor = SesameDeviceCommandExecutorFactory.create(applicationContext)
                if (messageEvent.path == SesameWearProtocol.PATH_STATUS_REQUEST) {
                    handleStatusRequest(executor, messageEvent)
                } else {
                    handleCommandRequest(executor, messageEvent)
                }
            }
        }
    }

    private suspend fun handleCommandRequest(
        executor: SesameDeviceCommandExecutor,
        messageEvent: MessageEvent,
    ) {
        val deviceUuid = SesameWearProtocol.decodeDeviceUuid(messageEvent.data)
        Log.d(TAG, "handleCommandRequest path=${messageEvent.path} deviceUuidBlank=${deviceUuid.isBlank()}")
        val result =
            when (val command = commandForPath(messageEvent.path)) {
                null -> SesameCommandResult.FAILURE
                else ->
                    when (executor.execute(deviceUuid, command)) {
                        SesameDeviceCommandExecutor.Outcome.SUCCESS -> SesameCommandResult.SUCCESS
                        SesameDeviceCommandExecutor.Outcome.FAILURE -> SesameCommandResult.FAILURE
                        SesameDeviceCommandExecutor.Outcome.DEBOUNCED -> {
                            Log.d(TAG, "handleCommandRequest debounced, skipping")
                            return
                        }
                    }
            }
        Log.d(TAG, "handleCommandRequest result=$result")
        // 結果返送もベストエフォートにし、送信失敗の例外でプロセスを落とさない（BL-118）。
        val sent =
            DataLayerBestEffort.run(onFailure = { Log.w(TAG, "handleCommandRequest send failed: statusCode=$it") }) {
                Wearable.getMessageClient(this@SesameMessageListenerService)
                    .sendMessage(messageEvent.sourceNodeId, SesameWearProtocol.PATH_COMMAND_RESULT, result.toPayload())
                    .await()
            }
        Log.d(TAG, "handleCommandRequest sent result to wear sent=$sent")
    }

    private suspend fun handleStatusRequest(
        executor: SesameDeviceCommandExecutor,
        messageEvent: MessageEvent,
    ) {
        val deviceUuid = SesameWearProtocol.decodeDeviceUuid(messageEvent.data)
        val isLocked = executor.refreshStatus(deviceUuid)
        Log.d(TAG, "handleStatusRequest isInLockRange=$isLocked")
    }

    private fun commandForPath(path: String): SesameCommand? =
        when (path) {
            SesameWearProtocol.PATH_LOCK_REQUEST -> SesameCommand.LOCK
            SesameWearProtocol.PATH_UNLOCK_REQUEST -> SesameCommand.UNLOCK
            else -> null
        }

    private companion object {
        const val TAG = "SesameMessageListener"
    }
}

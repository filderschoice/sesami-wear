package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * ウィジェットのタップから施錠・解錠・状態取得を実行する（BL-122）。Android非依存のためユニットテスト対象。
 *
 * 対象uuidは`SesameDeviceTargets.targetUuids`で展開し（全デバイスなら登録済み全台）、各uuidへ個別に
 * [SesameDeviceCommandExecutor]を呼ぶ。複数台は並行して呼び、全体の所要時間を1台分に近づける
 * （BroadcastReceiverの`goAsync`で実行するため、実行時間を短く保つ）。
 *
 * 実行中は[inProgressTracker]へ登録して再描画し（通信中表示）、終わったら解除して再描画する。
 * ロック状態は実行口が成功時にだけ保存するため、失敗した場合の再描画は操作前の状態に戻る。
 */
class WidgetCommandRunner(
    private val executor: SesameDeviceCommandExecutor,
    private val loadRegisteredDevices: () -> List<SesameDeviceSummary>,
    private val inProgressTracker: WidgetInProgressTracker,
    private val requestRedraw: suspend () -> Unit,
) {
    suspend fun runCommand(
        deviceUuid: String,
        command: SesameCommand,
    ): List<SesameDeviceCommandExecutor.Outcome> = runOnTargets(deviceUuid) { executor.execute(it, command) }

    /** デバイス名のタップ。状態取得（GET）のみを行い、取得できたuuidの状態を返す（失敗はnull）。 */
    suspend fun refreshStatus(deviceUuid: String): List<Boolean?> =
        runOnTargets(deviceUuid) { executor.refreshStatus(it) }

    private suspend fun <T> runOnTargets(
        deviceUuid: String,
        block: suspend (String) -> T,
    ): List<T> {
        val targets = SesameDeviceTargets.targetUuids(deviceUuid, loadRegisteredDevices())
        val tracked = (targets + deviceUuid).distinct()
        inProgressTracker.start(tracked)
        return try {
            requestRedraw()
            coroutineScope { targets.map { uuid -> async { block(uuid) } }.awaitAll() }
        } finally {
            inProgressTracker.finish(tracked)
            // 取り消された場合でも「通信中」のまま残さないよう、解除後の再描画は必ず行う。
            withContext(NonCancellable) { requestRedraw() }
        }
    }
}

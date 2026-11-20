package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * デバイスカードの「施錠」「解錠」ボタンの送信を、画面の寿命（構成変更・画面離脱）から切り離して持つ（BL-295後半の
 * レビュー指摘）。以前はボタンのComposable内の`rememberCoroutineScope`で送っていたため、画面の回転や離脱で
 * 送信のコルーチンが取り消され、結果のスナックバーが出ず、送信中の無効化も外れていた。
 *
 * [scope]は画面より長く生きるもの（アプリのプロセス内のスコープ）を渡す。送信は画面を離れても完了まで続き、
 * 送信中の状態（[sending]）と未表示の結果（[results]）を保持する。画面側は両方を観測して表示し、結果を表示したら
 * [consumeResult]で消す。結果を状態として持つので、回転の途中で完了しても取りこぼさず、画面へ戻ったときに表示される。
 * Android非依存のためユニットテスト対象。
 */
internal class DeviceCardCommandSender(
    private val scope: CoroutineScope,
    private val execute: suspend (uuid: String, command: SesameCommand) -> SesameDeviceCommandExecutor.Outcome,
) {
    private val sendingUuids = MutableStateFlow<Set<String>>(emptySet())
    private val pendingResults = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 送信中のデバイスのuuid。 */
    val sending: StateFlow<Set<String>> = sendingUuids.asStateFlow()

    /** まだ画面に出していない結果のメッセージ（uuidごと）。 */
    val results: StateFlow<Map<String, String>> = pendingResults.asStateFlow()

    /**
     * [uuid]へ[command]を送る。同じデバイスが送信中なら何もせずfalseを返す（二重送信を避ける）。
     * 完了すると、[displayName]入りの結果を[results]へ置いてから送信中を解く。
     */
    fun send(
        uuid: String,
        displayName: String,
        command: SesameCommand,
    ): Boolean {
        if (!tryMarkSending(uuid)) return false
        scope.launch {
            try {
                val outcome = execute(uuid, command)
                val message = DeviceCardCommandPolicy.resultMessage(displayName, command, outcome)
                pendingResults.update { it + (uuid to message) }
            } finally {
                sendingUuids.update { it - uuid }
            }
        }
        return true
    }

    /** 画面に出し終えた[uuid]の結果を消す。 */
    fun consumeResult(uuid: String) {
        pendingResults.update { it - uuid }
    }

    private fun tryMarkSending(uuid: String): Boolean {
        while (true) {
            val current = sendingUuids.value
            if (uuid in current) return false
            if (sendingUuids.compareAndSet(current, current + uuid)) return true
        }
    }
}

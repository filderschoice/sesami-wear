package com.sesamiwear.mobile.credentials

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * デバイスカードの「施錠」「解錠」ボタン（BL-295後半）。表示している状態によらず押せ、すでに施錠中でも
 * 施錠を、解錠中でも解錠を送れる。送信中は両方を無効にして二重送信を避ける。解錠は確認ダイアログを挟む。
 * 送信はウィジェット・ウォッチと同じ実行口（[SesameDeviceCommandExecutorFactory]）で行うため、
 * 経路選択・重複抑止・送信後の状態確認・診断ログ・ウォッチ同期・ウィジェット再描画が同じように効く。
 *
 * 送信と送信中の状態、未表示の結果は[DeviceCardCommandSender]（アプリのプロセス内のスコープ）が持つため、
 * 画面の回転・離脱でも送信は取り消されず完了まで続き、送信中の無効化も保たれる。結果は[onResult]（スナックバー）で
 * 通知し、完了後に[onFinished]でカードの表示を読み直させる。画面を離れている間に完了した結果は、戻ったときに表示する。
 * 確認ダイアログの表示状態は回転後も保つ（`rememberSaveable`）。解錠はダイアログの「解錠」を押したときだけ送るので、
 * 状態を復元しても確認なしで解錠が送られることはない。
 * 規則は[DeviceCardCommandPolicy]（ユニットテスト対象）。Compose部品のためユニットテスト対象外。
 */
@Composable
internal fun DeviceCardCommandButtons(
    uuid: String,
    displayName: String,
    onResult: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val sender = DeviceCardCommandSenders.get(LocalContext.current)
    val sendingUuids by sender.sending.collectAsState()
    val results by sender.results.collectAsState()
    val isSending = uuid in sendingUuids
    var confirming by rememberSaveable(uuid) { mutableStateOf(false) }

    // 完了した結果を一度だけ表示する（回転・離脱の間に完了したものも、状態として残っているので取りこぼさない）。
    // 結果は連番（id）で識別し、同じidは一度しか表示しない。消すときも表示したidと一致するものだけ消す。
    val pending = results[uuid]
    var shownId by rememberSaveable(uuid) { mutableStateOf(-1L) }
    LaunchedEffect(pending?.id) {
        if (pending != null && pending.id != shownId) {
            shownId = pending.id
            sender.consumeResult(uuid, pending.id)
            onResult(pending.message)
            onFinished()
        }
    }

    fun press(command: SesameCommand) {
        when (val action = DeviceCardCommandPolicy.onPressed(command, isSending)) {
            is DeviceCardCommandPolicy.PressAction.Send -> sender.send(uuid, displayName, action.command)
            is DeviceCardCommandPolicy.PressAction.AskConfirm -> confirming = true
            DeviceCardCommandPolicy.PressAction.Ignore -> Unit
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(DeviceCardCommandPolicy.CONFIRM_TITLE) },
            text = { Text(DeviceCardCommandPolicy.confirmMessage(displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        sender.send(uuid, displayName, SesameCommand.UNLOCK)
                    },
                ) { Text(DeviceCardCommandPolicy.UNLOCK_LABEL) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(CANCEL_LABEL) } },
        )
    }
    val enabled = DeviceCardCommandPolicy.isEnabled(isSending)
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { press(SesameCommand.LOCK) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) { Text(DeviceCardCommandPolicy.buttonLabel(SesameCommand.LOCK)) }
        OutlinedButton(
            onClick = { press(SesameCommand.UNLOCK) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) { Text(DeviceCardCommandPolicy.buttonLabel(SesameCommand.UNLOCK)) }
    }
}

/**
 * [DeviceCardCommandSender]をアプリのプロセス内で1つだけ持つ入れ物。送信は画面（Activity・Composable）より長く
 * 生きるスコープで動かし、回転や画面離脱で取り消さない。ApplicationContextだけを保持する。
 */
private object DeviceCardCommandSenders {
    private var instance: DeviceCardCommandSender? = null

    @Synchronized
    fun get(context: Context): DeviceCardCommandSender =
        instance ?: run {
            val appContext = context.applicationContext
            // 送信の外へ漏れた例外でプロセスが落ちないよう、固定文だけを記録して握る（本文・識別子は出さない）。
            val handler = CoroutineExceptionHandler { _, _ -> Log.w(TAG, UNCAUGHT_LOG) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
            DeviceCardCommandSender(scope, log = { Log.w(TAG, it) }) { uuid, command ->
                SesameDeviceCommandExecutorFactory.create(appContext).execute(uuid, command)
            }.also { instance = it }
        }
}

private const val CANCEL_LABEL = "キャンセル"
private const val TAG = "DeviceCardCommand"
private const val UNCAUGHT_LOG = "デバイスカードの送信処理で想定外のエラーが発生しました"

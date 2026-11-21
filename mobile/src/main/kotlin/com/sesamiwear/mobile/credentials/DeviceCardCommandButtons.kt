package com.sesamiwear.mobile.credentials

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sesamiwear.mobile.R
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorFactory
import com.sesamiwear.mobile.widget.CommandChooser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * デバイスカードの「鍵を操作」ボタン（BL-295後半で「施錠」「解錠」の2ボタンとして追加し、BL-303で1つにまとめた）。
 * 押すと［キャンセル｜施錠｜解錠］を選ぶ画面（ウィジェットと同じ[CommandChooser]）を開き、選んだ操作を送る。
 * 表示している状態によらず押せ、すでに施錠中でも施錠を、解錠中でも解錠を送れる。送信中はボタンを無効にして
 * 二重送信を避ける。解錠は選ぶ画面で「解錠」を押したときだけ送る（確認は重ねない。ウィジェットと同じ方針）。
 * 送信はウィジェット・ウォッチと同じ実行口（[SesameDeviceCommandExecutorFactory]）で行うため、
 * 経路選択・重複抑止・送信後の状態確認・診断ログ・ウォッチ同期・ウィジェット再描画が同じように効く。
 *
 * 送信と送信中の状態、未表示の結果は[DeviceCardCommandSender]（アプリのプロセス内のスコープ）が持つため、
 * 画面の回転・離脱でも送信は取り消されず完了まで続き、送信中の無効化も保たれる。結果は[onResult]（スナックバー）で
 * 通知し、完了後に[onFinished]でカードの表示を読み直させる。画面を離れている間に完了した結果は、戻ったときに表示する。
 * 選ぶ画面の表示状態は回転後も保つ（`rememberSaveable`）。状態を復元しても、選ぶ画面で押すまでは何も送らない。
 * 規則は[DeviceCardCommandPolicy]（ユニットテスト対象）。Compose部品のためユニットテスト対象外。
 */
@Composable
internal fun DeviceCardCommandButton(
    uuid: String,
    displayName: String,
    onResult: (String) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sender = DeviceCardCommandSenders.get(LocalContext.current)
    val sendingUuids by sender.sending.collectAsState()
    val results by sender.results.collectAsState()
    val isSending = uuid in sendingUuids
    var choosing by rememberSaveable(uuid) { mutableStateOf(false) }

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

    if (choosing) {
        // 既定のダイアログ幅では3ボタンの「キャンセル」が折り返すため、幅は自前で決める（BL-301と同じ理由）。
        Dialog(
            onDismissRequest = { choosing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION)) {
                CommandChooser(displayName = displayName, isAllDevices = false) { command ->
                    choosing = false
                    val action = DeviceCardCommandPolicy.onChosen(command, isSending)
                    if (action is DeviceCardCommandPolicy.ChooseAction.Send) {
                        sender.send(
                            uuid,
                            displayName,
                            action.command,
                        )
                    }
                }
            }
        }
    }
    FilledTonalButton(
        onClick = {
            if (DeviceCardCommandPolicy.onPressed(isSending) == DeviceCardCommandPolicy.PressAction.OpenChooser) {
                choosing = true
            }
        },
        enabled = DeviceCardCommandPolicy.isEnabled(isSending),
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_shortcut_lock),
            contentDescription = null,
            modifier = Modifier.size(BUTTON_ICON_SIZE_DP.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = DeviceCardCommandPolicy.MENU_LABEL, maxLines = 1)
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

private const val BUTTON_ICON_SIZE_DP = 18

/** 選ぶ画面のダイアログ幅（画面幅に対する割合。ウィジェットの選ぶ画面と同じ。BL-301）。 */
private const val DIALOG_WIDTH_FRACTION = 0.9f
private const val TAG = "DeviceCardCommand"
private const val UNCAUGHT_LOG = "デバイスカードの送信処理で想定外のエラーが発生しました"

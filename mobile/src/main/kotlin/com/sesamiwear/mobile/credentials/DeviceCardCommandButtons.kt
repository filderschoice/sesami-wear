package com.sesamiwear.mobile.credentials

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * デバイスカードの「施錠」「解錠」ボタン（BL-295後半）。表示している状態によらず押せ、すでに施錠中でも
 * 施錠を、解錠中でも解錠を送れる。送信中は両方を無効にして二重送信を避ける。解錠は確認ダイアログを挟む。
 * 送信はウィジェット・ウォッチと同じ実行口（[SesameDeviceCommandExecutorFactory]）で行うため、
 * 経路選択・重複抑止・送信後の状態確認・診断ログ・ウォッチ同期・ウィジェット再描画が同じように効く。
 * 結果は[onResult]（スナックバー）で通知し、完了後に[onFinished]でカードの表示を読み直させる。
 * 規則は[DeviceCardCommandPolicy]（ユニットテスト対象）。Compose部品のためユニットテスト対象外。
 */
@Composable
internal fun DeviceCardCommandButtons(
    uuid: String,
    displayName: String,
    onResult: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    fun send(command: SesameCommand) {
        isSending = true
        scope.launch {
            val outcome =
                try {
                    withContext(Dispatchers.IO) {
                        SesameDeviceCommandExecutorFactory.create(context).execute(uuid, command)
                    }
                } finally {
                    isSending = false
                }
            onResult(DeviceCardCommandPolicy.resultMessage(displayName, command, outcome))
            onFinished()
        }
    }

    fun press(command: SesameCommand) {
        when (val action = DeviceCardCommandPolicy.onPressed(command, isSending)) {
            is DeviceCardCommandPolicy.PressAction.Send -> send(action.command)
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
                        send(SesameCommand.UNLOCK)
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

private const val CANCEL_LABEL = "キャンセル"

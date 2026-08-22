package com.sesamiwear.wear.action

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.api.SesameCommandConfirmation
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider

/**
 * Tileタップから起動される施錠/解錠実行画面。
 * 施錠（LOCK）はワンタップ即実行、解錠（UNLOCK）は確認ボタンを挟む（PLAN.mdのUX要件）。
 * コマンド送信はFire-and-forgetで行い、実行結果（成功/失敗）のリアルタイム反映とハプティクスはBL-008で扱う。
 * 操作対象デバイスのuuid（BL-053、tileIdに割り当てられたデバイス）をIntent extra経由で受け取る。
 */
class SesameActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command =
            SesameActionCommandParser.fromExtra(
                intent.getStringExtra(SesameActionCommandParser.EXTRA_COMMAND),
            )
        val deviceUuid = intent.getStringExtra(SesameActionCommandParser.EXTRA_DEVICE_UUID)
        if (command == null || deviceUuid.isNullOrEmpty()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                SesameActionScreen(command = command, deviceUuid = deviceUuid, onFinished = ::finish)
            }
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            command: SesameCommand,
            deviceUuid: String,
        ): Intent =
            Intent(context, SesameActionActivity::class.java).apply {
                putExtra(SesameActionCommandParser.EXTRA_COMMAND, command.name)
                putExtra(SesameActionCommandParser.EXTRA_DEVICE_UUID, deviceUuid)
            }
    }
}

@Composable
private fun SesameActionScreen(
    command: SesameCommand,
    deviceUuid: String,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var awaitingConfirmation by remember { mutableStateOf(SesameCommandConfirmation.isRequired(command)) }
    var sending by remember { mutableStateOf(!awaitingConfirmation) }

    if (sending) {
        LaunchedEffect(command) {
            val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(context)
            if (nodeId != null) {
                val sender = SesameCommandSenderProvider.create(context)
                when (command) {
                    SesameCommand.LOCK -> sender.requestLock(nodeId, deviceUuid)
                    SesameCommand.UNLOCK -> sender.requestUnlock(nodeId, deviceUuid)
                }
            }
            onFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (awaitingConfirmation) {
            Button(onClick = {
                awaitingConfirmation = false
                sending = true
            }) {
                Text(text = "タップして解錠")
            }
        } else {
            Text(text = "送信中...")
        }
    }
}

package com.sesamiwear.wear.action

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.api.SesameCommandConfirmation
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.tile.SesameTileContent

/**
 * Tileタップから起動される施錠/解錠実行画面。
 * 施錠（LOCK）はワンタップ即実行、解錠（UNLOCK）は確認ボタンを挟む（PLAN.mdのUX要件）。
 * コマンド送信はFire-and-forgetで行い、実行結果（成功/失敗）のリアルタイム反映とハプティクスはBL-008で扱う。
 * 操作対象デバイスのuuid（BL-053、tileIdに割り当てられたデバイス）をIntent extra経由で受け取る。
 * uuidが[SesameWearProtocol.ALL_DEVICES_TARGET_UUID]（「全デバイス」選択）の場合は登録済み
 * 全デバイスへ同一コマンドを送信する（BL-071、複数デバイス一括操作）。
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
                val targetUuids = SesameActionTargetResolver.resolveDeviceUuids(context, deviceUuid)
                targetUuids.forEach { targetUuid ->
                    when (command) {
                        SesameCommand.LOCK -> sender.requestLock(nodeId, targetUuid)
                        SesameCommand.UNLOCK -> sender.requestUnlock(nodeId, targetUuid)
                    }
                }
            }
            onFinished()
        }
    }

    if (awaitingConfirmation) {
        SesameConfirmationButtons(
            command = command,
            isAllDevices = deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
            onCancel = onFinished,
            onConfirm = {
                awaitingConfirmation = false
                sending = true
            },
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "送信中...")
        }
    }
}

/**
 * 解錠確認画面のボタン群（BL-070）。以前は円形の[androidx.wear.compose.material.Button]に
 * 「タップして解錠」という長いテキストを詰め込んでいたため文字が見切れていた。左＝キャンセル、
 * 右＝施錠/解錠、の角丸チップ2つへ再設計し、Tile側（[com.sesamiwear.wear.tile.SesameTileService]）
 * と共通の角丸デザイン・状態色（[SesameTileContent]）を用いて一貫したUXにする。
 */
@Composable
private fun SesameConfirmationButtons(
    command: SesameCommand,
    isAllDevices: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val resultingState = if (command == SesameCommand.LOCK) TileDisplayState.LOCKED else TileDisplayState.UNLOCKED
    val actionLabel =
        (if (command == SesameCommand.LOCK) "施錠" else "解錠").let { if (isAllDevices) "全$it" else it }

    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SesameActionChip(
            label = "キャンセル",
            backgroundColor = Color(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB),
            textColor = Color.White,
            onClick = onCancel,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SesameActionChip(
            label = actionLabel,
            backgroundColor = Color(SesameTileContent.backgroundColorArgb(resultingState)),
            textColor = Color(SesameTileContent.statusTextColorArgb(resultingState)),
            onClick = onConfirm,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SesameActionChip(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(ACTION_CHIP_CORNER_RADIUS_DP.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = textColor, textAlign = TextAlign.Center)
    }
}

private const val ACTION_CHIP_CORNER_RADIUS_DP = 12

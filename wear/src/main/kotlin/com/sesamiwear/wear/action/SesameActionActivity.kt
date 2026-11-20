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
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.api.SesameCommandConfirmation
import com.sesamiwear.core.display.SesameTileActions
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.core.haptics.HapticPattern
import com.sesamiwear.wear.demo.DemoLockStateStore
import com.sesamiwear.wear.display.SesameDisplayUpdateRequester
import com.sesamiwear.wear.haptics.SesameHapticPlayer
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider

/**
 * Tileタップから起動される施錠/解錠実行画面。
 * 施錠（LOCK）の直接指定はワンタップ即実行する。施錠・解錠を選ぶ依頼（[SesameActionCommandParser.EXTRA_CHOOSE]、
 * 施錠中・一部解錠のTileタップとデバイス一覧の行タップ、BL-295）と解錠（UNLOCK）の直接指定は、
 * 施錠・解錠の2ボタンの選ぶ画面を開き、押した方をそのまま実行する（確認は重ねない。キャンセルボタンは無く、
 * スワイプで戻る＝キャンセル）。解錠はこの画面で「解錠」を押す明示操作を経て初めて実行される
 * （PLAN.mdのUX要件、[SesameCommandConfirmation]）。
 * コマンド送信はFire-and-forgetで行い、実行結果（成功/失敗）のリアルタイム反映とハプティクスはBL-008で扱う。
 * 操作対象デバイスのuuid（BL-053、tileIdに割り当てられたデバイス）をIntent extra経由で受け取る。
 * uuidが[SesameWearProtocol.ALL_DEVICES_TARGET_UUID]（「全デバイス」選択）の場合は登録済み
 * 全デバイスへ同一コマンドを送信する（BL-071、複数デバイス一括操作）。
 * uuidが[SesameDemoMode.DEMO_DEVICE_UUID]（デモモード、BL-109）の場合はスマホへメッセージを
 * 送らず、wear単体のダミー状態を書き換えるだけで完結する。
 */
class SesameActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val plan =
            SesameActionLaunchPlanner.plan(
                commandName = intent.getStringExtra(SesameActionCommandParser.EXTRA_COMMAND),
                choose = intent.getBooleanExtra(SesameActionCommandParser.EXTRA_CHOOSE, false),
                deviceUuid = intent.getStringExtra(SesameActionCommandParser.EXTRA_DEVICE_UUID),
            )
        val deviceUuid = intent.getStringExtra(SesameActionCommandParser.EXTRA_DEVICE_UUID)
        if (plan == SesameActionLaunchPlanner.Plan.Finish || deviceUuid.isNullOrEmpty()) {
            finish()
            return
        }
        val directCommand = (plan as? SesameActionLaunchPlanner.Plan.Execute)?.command

        setContent {
            MaterialTheme {
                SesameActionScreen(directCommand = directCommand, deviceUuid = deviceUuid, onFinished = ::finish)
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

        /** 施錠・解錠を選ぶ画面を開く（BL-295）。 */
        fun createChooserIntent(
            context: Context,
            deviceUuid: String,
        ): Intent =
            Intent(context, SesameActionActivity::class.java).apply {
                putExtra(SesameActionCommandParser.EXTRA_CHOOSE, true)
                putExtra(SesameActionCommandParser.EXTRA_DEVICE_UUID, deviceUuid)
            }
    }
}

@Composable
private fun SesameActionScreen(
    directCommand: SesameCommand?,
    deviceUuid: String,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    // directCommandが無いとき（選ぶ画面）は、ボタンで選ばれたコマンドをここへ入れる。
    var command by remember { mutableStateOf(directCommand) }

    command?.let { toSend ->
        LaunchedEffect(toSend) {
            if (SesameDemoMode.isDemoDevice(deviceUuid)) {
                applyDemoCommand(context, toSend)
            } else {
                sendCommand(context, toSend, deviceUuid)
            }
            onFinished()
        }
    }

    if (command == null) {
        SesameChooserButtons(
            isAllDevices = deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
            onChoose = { command = it },
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "送信中...")
        }
    }
}

/**
 * 実デバイスへのコマンド送信（Fire-and-forget）。結果は
 * [com.sesamiwear.wear.messaging.SesameResultListenerService]がmobile側から受け取る。
 */
private suspend fun sendCommand(
    context: Context,
    command: SesameCommand,
    deviceUuid: String,
) {
    val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(context) ?: return
    val sender = SesameCommandSenderProvider.create(context)
    SesameActionTargetResolver.resolveDeviceUuids(context, deviceUuid).forEach { targetUuid ->
        when (command) {
            SesameCommand.LOCK -> sender.requestLock(nodeId, targetUuid)
            SesameCommand.UNLOCK -> sender.requestUnlock(nodeId, targetUuid)
        }
    }
}

/**
 * デモモード（BL-109）のダミー施錠状態を書き換える。実デバイスが存在しないためスマホへは
 * 一切メッセージを送らず、wear単体で状態更新・ハプティクス・再描画まで完結させる。
 * 結果ハプティクスは実デバイス操作時（[com.sesamiwear.wear.messaging.SesameResultHandler]）と
 * 同じ成功パターンを鳴らし、体験を揃える。
 */
private fun applyDemoCommand(
    context: Context,
    command: SesameCommand,
) {
    DemoLockStateStore(context).setLocked(SesameDemoMode.nextIsLocked(command))
    SesameHapticPlayer(context).play(HapticPattern.SUCCESS)
    SesameDisplayUpdateRequester.requestUpdateAll(context)
}

/**
 * 施錠・解錠を選ぶ画面のボタン群（BL-070、BL-295で選ぶ画面へ変更）。左＝施錠、右＝解錠の角丸チップ2つで、
 * キャンセルボタンは置かない（スワイプで戻る＝何も送らずに閉じる）。Tile側
 * （[com.sesamiwear.wear.tile.SesameTileService]）と共通の角丸デザイン・状態色（[SesameTileContent]）を用い、
 * 各ボタンは押した結果の状態色にする。対象が全デバイスなら「全施錠」「全解錠」。
 */
@Composable
private fun SesameChooserButtons(
    isAllDevices: Boolean,
    onChoose: (SesameCommand) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(SesameCommand.LOCK, SesameCommand.UNLOCK).forEach { choice ->
            val resultingState =
                if (choice == SesameCommand.LOCK) TileDisplayState.LOCKED else TileDisplayState.UNLOCKED
            SesameActionChip(
                label = SesameTileActions.commandLabel(choice, isAllDevices),
                backgroundColor = Color(SesameTileContent.backgroundColorArgb(resultingState)),
                textColor = Color(SesameTileContent.statusTextColorArgb(resultingState)),
                onClick = { onChoose(choice) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
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

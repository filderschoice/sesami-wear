package com.sesamiwear.mobile.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.mobile.ui.SesameTheme

/**
 * ウィジェットから解錠するときの確認画面（BL-122）。wearの`SesameActionActivity`の確認画面と同じく、
 * 左＝「キャンセル」（中立色）・右＝「解錠」（全デバイスは「全解錠」、解錠中の状態色）の2ボタンを並べる。
 * 解錠を押すと[WidgetCommandReceiver]へ実行を依頼してすぐ閉じ（実行と通信中表示はウィジェット側）、
 * キャンセル・画面外タップ・戻る操作では何も送らずに閉じる。
 * ダイアログテーマの軽量Activityで、`noHistory`・`excludeFromRecents`を持つ（wearの確認画面と同方針）。
 * 画面の組み立てだけのAndroid依存クラスのためユニットテスト対象外（確定時のコマンドは[WidgetTapAction]でテスト済み）。
 */
class WidgetUnlockConfirmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceUuid = intent.getStringExtra(EXTRA_DEVICE_UUID)
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        val appWidgetId = intent.getIntExtra(EXTRA_APP_WIDGET_ID, 0)
        if (deviceUuid.isNullOrEmpty()) {
            finish()
            return
        }
        setContent {
            SesameTheme {
                Surface(shape = RoundedCornerShape(16.dp)) {
                    UnlockConfirmation(
                        displayName = displayName,
                        isAllDevices = SesameDeviceTargets.isAllDevices(deviceUuid),
                        onResult = { confirmed ->
                            WidgetTapAction.commandAfterConfirmation(SesameCommand.UNLOCK, confirmed)?.let { command ->
                                sendBroadcast(
                                    WidgetCommandReceiver.runCommandIntent(this, appWidgetId, deviceUuid, command),
                                )
                            }
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_DEVICE_UUID = "device_uuid"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_APP_WIDGET_ID = "app_widget_id"

        fun createIntent(
            context: Context,
            appWidgetId: Int,
            deviceUuid: String,
            displayName: String,
        ): Intent =
            Intent(context, WidgetUnlockConfirmActivity::class.java)
                .putExtra(EXTRA_DEVICE_UUID, deviceUuid)
                .putExtra(EXTRA_DISPLAY_NAME, displayName)
                .putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
                .setData(Uri.parse("sesamiwear://widget/confirm-unlock/$appWidgetId"))
    }
}

@Composable
private fun UnlockConfirmation(
    displayName: String,
    isAllDevices: Boolean,
    onResult: (confirmed: Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "${displayName}を解錠しますか？", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfirmChip(
                label = "キャンセル",
                backgroundArgb = SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB,
                textArgb = NEUTRAL_TEXT_ARGB,
                onClick = { onResult(false) },
                modifier = Modifier.weight(1f),
            )
            ConfirmChip(
                label = if (isAllDevices) "全解錠" else "解錠",
                backgroundArgb = SesameTileContent.backgroundColorArgb(TileDisplayState.UNLOCKED),
                textArgb = SesameTileContent.statusTextColorArgb(TileDisplayState.UNLOCKED),
                onClick = { onResult(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConfirmChip(
    label: String,
    backgroundArgb: Int,
    textArgb: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .height(CHIP_HEIGHT_DP.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(CHIP_CORNER_RADIUS_DP.dp))
                .background(Color(backgroundArgb))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color(textArgb), style = MaterialTheme.typography.titleMedium)
    }
}

// NEUTRAL_TEXT_ARGB / CHIP_CORNER_RADIUS_DP はウィジェット本体と同じ値を使う
// （SesameWidgetChips.kt。同じ面の部品のため、片方だけ変わると見た目がずれる）。
private const val CHIP_HEIGHT_DP = 56

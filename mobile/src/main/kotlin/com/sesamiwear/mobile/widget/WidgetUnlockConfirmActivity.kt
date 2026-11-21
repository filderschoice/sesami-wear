package com.sesamiwear.mobile.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
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
import com.sesamiwear.core.display.SesameTileActions
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.mobile.ui.SesameTheme

/**
 * ウィジェット・クイック設定タイルから施錠・解錠を選ぶ画面（BL-122の解錠確認画面をBL-295で選ぶ画面へ変更。
 * クラス名はManifestがあるため維持）。左から［キャンセル｜施錠｜解錠］（中立色・施錠中の状態色・解錠中の状態色。
 * 全デバイスは「全施錠」「全解錠」）の3ボタンを並べる。表示と同じ操作（施錠中に施錠など）も選べる。
 * 施錠・解錠を押すとそのまま[WidgetCommandReceiver]へ実行を依頼してすぐ閉じ（確認は重ねない。実行と通信中表示は
 * ウィジェット側）、解錠は「解錠」を押す明示操作を経て実行される。
 * キャンセル・画面外タップ・戻る操作では何も送らずに閉じる（スマホはキャンセルボタンを残す。ウォッチの
 * 選ぶ画面はキャンセルボタンを持たずスワイプで戻る）。
 * ダイアログテーマの軽量Activityで、`noHistory`・`excludeFromRecents`を持つ（wearの確認画面と同方針）。
 * 画面の組み立てだけのAndroid依存クラスのためユニットテスト対象外（このActivityを開くかどうかは[WidgetTapAction]でテスト済み）。
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
                    CommandChooser(
                        displayName = displayName,
                        isAllDevices = SesameDeviceTargets.isAllDevices(deviceUuid),
                        onResult = { command ->
                            command?.let {
                                sendBroadcast(
                                    WidgetCommandReceiver.runCommandIntent(this, appWidgetId, deviceUuid, it),
                                )
                            }
                            finish()
                        },
                    )
                }
            }
        }
        // ダイアログテーマの既定幅（Pixel 8 Proで約287dp）では3ボタンを等分すると「キャンセル」が
        // 2行に折り返すため、画面幅の一定割合まで広げる（BL-301）。高さは内容に合わせる。
        window.setLayout(
            (resources.displayMetrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
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
                .setData(Uri.parse("sesamiwear://widget/choose-command/$appWidgetId"))
    }
}

/**
 * 施錠・解錠を選ぶ画面の中身（見出しと［キャンセル｜施錠｜解錠］）。ウィジェットの選ぶ画面と、アプリ主画面の
 * デバイスカードの「鍵を操作」（BL-303）が同じ見た目を使う。[onResult]はキャンセルならnull。
 */
@Composable
internal fun CommandChooser(
    displayName: String,
    isAllDevices: Boolean,
    onResult: (command: SesameCommand?) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "${displayName}の操作を選んでください", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfirmChip(
                label = "キャンセル",
                backgroundArgb = SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB,
                textArgb = NEUTRAL_TEXT_ARGB,
                onClick = { onResult(null) },
                modifier = Modifier.weight(1f),
            )
            ConfirmChip(
                label = SesameTileActions.commandLabel(SesameCommand.LOCK, isAllDevices),
                backgroundArgb = SesameTileContent.backgroundColorArgb(TileDisplayState.LOCKED),
                textArgb = SesameTileContent.statusTextColorArgb(TileDisplayState.LOCKED),
                onClick = { onResult(SesameCommand.LOCK) },
                modifier = Modifier.weight(1f),
            )
            ConfirmChip(
                label = SesameTileActions.commandLabel(SesameCommand.UNLOCK, isAllDevices),
                backgroundArgb = SesameTileContent.backgroundColorArgb(TileDisplayState.UNLOCKED),
                textArgb = SesameTileContent.statusTextColorArgb(TileDisplayState.UNLOCKED),
                onClick = { onResult(SesameCommand.UNLOCK) },
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

// 選ぶ画面のダイアログ幅（画面幅に対する割合。BL-301）
private const val DIALOG_WIDTH_FRACTION = 0.9f

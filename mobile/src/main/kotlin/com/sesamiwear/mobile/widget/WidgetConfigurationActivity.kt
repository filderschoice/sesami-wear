package com.sesamiwear.mobile.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.mobile.EntryPointGuard
import kotlinx.coroutines.launch

/**
 * ホーム画面ウィジェットの対象デバイスを選ぶ画面（BL-121）。ウィジェットの追加時に
 * `appwidget-provider`の`android:configure`から開くほか、ウィジェットの「変更」・「タップして設定」からも開く。
 * wearのTileには追加時に設定画面を開く標準機構が無いが、選べる内容はTileと同じ
 * （[SesameDeviceTargets.choices]。登録済みデバイス、2台以上なら先頭に「全デバイス」、0台ならデモ用デバイス）。
 *
 * 選択するとappWidgetIdごとに割り当てを保存し、そのウィジェットの再描画を要求してから`RESULT_OK`で閉じる。
 * 選ばずに戻った場合は`RESULT_CANCELED`のままになり、追加時であればホーム画面はウィジェットを配置しない。
 * 画面の組み立てだけのAndroid依存クラスのためユニットテスト対象外（選択肢はcoreでテスト済み）。
 */
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId =
            intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val registeredDevices = SesameWidgetRepository.loadRegisteredDevices(this)
        val choices = SesameDeviceTargets.choices(registeredDevices)
        val isDemoMode = SesameDemoMode.isAvailable(registeredDevices)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WidgetDeviceSelectionScreen(
                        choices = choices,
                        isDemoMode = isDemoMode,
                        onSelected = { uuid -> onDeviceSelected(appWidgetId, uuid) },
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    private fun onDeviceSelected(
        appWidgetId: Int,
        uuid: String,
    ) {
        SesameWidgetRepository.assignmentStore(this).assign(appWidgetId, uuid)
        lifecycleScope.launch {
            // 再描画の失敗で割り当ての確定（RESULT_OK）まで落とさない（BL-134）。
            EntryPointGuard.run(onFailure = { Log.w(TAG, "widget redraw failed: $it") }) {
                SesameWidgetUpdater.update(applicationContext, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private companion object {
        const val TAG = "SesameWidgetConfig"
    }
}

@Composable
private fun WidgetDeviceSelectionScreen(
    choices: List<SesameDeviceTargets.Choice>,
    isDemoMode: Boolean,
    onSelected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = SELECTION_TITLE, style = MaterialTheme.typography.titleMedium)
        if (isDemoMode) {
            Text(text = DEMO_HINT_TITLE, style = MaterialTheme.typography.labelLarge)
            Text(text = DEMO_HINT_BODY, style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(choices, key = { it.uuid }) { choice ->
                Button(onClick = { onSelected(choice.uuid) }, modifier = Modifier.fillMaxWidth()) {
                    Text(choice.label)
                }
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("キャンセル")
        }
    }
}

private const val SELECTION_TITLE = "ウィジェットで操作するSesame"

// wearのデバイス選択画面（DeviceSelectionContent）と同じ見出し・趣旨。スマホは画面幅に余裕があるため改行は画面に委ねる。
private const val DEMO_HINT_TITLE = "デモモード"
private const val DEMO_HINT_BODY = "スマホでSesameを登録すると、実際の鍵を操作できます。"

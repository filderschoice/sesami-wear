package com.sesamiwear.mobile.credentials

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.mobile.alert.SesameAlertSettingsStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * 「お知らせ」の設定（BL-238）。上部バーの設定メニューから開く。
 *
 * 通知には`POST_NOTIFICATIONS`（Android 13以上）が要るため、オンにする操作の中で権限も求める
 * （経路の変化の通知と同じ扱い、`RouteNotificationState`）。
 * Compose画面のためユニットテスト対象外（保存は`SesameAlertSettingsStoreTest`で検証済み）。
 */
internal class AlertSettingsState(
    val isLowBatteryEnabled: Boolean,
    /** 端末側の通知が有効で、実際に届く状態か。 */
    val isDeliverable: Boolean,
    val setLowBatteryEnabled: (Boolean) -> Unit,
) {
    /** 設定メニューの副題に出す1行。 */
    val shortStatus: String
        get() =
            buildString {
                append("電池残量：").append(if (isLowBatteryEnabled) "オン" else "オフ")
                if (!isDeliverable) append("（端末の設定で通知が許可されていません）")
            }
}

@Composable
internal fun rememberAlertSettingsState(): AlertSettingsState {
    val context = LocalContext.current
    val settings = remember { SesameAlertSettingsStore(SharedPreferencesKeyValueStore.forAlerts(context)) }
    var lowBattery by remember { mutableStateOf(settings.isLowBatteryEnabled()) }
    var deliverable by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        deliverable = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            deliverable = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun requestIfNeeded() {
        if (!deliverable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return AlertSettingsState(
        isLowBatteryEnabled = lowBattery,
        isDeliverable = deliverable,
        setLowBatteryEnabled = { next ->
            settings.setLowBatteryEnabled(next)
            lowBattery = next
            if (next) requestIfNeeded()
        },
    )
}

/** 「お知らせ」の設定ダイアログ。項目ごとに、何を知らせるのかを説明文で示す。 */
@Composable
internal fun AlertSettingsDialog(
    state: AlertSettingsState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = ALERT_SETTINGS_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SwitchRow(
                    title = LOW_BATTERY_TITLE,
                    description = LOW_BATTERY_DESCRIPTION,
                    checked = state.isLowBatteryEnabled,
                    onCheckedChange = state.setLowBatteryEnabled,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = "閉じる") } },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 設定メニューの項目名。ダイアログの見出しと揃える。 */
internal const val ALERT_SETTINGS_TITLE = "お知らせ"

private const val LOW_BATTERY_TITLE = "電池残量が少なくなったとき"
private const val LOW_BATTERY_DESCRIPTION =
    "状態の取得や操作で分かった電池残量が20%を下回ったときに1回だけ通知します。" +
        "このための通信は行いません。"

package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sesamiwear.mobile.alert.SesameAlertSettingsStore
import com.sesamiwear.mobile.settings.SettingsPageScaffold

/**
 * 「通知」の設定画面。もとは「経路が変わったときの通知」と「お知らせ」が別々のメニュー項目・
 * ダイアログで、どちらも「何を知らせるか」を選ぶだけなのに2回開く必要があった。1つにまとめ、
 * 経路・電池残量・解錠したままの3種類を並べて切り替える（2026-11-08）。説明文が長く、複数の設定を
 * 含むためダイアログではなく画面で開く。通知が端末側で許可されていないときは、許可の案内を上に1か所だけ出す。
 */
@Composable
internal fun NotificationSettingsScreen(
    route: RouteNotificationState,
    alerts: AlertSettingsState,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(title = NOTIFICATION_TITLE, onBack = onBack) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!alerts.isDeliverable) PermissionNotice(onAllow = route.requestPermission)
            SwitchRow(
                title = ROUTE_NOTIFICATION_TITLE,
                description = ROUTE_NOTIFICATION_DESCRIPTION,
                checked = route.isEnabled,
                onCheckedChange = route.setEnabled,
            )
            HorizontalDivider()
            SwitchRow(
                title = LOW_BATTERY_TITLE,
                description = LOW_BATTERY_DESCRIPTION,
                checked = alerts.isLowBatteryEnabled,
                onCheckedChange = alerts.setLowBatteryEnabled,
            )
            HorizontalDivider()
            ReminderChoices(selected = alerts.unlockReminderMinutes, onSelect = alerts.setUnlockReminderMinutes)
        }
    }
}

@Composable
private fun PermissionNotice(onAllow: () -> Unit) {
    Column {
        Text(
            text = NOT_ALLOWED_MESSAGE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onAllow) { Text(text = ALLOW_LABEL) }
    }
}

/** メニュー項目の副題。3種類のうちいくつがオンかを1行で示す。 */
internal fun notificationSummary(
    route: RouteNotificationState,
    alerts: AlertSettingsState,
): String {
    if (!alerts.isDeliverable) return NOT_ALLOWED_SHORT
    val on =
        listOf(
            route.isEnabled,
            alerts.isLowBatteryEnabled,
            alerts.unlockReminderMinutes != SesameAlertSettingsStore.OFF,
        ).count {
            it
        }
    return "3項目中${on}項目がオン"
}

private const val NOTIFICATION_TITLE = "通知"
private const val NOT_ALLOWED_SHORT = "端末の通知が許可されていません"
private const val NOT_ALLOWED_MESSAGE =
    "端末の設定で通知が許可されていないため、オンにしても届きません。"
private const val ALLOW_LABEL = "通知を許可する"

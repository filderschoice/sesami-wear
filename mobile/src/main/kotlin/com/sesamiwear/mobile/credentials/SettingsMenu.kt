package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.mobile.diagnostics.DiagnosticsLogDialog
import com.sesamiwear.mobile.showcase.ShowcaseModeUi
import com.sesamiwear.mobile.widget.WidgetPinRequester

/**
 * 上部バーの設定メニュー（BL-180）。歯車アイコンで開き、項目は「接続／通知／ホーム画面／サポート」の見出しで区切る。
 *
 * もとはヘルプが見出し横のボタン、経路の方針とBluetooth権限が画面本体の2行に常時置かれており、
 * 主画面の情報量を押し上げていた。3つとも「たまに開く設定」なので、⋮のメニューへまとめ、
 * 実際の操作はそれぞれのダイアログで行う（2026-09-20、ユーザー指示）。
 * 同じ理由で、診断ログ（BL-188）と、経路が変わったときの通知の設定（BL-190）、「お知らせ」の設定
 * （電池残量・解錠したまま、BL-238 / BL-241）もこのメニューから開く。
 * ホーム画面ウィジェットの配置（BL-262）もここから要求する（2026-10-17、ユーザー判断で置き場所を設定メニューに決定）。
 *
 * **現在の状態はメニュー項目の副題に出す。** 設定を開かなくても、メニューを開いた時点で
 * 経路の方針とBluetoothの許可状況が分かるようにするため。
 *
 * Bluetoothの項目は、許可済みで求めるものが無いとき（`buttonLabel`がnull）は押せない。
 * 状態表示としてだけ残す。
 */
@Composable
internal fun SettingsMenu() {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dialogs = remember { SettingsDialogState() }
    val routePolicy = rememberRoutePolicyState()
    val blePermission = rememberBlePermissionState()
    val routeNotification = rememberRouteNotificationState()
    val alertSettings = rememberAlertSettingsState()

    SettingsDialogs(
        dialogs = dialogs,
        routePolicy = routePolicy,
        blePermission = blePermission,
        routeNotification = routeNotification,
        alertSettings = alertSettings,
    )

    IconButton(onClick = { expanded = true }) {
        Icon(imageVector = Icons.Default.Settings, contentDescription = MENU_DESCRIPTION)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        fun open(show: () -> Unit) {
            expanded = false
            show()
        }
        MenuSection(title = SECTION_CONNECTION, isFirst = true)
        SettingsMenuItem(
            title = ROUTE_POLICY_TITLE,
            subtitle = SesameRouteLabel.policyLabel(routePolicy.policy),
            onClick = { open { dialogs.routePicker = true } },
        )
        SettingsMenuItem(
            title = BLE_PERMISSION_MENU_TITLE,
            subtitle = blePermission.shortStatus,
            enabled = blePermission.buttonLabel != null,
            onClick = { open { dialogs.bleRationale = true } },
        )
        MenuSection(title = SECTION_NOTIFICATION)
        SettingsMenuItem(
            title = ROUTE_NOTIFICATION_MENU_TITLE,
            subtitle = routeNotification.shortStatus,
            onClick = { open { dialogs.routeNotification = true } },
        )
        SettingsMenuItem(
            title = ALERT_SETTINGS_TITLE,
            subtitle = alertSettings.shortStatus,
            onClick = { open { dialogs.alertSettings = true } },
        )
        MenuSection(title = SECTION_HOME_SCREEN)
        AddWidgetMenuItem(onDone = { expanded = false })
        SupportMenuItems(dialogs = dialogs, open = ::open)
    }
}

/** 「サポート」区分の項目（ヘルプ・操作履歴・撮影モード）。 */
@Composable
private fun SupportMenuItems(
    dialogs: SettingsDialogState,
    open: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    MenuSection(title = SECTION_SUPPORT)
    SettingsMenuItem(
        title = HELP_MENU_TITLE,
        subtitle = null,
        onClick = { open { dialogs.help = true } },
    )
    SettingsMenuItem(
        title = DIAGNOSTICS_MENU_TITLE,
        subtitle = DIAGNOSTICS_MENU_SUBTITLE,
        onClick = { open { dialogs.diagnostics = true } },
    )
    // 撮影モード（BL-213）。デバッグ版だけが項目を返し、リリース版では現れない。
    ShowcaseModeUi.menuEntryOrNull(context)?.let { entry ->
        SettingsMenuItem(entry.title, entry.subtitle) { open { context.startActivity(entry.intent) } }
    }
}

/** メニューから開く6つのダイアログ。どれを出しているかは[SettingsDialogState]が持つ。 */
@Composable
private fun SettingsDialogs(
    dialogs: SettingsDialogState,
    routePolicy: RoutePolicyState,
    blePermission: BlePermissionState,
    routeNotification: RouteNotificationState,
    alertSettings: AlertSettingsState,
) {
    if (dialogs.routePicker) {
        RoutePolicyPickerDialog(
            selected = routePolicy.policy,
            onSelect = {
                routePolicy.select(it)
                dialogs.routePicker = false
            },
            onDismiss = { dialogs.routePicker = false },
        )
    }
    if (dialogs.bleRationale) {
        BlePermissionRationaleDialog(
            permission = blePermission,
            onConfirm = {
                dialogs.bleRationale = false
                blePermission.confirm()
            },
            onDismiss = { dialogs.bleRationale = false },
        )
    }
    if (dialogs.routeNotification) {
        RouteNotificationDialog(state = routeNotification, onDismiss = { dialogs.routeNotification = false })
    }
    if (dialogs.alertSettings) {
        AlertSettingsDialog(state = alertSettings, onDismiss = { dialogs.alertSettings = false })
    }
    if (dialogs.help) HelpDialog(onDismiss = { dialogs.help = false })
    if (dialogs.diagnostics) DiagnosticsLogDialog(onDismiss = { dialogs.diagnostics = false })
}

/** メニュー1項目。副題を持てるようにするため、`text`スロットへ2行を置く。 */
@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        text = {
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

/** メニューの区切り見出し。長いメニューを「接続／通知／ホーム画面／サポート」に分けて探しやすくする。 */
@Composable
private fun MenuSection(
    title: String,
    isFirst: Boolean = false,
) {
    if (!isFirst) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * 「ホーム画面にウィジェットを追加」（BL-262）。押すとランチャーの確認画面が開く（[WidgetPinRequester]）。
 * ランチャーが配置の要求に対応しない場合は押せない項目にし、副題で長押しからの追加を案内する。
 */
@Composable
private fun AddWidgetMenuItem(onDone: () -> Unit) {
    val context = LocalContext.current
    val isSupported = remember { WidgetPinRequester.isSupported(context) }
    SettingsMenuItem(
        title = ADD_WIDGET_MENU_TITLE,
        subtitle = if (isSupported) ADD_WIDGET_MENU_SUBTITLE else ADD_WIDGET_UNSUPPORTED_SUBTITLE,
        enabled = isSupported,
        onClick = {
            onDone()
            WidgetPinRequester.request(context)
        },
    )
}

/** どのダイアログを出しているか。メニュー本体の行数を抑えるためにまとめている。 */
private class SettingsDialogState {
    var routePicker by mutableStateOf(false)
    var bleRationale by mutableStateOf(false)
    var routeNotification by mutableStateOf(false)
    var alertSettings by mutableStateOf(false)
    var help by mutableStateOf(false)
    var diagnostics by mutableStateOf(false)
}

private const val MENU_DESCRIPTION = "設定"
private const val HELP_MENU_TITLE = "ヘルプ"

private const val SECTION_CONNECTION = "接続"
private const val SECTION_NOTIFICATION = "通知"
private const val SECTION_HOME_SCREEN = "ホーム画面"
private const val SECTION_SUPPORT = "サポート"

// 区切り見出しが文脈を担うため、項目名は短くする。副題は1行に収める。
private const val ROUTE_NOTIFICATION_MENU_TITLE = "経路が変わったとき"
private const val ADD_WIDGET_MENU_TITLE = "ウィジェットを追加"
private const val ADD_WIDGET_MENU_SUBTITLE = "アプリを開かずに施錠・解錠"
private const val ADD_WIDGET_UNSUPPORTED_SUBTITLE = "ホーム画面の長押しから追加"

// 利用者が「操作の履歴」を探したときに見つけられるよう、履歴であることを先に出す（BL-237）。
private const val DIAGNOSTICS_MENU_TITLE = "操作履歴（診断ログ）"
private const val DIAGNOSTICS_MENU_SUBTITLE = "記録を見る・送る"

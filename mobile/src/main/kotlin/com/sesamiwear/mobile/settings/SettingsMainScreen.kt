package com.sesamiwear.mobile.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import com.sesamiwear.mobile.credentials.BLE_PERMISSION_MENU_TITLE
import com.sesamiwear.mobile.credentials.BlePermissionRationaleDialog
import com.sesamiwear.mobile.credentials.ROUTE_POLICY_TITLE
import com.sesamiwear.mobile.credentials.RoutePolicyPickerDialog
import com.sesamiwear.mobile.credentials.notificationSummary
import com.sesamiwear.mobile.credentials.rememberAlertSettingsState
import com.sesamiwear.mobile.credentials.rememberBlePermissionState
import com.sesamiwear.mobile.credentials.rememberRouteNotificationState
import com.sesamiwear.mobile.credentials.rememberRoutePolicyState
import com.sesamiwear.mobile.showcase.ShowcaseModeUi
import com.sesamiwear.mobile.widget.WidgetPinRequester

/**
 * 設定の一覧。項目は「接続／通知／ホーム画面／サポート」の4区分。
 * 行の右に「›」があるものは別のページ（画面）が開き、無いものはダイアログか、その場の操作になる。
 */
@Composable
internal fun SettingsMainScreen(
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    val notificationSummary = notificationSummary(rememberRouteNotificationState(), rememberAlertSettingsState())
    SettingsPageScaffold(title = SETTINGS_TITLE, onBack = onBack) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            ConnectionSection()
            SectionHeader(SECTION_NOTIFICATION)
            SettingsRow(NOTIFICATION_TITLE, notificationSummary, opensPage = true) { onOpen(SettingsPage.NOTIFICATION) }
            SectionHeader(SECTION_HOME_SCREEN)
            AddWidgetRow()
            SectionHeader(SECTION_SUPPORT)
            SettingsRow(HELP_TITLE, HELP_SUBTITLE, opensPage = true) { onOpen(SettingsPage.HELP) }
            SettingsRow(DIAGNOSTICS_TITLE, DIAGNOSTICS_SUBTITLE, opensPage = true) {
                onOpen(SettingsPage.DIAGNOSTICS)
            }
            ShowcaseRow()
        }
    }
}

/** 「接続」区分。選ぶだけ・確認だけで完結するため、どちらもダイアログで開く。 */
@Composable
private fun ConnectionSection() {
    val routePolicy = rememberRoutePolicyState()
    val blePermission = rememberBlePermissionState()
    var routePicker by remember { mutableStateOf(false) }
    var bleRationale by remember { mutableStateOf(false) }
    if (routePicker) {
        RoutePolicyPickerDialog(
            selected = routePolicy.policy,
            onSelect = {
                routePolicy.select(it)
                routePicker = false
            },
            onDismiss = { routePicker = false },
        )
    }
    if (bleRationale) {
        BlePermissionRationaleDialog(
            permission = blePermission,
            onConfirm = {
                bleRationale = false
                blePermission.confirm()
            },
            onDismiss = { bleRationale = false },
        )
    }
    SectionHeader(SECTION_CONNECTION, isFirst = true)
    SettingsRow(ROUTE_POLICY_TITLE, SesameRouteLabel.policyLabel(routePolicy.policy)) { routePicker = true }
    // 許可済みで求めるものが無いとき（buttonLabelがnull）は、押せない状態表示にする。
    SettingsRow(
        title = BLE_PERMISSION_MENU_TITLE,
        subtitle = blePermission.shortStatus,
        enabled = blePermission.buttonLabel != null,
    ) { bleRationale = true }
}

/** ランチャーが配置の要求に対応しない場合は押せない行にし、副題で長押しからの追加を案内する（BL-262）。 */
@Composable
private fun AddWidgetRow() {
    val context = LocalContext.current
    val isSupported = remember { WidgetPinRequester.isSupported(context) }
    SettingsRow(
        title = ADD_WIDGET_TITLE,
        subtitle = if (isSupported) ADD_WIDGET_SUBTITLE else ADD_WIDGET_UNSUPPORTED_SUBTITLE,
        enabled = isSupported,
    ) { WidgetPinRequester.request(context) }
}

/** 撮影モード（BL-213）。デバッグ版だけが項目を返し、リリース版では現れない。 */
@Composable
private fun ShowcaseRow() {
    val context = LocalContext.current
    ShowcaseModeUi.menuEntryOrNull(context)?.let { entry ->
        SettingsRow(entry.title, entry.subtitle) { context.startActivity(entry.intent) }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    isFirst: Boolean = false,
) {
    if (!isFirst) HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    opensPage: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(text = title) },
        supportingContent =
            subtitle?.let {
                { Text(text = it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            },
        trailingContent =
            if (opensPage) {
                { Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
            } else {
                null
            },
    )
}

private const val SETTINGS_TITLE = "設定"
private const val SECTION_CONNECTION = "接続"
private const val SECTION_NOTIFICATION = "通知"
private const val SECTION_HOME_SCREEN = "ホーム画面"
private const val SECTION_SUPPORT = "サポート"
private const val NOTIFICATION_TITLE = "通知"
private const val ADD_WIDGET_TITLE = "ウィジェットを追加"
private const val ADD_WIDGET_SUBTITLE = "アプリを開かずに施錠・解錠できます"
private const val ADD_WIDGET_UNSUPPORTED_SUBTITLE = "ホーム画面の長押しから追加してください"
private const val HELP_TITLE = "ヘルプ"
private const val HELP_SUBTITLE = "値の取得方法・デモで試す方法・困ったとき"
private const val DIAGNOSTICS_TITLE = "操作履歴（診断ログ）"
private const val DIAGNOSTICS_SUBTITLE = "記録を見る・送る"

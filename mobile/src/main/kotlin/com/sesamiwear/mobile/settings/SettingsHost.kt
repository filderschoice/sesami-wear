package com.sesamiwear.mobile.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sesamiwear.mobile.credentials.HelpListScreen
import com.sesamiwear.mobile.credentials.HelpTopicScreen
import com.sesamiwear.mobile.credentials.NotificationSettingsScreen
import com.sesamiwear.mobile.credentials.rememberAlertSettingsState
import com.sesamiwear.mobile.credentials.rememberRouteNotificationState
import com.sesamiwear.mobile.diagnostics.DiagnosticsLogScreen
import com.sesamiwear.mobile.help.HelpTopic

/**
 * 設定画面のページ切り替え。一覧から各ページを開き、戻る操作で一覧（ヘルプの本文なら項目一覧）へ戻る。
 * 一覧を経ずに開いた場合（[start]が一覧以外）は、戻る操作で画面を閉じる。
 */
@Composable
internal fun SettingsHost(
    start: SettingsPage,
    onFinish: () -> Unit,
) {
    var page by remember { mutableStateOf(start) }
    var helpTopic by remember { mutableStateOf<HelpTopic?>(null) }
    val routeNotification = rememberRouteNotificationState()
    val alertSettings = rememberAlertSettingsState()

    fun back() {
        when {
            helpTopic != null -> helpTopic = null
            page == SettingsPage.MAIN || page == start -> onFinish()
            else -> page = SettingsPage.MAIN
        }
    }
    BackHandler(onBack = ::back)

    val topic = helpTopic
    when {
        page == SettingsPage.MAIN -> SettingsMainScreen(onBack = onFinish, onOpen = { page = it })
        page == SettingsPage.NOTIFICATION ->
            NotificationSettingsScreen(route = routeNotification, alerts = alertSettings, onBack = ::back)
        page == SettingsPage.DIAGNOSTICS -> DiagnosticsLogScreen(onBack = ::back)
        topic == null -> HelpListScreen(onTopic = { helpTopic = it }, onBack = ::back)
        else -> HelpTopicScreen(topic = topic, onBack = ::back)
    }
}

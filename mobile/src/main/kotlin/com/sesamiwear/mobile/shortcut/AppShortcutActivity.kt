package com.sesamiwear.mobile.shortcut

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Toast
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.widget.SesameWidgetRepository
import com.sesamiwear.mobile.widget.WidgetCommandReceiver

/**
 * アプリアイコン長押しメニュー（App Shortcuts）の受け口（BL-239）。画面を持たず、すぐに閉じる。
 *
 * 実行そのものはホーム画面ウィジェットと同じ[WidgetCommandReceiver]へ「全デバイス」を対象に渡す。
 * 時間のかかる通信をActivityの寿命に縛らず`goAsync`で実行でき、重複の抑止・振動・ウィジェットと
 * ウォッチへの反映もウィジェットからの操作と同じになるため。
 *
 * `exported="false"`で宣言する。ショートカットはランチャーが本アプリとして起動するため
 * 非公開のActivityでも起動でき、他のアプリから施錠を実行される入口を作らない。
 * Intentの解釈と配線だけの薄いアダプタのためユニットテスト対象外（解釈は[AppShortcutAction]でテスト済み）。
 */
class AppShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = AppShortcutAction.fromIntentAction(intent?.action)
        if (action != null && savedInstanceState == null) run(action)
        finish()
    }

    private fun run(action: AppShortcutAction) {
        val deviceCount = SesameWidgetRepository.loadRegisteredDevices(this).size
        Toast.makeText(applicationContext, action.acceptedMessage(deviceCount), Toast.LENGTH_SHORT).show()
        if (deviceCount == 0) return
        val target = SesameWearProtocol.ALL_DEVICES_TARGET_UUID
        val noWidget = AppWidgetManager.INVALID_APPWIDGET_ID
        val intent =
            when (action) {
                AppShortcutAction.LOCK_ALL ->
                    WidgetCommandReceiver.runCommandIntent(this, noWidget, target, SesameCommand.LOCK)
                AppShortcutAction.REFRESH_ALL -> WidgetCommandReceiver.refreshStatusIntent(this, noWidget, target)
            }
        sendBroadcast(intent)
    }
}

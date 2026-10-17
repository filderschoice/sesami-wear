package com.sesamiwear.mobile.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sesamiwear.mobile.R

/**
 * アプリアイコン長押しメニュー（App Shortcuts）へ「全施錠」「状態を更新」を登録する（BL-239）。
 *
 * 静的ショートカット（`res/xml/shortcuts.xml`）ではなく動的ショートカットにしている。静的ショートカットの
 * Intentはパッケージ名を固定で書く必要があり、デバッグ版（applicationIdに`.debug`が付く、BL-131）で
 * 起動先が見つからなくなるため。動的ショートカットは実行時のContextからIntentを組み立てられる。
 * アプリを開くたびに登録し直す（同じIDなら上書きされ、増えない）。利用者は最初にアプリでSesameを
 * 登録するため、ショートカットを使う時点では必ず一度アプリを開いている。
 *
 * 配線だけの薄いアダプタのためユニットテスト対象外（操作の解釈は[AppShortcutAction]でテスト済み）。
 */
object AppShortcutPublisher {
    fun publish(context: Context) {
        ShortcutManagerCompat.setDynamicShortcuts(context, AppShortcutAction.entries.map { shortcut(context, it) })
    }

    private fun shortcut(
        context: Context,
        action: AppShortcutAction,
    ): ShortcutInfoCompat =
        ShortcutInfoCompat
            .Builder(context, action.shortcutId)
            .setShortLabel(action.shortLabel)
            .setLongLabel(action.longLabel)
            .setIcon(IconCompat.createWithResource(context, iconOf(action)))
            .setIntent(Intent(context, AppShortcutActivity::class.java).setAction(action.intentAction))
            .build()

    private fun iconOf(action: AppShortcutAction): Int =
        when (action) {
            AppShortcutAction.LOCK_ALL -> R.drawable.ic_shortcut_lock
            AppShortcutAction.REFRESH_ALL -> R.drawable.ic_shortcut_refresh
        }
}

package com.sesamiwear.mobile.showcase

import android.content.Context
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 撮影モード（BL-213）の画面部品。**デバッグ版にだけ含まれる**（リリース版は`src/release`の同名スタブ）。
 *
 * - 設定メニューの項目: 撮影モードの操作画面（[ShowcaseModeActivity]）を開く。副題にオン/オフを出す。
 * - 表示: 撮影モード中だけ、画面のタイトルの横へ小さく「📷 撮影モード」を出す。撮影の邪魔にならないよう
 *   帯にはしない（ユーザーの選択）。ウォッチとウィジェットには出さない。
 */
object ShowcaseModeUi {
    private const val MENU_TITLE = "撮影モード"
    private const val INDICATOR_TEXT = "📷 撮影モード"

    fun menuEntryOrNull(context: Context): ShowcaseMenuEntry? =
        ShowcaseMenuEntry(
            title = MENU_TITLE,
            subtitle = if (ShowcaseMode.isActive(context)) "オン（実機は操作しません）" else "オフ",
            intent = Intent(context, ShowcaseModeActivity::class.java),
        )

    @Composable
    fun Indicator() {
        if (!ShowcaseMode.isActive(LocalContext.current)) return
        Text(
            text = INDICATOR_TEXT,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

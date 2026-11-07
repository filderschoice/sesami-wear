package com.sesamiwear.mobile.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.sesamiwear.mobile.ui.SesameTheme

/**
 * 設定画面。主画面の歯車から開く。
 *
 * もとは歯車（⋮）のドロップダウンから、どの項目も同じ「ダイアログ」で開いていた。選ぶだけ・確かめるだけの
 * ものと、読み物や複数の設定を含むものが同じ見た目で混ざり、後者は小さなダイアログに収まらなかった。
 * 使い分けは次のとおり（2026-11-08のユーザー指示）。
 * - **ダイアログ**: 択一や確認で完結するもの（操作の経路、Bluetoothの許可）。
 * - **画面（本Activity内のページ）**: 読み物・複数の設定・一覧を含むもの（通知、ヘルプ、操作履歴）。
 *   戻る操作で設定の一覧へ戻る。
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = SettingsPage.fromName(intent.getStringExtra(EXTRA_PAGE))
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(isDarkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
            }
            SesameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsHost(start = start, onFinish = ::finish)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PAGE = "page"

        /** [page]から開く。主画面の歯車は一覧、空状態のヘルプ導線はヘルプから開く。 */
        fun createIntent(
            context: Context,
            page: SettingsPage = SettingsPage.MAIN,
        ): Intent = Intent(context, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page.name)
    }
}

/** 設定画面のページ。[MAIN]が一覧で、ほかは一覧から開く画面。 */
enum class SettingsPage {
    MAIN,
    NOTIFICATION,
    HELP,
    DIAGNOSTICS,
    ;

    companion object {
        fun fromName(name: String?): SettingsPage = entries.firstOrNull { it.name == name } ?: MAIN
    }
}

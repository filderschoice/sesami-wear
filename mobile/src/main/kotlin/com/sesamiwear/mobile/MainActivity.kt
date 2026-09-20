package com.sesamiwear.mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.credentials.CredentialsSettingsScreen
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import com.sesamiwear.mobile.ui.SesameTheme
import com.sesamiwear.mobile.widget.SesameWidgetUpdater
import kotlinx.coroutines.launch

// wearが独立したapplicationモジュールになり、mobileがウォッチへインストールされることは
// なくなったため、PackageManager.FEATURE_WATCHを判定してwear.MainActivityへexplicit Intentで
// 委譲する処理を削除した（BL-092）。ウォッチ側のランチャー導線はwear側のMainActivityが持つ。
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentialsStore =
            SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(applicationContext))
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(isDarkTheme) { applySystemBarIcons(isDarkTheme) }
            SesameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CredentialsSettingsScreen(credentialsStore = credentialsStore)
                }
            }
        }
    }

    /**
     * ホーム画面ウィジェットの再描画を要求する（BL-135）。
     *
     * ウィジェットは定期更新を行わない（`updatePeriodMillis=0`）ため、実行中にプロセスが落ちるなどで
     * 解除の再描画が行われないと、操作もできない「通信中...」の表示がホーム画面に残り続ける。
     * 利用者が最初にとる行動はアプリを開くことなので、表示のたびに再描画を要求して確実に復帰させる。
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            EntryPointGuard.run(onFailure = { Log.w(TAG, "widget redraw failed: $it") }) {
                SesameWidgetUpdater.updateAll(applicationContext)
            }
        }
    }

    /**
     * ステータスバー・ナビゲーションバーのアイコン色をテーマへ追随させる（BL-103 / BL-181）。
     *
     * targetSdk 35以降はエッジツーエッジ表示が必須で、アプリの背景がシステムバーの領域まで
     * 広がる。明るい背景のときにアイコンが白のままだと、時刻・電池残量などがほとんど判読できない。
     *
     * ライトテーマでは暗色アイコン、ダークテーマでは明色アイコンにする。ダークテーマ対応（BL-181）
     * まではライト固定だったため、常に暗色を指定していた。
     */
    private fun applySystemBarIcons(isDarkTheme: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
    }

    private companion object {
        const val TAG = "SesameMainActivity"
    }
}

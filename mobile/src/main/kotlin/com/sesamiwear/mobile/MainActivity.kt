package com.sesamiwear.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.credentials.CredentialsSettingsScreen
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore

// wearが独立したapplicationモジュールになり、mobileがウォッチへインストールされることは
// なくなったため、PackageManager.FEATURE_WATCHを判定してwear.MainActivityへexplicit Intentで
// 委譲する処理を削除した（BL-092）。ウォッチ側のランチャー導線はwear側のMainActivityが持つ。
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLightSystemBarIcons()
        val credentialsStore =
            SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(applicationContext))
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CredentialsSettingsScreen(credentialsStore = credentialsStore)
                }
            }
        }
    }

    /**
     * ステータスバー・ナビゲーションバーのアイコンを暗色にする（BL-103）。
     *
     * targetSdk 35以降はエッジツーエッジ表示が必須で、アプリの背景がシステムバーの領域まで
     * 広がる。本画面は[MaterialTheme]の既定（ライトカラースキーム）で明るい背景を用いるため、
     * システムバーのアイコンが白のままだと時刻・電池残量などがほとんど判読できなかった。
     *
     * ダークテーマへは対応していない（[MaterialTheme]へcolorSchemeを渡していないため、
     * 端末の設定にかかわらず常にライト）ので、条件分岐せず常に暗色アイコンを指定する。
     * ダークテーマへ対応する場合は、この指定もテーマに追随させる必要がある。
     */
    private fun applyLightSystemBarIcons() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }
}

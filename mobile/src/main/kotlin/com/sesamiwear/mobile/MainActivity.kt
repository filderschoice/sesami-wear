package com.sesamiwear.mobile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.credentials.CredentialsSettingsScreen
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore

private const val WEAR_MAIN_ACTIVITY_CLASS_NAME = "com.sesamiwear.wear.MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            // mobileはbaseモジュールのためウォッチ側にも常時インストールされ、ランチャーアイコンは
            // 本Activityのみが持つ（wear.MainActivityはBL-066でLAUNCHER属性を除去しアイコン
            // 重複を解消した）。ウォッチでタップされた場合はスマホ向け資格情報設定画面を表示せず、
            // ウォッチ向け画面（wear.MainActivity）へexplicit Intent（クラス名文字列、
            // mobileはwearへコンパイル時依存できないため）で委譲する。
            try {
                startActivity(
                    Intent().setClassName(packageName, WEAR_MAIN_ACTIVITY_CLASS_NAME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (
                @Suppress("SwallowedException") e: ActivityNotFoundException,
            ) {
                // wearフィーチャーモジュールが未インストールの場合のフォールバック（通常発生しない想定）。
            }
            finish()
            return
        }
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
}

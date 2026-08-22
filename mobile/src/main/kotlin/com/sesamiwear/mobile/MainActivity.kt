package com.sesamiwear.mobile

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            // mobileはbaseモジュールのためウォッチ側にも常時インストールされる
            // （wear側はBL-039のdist:conditionsでwatch限定配信済み）。
            // スマホ向け資格情報設定画面をウォッチで表示させないためのガード。
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

package com.sesamiwear.mobile

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

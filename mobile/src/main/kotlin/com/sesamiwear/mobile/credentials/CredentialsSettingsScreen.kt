package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import kotlinx.coroutines.delay

/**
 * Sesame APIの認証情報3点（uuid/apikey/secretKey）を入力・保存する設定画面。
 * secretKeyはSesameアプリの「鍵をシェア」QRコードから取得したBase64文字列をそのまま入力する想定
 * （QRコードスキャン自体は本タスクの範囲外）。
 */
@Composable
fun CredentialsSettingsScreen(
    credentialsStore: SesameCredentialsStore,
    onSaved: () -> Unit = {},
) {
    val initial = remember { credentialsStore.load() }
    var uuid by remember { mutableStateOf(initial?.uuid.orEmpty()) }
    var apiKey by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var secretKeyBase64 by remember { mutableStateOf(initial?.secretKeyBase64.orEmpty()) }
    var showSavedMessage by remember { mutableStateOf(false) }
    val isInputValid = CredentialsInputValidator.isValid(uuid, apiKey, secretKeyBase64)

    if (showSavedMessage) {
        LaunchedEffect(Unit) {
            delay(SAVED_MESSAGE_DURATION_MS)
            showSavedMessage = false
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Sesame API設定")
        OutlinedTextField(
            value = uuid,
            onValueChange = { uuid = it },
            label = { Text("uuid") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("apikey") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = secretKeyBase64,
            onValueChange = { secretKeyBase64 = it },
            label = { Text("secretKey (Base64)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = isInputValid,
            onClick = {
                credentialsStore.save(
                    SesameCredentials(uuid = uuid, apiKey = apiKey, secretKeyBase64 = secretKeyBase64),
                )
                showSavedMessage = true
                onSaved()
            },
        ) {
            Text("保存")
        }
        if (showSavedMessage) {
            Text(text = "保存しました")
        }
    }
}

private const val SAVED_MESSAGE_DURATION_MS = 2000L

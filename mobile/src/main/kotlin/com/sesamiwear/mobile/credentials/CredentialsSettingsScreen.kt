package com.sesamiwear.mobile.credentials

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.messaging.SesameDeviceListSyncer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 複数台のSesameデバイスの資格情報（uuid/apikey/secretKey/表示名）を一覧・追加・編集・削除する画面（BL-049）。
 * uuidをデバイスの一意キーとして扱い、既存uuidでの保存は上書き、新規uuidでの保存は追加になる。
 * uuid/apikey/secretKeyはすべてbiz.candyhouse.co（SESAME Biz 開発者ページ）から取得する想定
 * （BL-059、Sesameアプリの「鍵をシェア」QRコードは使わない）。secretKeyは16進数32文字（BL-058）。
 * 取得元の詳細説明は初期表示せず、ヘルプボタンからのダイアログへ集約して情報量を抑える（BL-059）。
 */
@Composable
fun CredentialsSettingsScreen(
    credentialsStore: SesameCredentialsStore,
    onSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var credentialsList by remember { mutableStateOf(credentialsStore.loadAll()) }
    val formState = rememberCredentialsFormState()
    var showSavedMessage by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    if (showSavedMessage) {
        LaunchedEffect(Unit) {
            delay(SAVED_MESSAGE_DURATION_MS)
            showSavedMessage = false
        }
    }

    // wear側は資格情報を持たない設計方針のため、Tile Configuration Activityでの
    // デバイス選択肢表示用にuuid/displayNameのみの一覧をDataClient経由で同期する（BL-052）。
    fun syncDeviceList(list: List<SesameCredentials>) {
        coroutineScope.launch { SesameDeviceListSyncer(context).sync(list) }
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Sesame API設定（${credentialsList.size}台登録済み）",
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showHelp = true }) {
                Text("ヘルプ")
            }
        }
        DeviceList(
            credentialsList = credentialsList,
            onEdit = formState::startEditing,
            onDelete = { credentials ->
                credentialsStore.remove(credentials.uuid)
                credentialsList = credentialsStore.loadAll()
                syncDeviceList(credentialsList)
                if (formState.editingUuid == credentials.uuid) formState.startEditing(null)
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        CredentialsForm(
            formState = formState,
            onSave = {
                val updatedList =
                    credentialsList.filterNot { it.uuid == formState.uuid } + formState.toCredentials()
                credentialsStore.saveAll(updatedList)
                credentialsList = updatedList
                syncDeviceList(updatedList)
                showSavedMessage = true
                formState.startEditing(null)
                onSaved()
            },
        )
        if (showSavedMessage) {
            Text(text = "保存しました")
        }
    }
}

/**
 * uuid/apikey/secretKeyの取得元をまとめたヘルプ（BL-059）。
 * 初期表示では出さず、ヘルプボタンからのみ開く。SESAME Biz開発者ページへのリンクを含む。
 */
@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("値の取得方法") },
        text = {
            Column {
                Text(
                    "uuid・apikey・secretKeyは biz.candyhouse.co（SESAME Biz 開発者ページ）で" +
                        "確認できます。\nsecretKeyは16進数32文字です。",
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SESAME_BIZ_DEVELOPER_URL)))
                }) {
                    Text("SESAME Biz 開発者ページを開く")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

private const val SESAME_BIZ_DEVELOPER_URL = "https://biz.candyhouse.co/biz/developer"

@Composable
private fun DeviceList(
    credentialsList: List<SesameCredentials>,
    onEdit: (SesameCredentials) -> Unit,
    onDelete: (SesameCredentials) -> Unit,
) {
    if (credentialsList.isEmpty()) {
        Text(text = "まだSesameが登録されていません。下のフォームから追加してください。")
        return
    }
    LazyColumn {
        items(credentialsList, key = { it.uuid }) { credentials ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = credentials.displayName.ifBlank { credentials.uuid },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onEdit(credentials) }) {
                    Text("編集")
                }
                TextButton(onClick = { onDelete(credentials) }) {
                    Text("削除")
                }
            }
        }
    }
}

@Composable
private fun CredentialsForm(
    formState: CredentialsFormState,
    onSave: () -> Unit,
) {
    val isInputValid = CredentialsInputValidator.isValid(formState.uuid, formState.apiKey, formState.secretKeyHex)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = if (formState.editingUuid == null) "新しいSesameを追加" else "Sesameを編集")
        OutlinedTextField(
            value = formState.displayName,
            onValueChange = { formState.displayName = it },
            label = { Text("表示名（任意）") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = formState.uuid,
            onValueChange = { formState.uuid = it },
            label = { Text("uuid") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = formState.apiKey,
            onValueChange = { formState.apiKey = it },
            label = { Text("apikey") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = formState.secretKeyHex,
            onValueChange = { formState.secretKeyHex = it },
            label = { Text("secretKey (16進数32文字)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = isInputValid,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (formState.editingUuid == null) "追加" else "更新")
        }
        if (formState.editingUuid != null) {
            TextButton(
                onClick = { formState.startEditing(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("キャンセル")
            }
        }
    }
}

private class CredentialsFormState {
    var editingUuid by mutableStateOf<String?>(null)
    var uuid by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var secretKeyHex by mutableStateOf("")
    var displayName by mutableStateOf("")

    fun startEditing(credentials: SesameCredentials?) {
        editingUuid = credentials?.uuid
        uuid = credentials?.uuid.orEmpty()
        apiKey = credentials?.apiKey.orEmpty()
        secretKeyHex = credentials?.secretKeyHex.orEmpty()
        displayName = credentials?.displayName.orEmpty()
    }

    fun toCredentials(): SesameCredentials =
        SesameCredentials(uuid = uuid, apiKey = apiKey, secretKeyHex = secretKeyHex, displayName = displayName)
}

@Composable
private fun rememberCredentialsFormState(): CredentialsFormState = remember { CredentialsFormState() }

private const val SAVED_MESSAGE_DURATION_MS = 2000L

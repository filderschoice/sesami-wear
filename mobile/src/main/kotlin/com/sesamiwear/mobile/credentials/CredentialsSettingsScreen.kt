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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.help.HelpContent
import com.sesamiwear.mobile.help.HelpTopic
import com.sesamiwear.mobile.messaging.SesameDeviceListSyncer
import com.sesamiwear.mobile.widget.SesameWidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 複数台のSesameデバイスの資格情報（uuid/apikey/secretKey/表示名）を一覧・追加・編集・削除する画面（BL-049）。
 * uuidをデバイスの一意キーとして扱い、既存uuidでの保存は上書き、新規uuidでの保存は追加になる。
 * 保存時のリスト更新は[CredentialsListEditor]が担う。編集は一覧内の同じ位置を保ったまま置き換え、
 * uuidを変更した場合も元の項目が重複して残らない（BL-100）。
 * uuid/apikey/secretKeyはすべてbiz.candyhouse.co（SESAME Biz 開発者ページ）から取得する想定
 * （BL-059、Sesameアプリの「鍵をシェア」QRコードは使わない）。secretKeyは16進数32文字（BL-058）。
 * 取得元の詳細説明は初期表示せず、ヘルプボタンからのダイアログへ集約して情報量を抑える（BL-059）。
 * ヘルプは値の取得方法・デモの試し方・登録後の使い方を選べるメニュー形式で、文言は
 * [com.sesamiwear.mobile.help.HelpContent]が持つ（BL-113）。
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
    // ホーム画面ウィジェットは表示名・対象デバイスの有無が変わるため、あわせて再描画を要求する（BL-121）。
    fun syncDeviceList(list: List<SesameCredentials>) {
        coroutineScope.launch {
            SesameDeviceListSyncer(context).sync(list)
            SesameWidgetUpdater.updateAll(context)
        }
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
                    CredentialsListEditor.upsert(
                        credentialsList = credentialsList,
                        editingUuid = formState.editingUuid,
                        edited = formState.toCredentials(),
                    )
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
 * ヘルプボタンから開くダイアログ（BL-059/BL-113）。初期表示では出さず、ヘルプボタンからのみ開く。
 * 項目を選ぶ[HelpMenuDialog]と、選んだ項目の本文を出す[HelpTopicDialog]の2段構成にしている。
 * 値の取得方法だけの単一ダイアログでは、資格情報が未登録でもウォッチ側でデモ（BL-109）を
 * 操作できることに気づく導線が無かったため（BL-113）。
 */
@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    var selectedTopic by remember { mutableStateOf<HelpTopic?>(null) }
    val topic = selectedTopic
    if (topic == null) {
        HelpMenuDialog(onTopicSelected = { selectedTopic = it }, onDismiss = onDismiss)
    } else {
        HelpTopicDialog(topic = topic, onBack = { selectedTopic = null }, onDismiss = onDismiss)
    }
}

/** ヘルプの項目一覧（BL-113）。文言と並び順は[HelpContent]が持つ。 */
@Composable
private fun HelpMenuDialog(
    onTopicSelected: (HelpTopic) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(HelpContent.MENU_TITLE) },
        text = {
            Column {
                HelpContent.topics.forEach { topic ->
                    TextButton(
                        onClick = { onTopicSelected(topic) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = topic.title, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

/**
 * ヘルプ1項目の本文（BL-113）。本文が画面の高さを超える項目があるためスクロール可能にし、
 * 外部ページへのリンクを持つ項目（値の取得方法）ではブラウザを開くボタンを添える。
 */
@Composable
private fun HelpTopicDialog(
    topic: HelpTopic,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(topic.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topic.paragraphs.forEach { paragraph -> Text(paragraph) }
                topic.link?.let { link ->
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    }) {
                        Text(link.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("戻る") }
        },
    )
}

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
        // uuid/apikey/secretKeyはいずれもASCII文字のみで構成される。日本語IMEで全角文字が
        // 入力されると見た目では半角と区別できないまま保存され、署名検証がAPI側で失敗する
        // 原因になるため、ASCIIキーボードを既定にしたうえで入力値を都度正規化する（BL-112）。
        OutlinedTextField(
            value = formState.uuid,
            onValueChange = { formState.uuid = CredentialsInputSanitizer.sanitizeUuid(it) },
            label = { Text("uuid") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = formState.apiKey,
            onValueChange = { formState.apiKey = CredentialsInputSanitizer.sanitizeApiKey(it) },
            label = { Text("apikey") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = formState.secretKeyHex,
            onValueChange = { formState.secretKeyHex = CredentialsInputSanitizer.sanitizeSecretKeyHex(it) },
            label = { Text("secretKey (16進数32文字)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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

package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sesamiwear.core.SesameCredentials

/**
 * Sesameの追加・編集を行う全画面ダイアログ（BL-178）。
 *
 * もとは一覧の下に入力フォームが常時置かれていたが、画面が縦に長くなり、
 * 一覧とフォームのどちらを見ているのか分かりにくかった。追加ボタン（FAB）とカードの編集ボタンから
 * 開く全画面ダイアログへ移し、主画面を一覧だけにする。
 *
 * 入力値の正規化（[CredentialsInputSanitizer]）・検証（[CredentialsInputValidator]）は従来のまま。
 * uuid/apikey/secretKeyはいずれもASCII文字のみで構成される。日本語IMEで全角文字が入力されると
 * 見た目では半角と区別できないまま保存され、署名検証がAPI側で失敗する原因になるため、
 * ASCIIキーボードを既定にしたうえで入力値を都度正規化する（BL-112）。
 *
 * Compose画面のためユニットテスト対象外（検証ロジックは`CredentialsInputValidatorTest` /
 * `CredentialsInputSanitizerTest`、保存時のリスト更新は`CredentialsListEditorTest`で検証済み）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CredentialsEditorDialog(
    /** 編集対象。null なら新規追加。 */
    editing: SesameCredentials?,
    onDismiss: () -> Unit,
    onSave: (SesameCredentials) -> Unit,
) {
    val formState = remember(editing) { CredentialsFormState(editing) }
    val isInputValid = CredentialsInputValidator.isValid(formState.uuid, formState.apiKey, formState.secretKeyHex)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(if (editing == null) ADD_TITLE else EDIT_TITLE) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = CANCEL_LABEL)
                        }
                    },
                    actions = {
                        TextButton(enabled = isInputValid, onClick = { onSave(formState.toCredentials()) }) {
                            Text(SAVE_LABEL)
                        }
                    },
                )
            },
        ) { innerPadding ->
            CredentialsFormFields(
                formState = formState,
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            )
        }
    }
}

@Composable
private fun CredentialsFormFields(
    formState: CredentialsFormState,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = FORM_HINT, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = formState.displayName,
            onValueChange = { formState.displayName = it },
            label = { Text("表示名（任意）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
    }
}

/** 入力中の値。ダイアログを開くたびに対象へ合わせて作り直す。 */
internal class CredentialsFormState(editing: SesameCredentials?) {
    val editingUuid: String? = editing?.uuid
    var uuid by mutableStateOf(editing?.uuid.orEmpty())
    var apiKey by mutableStateOf(editing?.apiKey.orEmpty())
    var secretKeyHex by mutableStateOf(editing?.secretKeyHex.orEmpty())
    var displayName by mutableStateOf(editing?.displayName.orEmpty())

    fun toCredentials(): SesameCredentials =
        SesameCredentials(uuid = uuid, apiKey = apiKey, secretKeyHex = secretKeyHex, displayName = displayName)
}

private const val ADD_TITLE = "新しいSesameを追加"
private const val EDIT_TITLE = "Sesameを編集"
private const val SAVE_LABEL = "保存"
private const val CANCEL_LABEL = "キャンセル"
private const val FORM_HINT = "uuid・apikey・secretKeyはSESAME Bizの開発者ページで確認できます（ヘルプ参照）。"

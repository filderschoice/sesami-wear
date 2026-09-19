package com.sesamiwear.mobile.credentials

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.mobile.ble.SesameBlePermissionAskedStore
import com.sesamiwear.mobile.ble.SesameBlePermissionPrompt
import com.sesamiwear.mobile.ble.SesameBlePermissions
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * BLE直接操作の権限を任意で求めるセクション（BL-153）。
 *
 * **権限は必須ではない。** 許可されていなくてもアプリは従来どおりWeb API経由で動き、
 * 現在どちらで動いているかを1行で常時表示する。
 *
 * 資格情報設定画面は縦スクロールしないため、長い説明は常時表示せず、
 * 権限を求める直前に出す説明ダイアログ（Androidが推奨するrationaleの形）へ回す。
 * 位置情報の許可が必要になるAndroid 11以前では、近くのSesameを探すためだけに使い
 * 収集も送信もしないことを同じダイアログに明示する（BL-153の完了条件）。
 *
 * 一度拒否された権限は再要求してもダイアログが出ないことがあるため、要求済みの場合は
 * 端末のアプリ設定画面を開く導線へ切り替える（[SesameBlePermissionPrompt.requestsPermission]）。
 * 画面が再開するたび（`ON_RESUME`）に許可状況を読み直し、設定画面で許可して戻ったときに追随させる
 * （`rememberApiUsageCount`と同じ理由、BL-159）。
 *
 * Compose画面のためユニットテスト対象外（文言と状態の決定は`SesameBlePermissionPromptTest`で検証済み）。
 */
@Composable
internal fun BlePermissionSection() {
    val context = LocalContext.current
    val askedStore =
        remember { SesameBlePermissionAskedStore(SharedPreferencesKeyValueStore.forBleReachability(context)) }
    var missing by remember { mutableStateOf(SesameBlePermissions.missing(context)) }
    var asked by remember { mutableStateOf(askedStore.wasAsked()) }
    var showRationale by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { missing = SesameBlePermissions.missing(context) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            missing = SesameBlePermissions.missing(context)
        }

    val state = SesameBlePermissionPrompt.state(missing, asked)

    if (showRationale) {
        BlePermissionRationaleDialog(
            state = state,
            onConfirm = {
                showRationale = false
                if (SesameBlePermissionPrompt.requestsPermission(state)) {
                    askedStore.markAsked()
                    asked = true
                    launcher.launch(missing.toTypedArray())
                } else {
                    context.startActivity(appSettingsIntent(context.packageName))
                }
            },
            onDismiss = { showRationale = false },
        )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = SesameBlePermissionPrompt.shortStatus(state), modifier = Modifier.weight(1f))
        if (SesameBlePermissionPrompt.buttonLabel(state) != null) {
            TextButton(onClick = { showRationale = true }) {
                Text(text = "設定")
            }
        }
    }
}

/**
 * 権限を要求する前に出す説明ダイアログ。何に使うのか、許可しなくても動くこと、
 * （Android 11以前では）位置情報を収集・送信しないことをここで伝える。
 */
@Composable
private fun BlePermissionRationaleDialog(
    state: SesameBlePermissionPrompt.State,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = SesameBlePermissionPrompt.TITLE) },
        text = { Text(text = SesameBlePermissionPrompt.summary(state, Build.VERSION.SDK_INT)) },
        confirmButton = {
            SesameBlePermissionPrompt.buttonLabel(state)?.let { label ->
                TextButton(onClick = onConfirm) { Text(text = label) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = SesameBlePermissionPrompt.DISMISS_LABEL) }
        },
    )
}

/** 端末の「アプリ情報」画面を開くIntent。拒否済みの権限は、ここからでないと許可できない。 */
private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

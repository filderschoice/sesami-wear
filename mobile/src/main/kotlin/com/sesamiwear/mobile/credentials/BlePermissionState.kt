package com.sesamiwear.mobile.credentials

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.mobile.ble.SesameBlePermissionAskedStore
import com.sesamiwear.mobile.ble.SesameBlePermissionPrompt
import com.sesamiwear.mobile.ble.SesameBlePermissions
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * BLE直接操作の権限を任意で求めるための状態（BL-153 / BL-180）。
 *
 * **権限は必須ではない。** 許可されていなくてもアプリは従来どおりWeb API経由で動く。
 * 現在どちらで動いているかは、上部バーの設定メニュー（[SettingsMenu]）の項目の副題に出る
 * （もとは画面本体へ1行を常時置いていたが、主画面の情報量を抑えるため移した）。
 *
 * 長い説明は、権限を求める直前に出す説明ダイアログ（Androidが推奨するrationaleの形、
 * [BlePermissionRationaleDialog]）へ回す。位置情報の許可が必要になるAndroid 11以前では、
 * 近くのSesameを探すためだけに使い収集も送信もしないことを同じダイアログに明示する。
 *
 * 一度拒否された権限は再要求してもダイアログが出ないことがあるため、要求済みの場合は
 * 端末のアプリ設定画面を開く導線へ切り替える（[SesameBlePermissionPrompt.requestsPermission]）。
 * 画面が再開するたび（`ON_RESUME`）に許可状況を読み直し、設定画面で許可して戻ったときに追随させる
 * （BL-159と同じ理由）。
 *
 * Compose画面のためユニットテスト対象外（文言と状態の決定は`SesameBlePermissionPromptTest`で検証済み）。
 */
internal class BlePermissionState(
    val state: SesameBlePermissionPrompt.State,
    /** 設定メニューの副題に出す1行（「Bluetooth：未許可（インターネット経由で動作中）」など）。 */
    val shortStatus: String,
    /** 説明ダイアログの確定ボタンの文言。nullなら求めるものが無い（許可済み）。 */
    val buttonLabel: String?,
    /** 説明ダイアログで確定したときの処理（権限要求、または端末の設定画面を開く）。 */
    val confirm: () -> Unit,
)

@Composable
internal fun rememberBlePermissionState(): BlePermissionState {
    val context = LocalContext.current
    val askedStore =
        remember { SesameBlePermissionAskedStore(SharedPreferencesKeyValueStore.forBleReachability(context)) }
    var missing by remember { mutableStateOf(SesameBlePermissions.missing(context)) }
    var asked by remember { mutableStateOf(askedStore.wasAsked()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { missing = SesameBlePermissions.missing(context) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            missing = SesameBlePermissions.missing(context)
        }

    val state = SesameBlePermissionPrompt.state(missing, asked)
    return BlePermissionState(
        state = state,
        shortStatus = SesameBlePermissionPrompt.shortStatus(state),
        buttonLabel = SesameBlePermissionPrompt.buttonLabel(state),
        confirm = {
            if (SesameBlePermissionPrompt.requestsPermission(state)) {
                askedStore.markAsked()
                asked = true
                launcher.launch(missing.toTypedArray())
            } else {
                context.startActivity(appSettingsIntent(context.packageName))
            }
        },
    )
}

/**
 * 権限を要求する前に出す説明ダイアログ。何に使うのか、許可しなくても動くこと、
 * （Android 11以前では）位置情報を収集・送信しないことをここで伝える。
 */
@Composable
internal fun BlePermissionRationaleDialog(
    permission: BlePermissionState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = SesameBlePermissionPrompt.TITLE) },
        text = { Text(text = SesameBlePermissionPrompt.summary(permission.state, Build.VERSION.SDK_INT)) },
        confirmButton = {
            permission.buttonLabel?.let { label ->
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

/** 設定メニューの項目名。 */
internal const val BLE_PERMISSION_MENU_TITLE = "Bluetoothで直接操作"

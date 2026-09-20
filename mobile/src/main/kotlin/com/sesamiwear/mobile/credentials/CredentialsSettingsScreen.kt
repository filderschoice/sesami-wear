package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.state.ApiUsageCounter
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import kotlinx.coroutines.launch

/**
 * 複数台のSesameデバイスの資格情報（uuid/apikey/secretKey/表示名）を一覧・追加・編集・削除する画面（BL-049）。
 *
 * 構成は上部バー＋デバイスのカード一覧＋右下の追加ボタン（BL-177）。もとは見出し・API呼び出し回数・
 * 一覧・接続の設定・入力フォームが1画面へ縦に並び、文字ばかりで情報の区切りが見えなかった。
 * 追加・編集は全画面ダイアログ（[CredentialsEditorDialog]、BL-178）、削除は確認ダイアログ（BL-179）。
 * 保存・削除とそれに伴う同期は[CredentialsScreenController]が持つ。
 *
 * uuidをデバイスの一意キーとして扱い、既存uuidでの保存は上書き、新規uuidでの保存は追加になる。
 * uuid/apikey/secretKeyはすべてbiz.candyhouse.co（SESAME Biz 開発者ページ）から取得する想定
 * （BL-059、Sesameアプリの「鍵をシェア」QRコードは使わない）。secretKeyは16進数32文字（BL-058）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsSettingsScreen(
    credentialsStore: SesameCredentialsStore,
    onSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { CredentialsScreenController(context, credentialsStore, coroutineScope) }
    val snackbarHostState = remember { SnackbarHostState() }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<SesameCredentials?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    if (showHelp) HelpDialog(onDismiss = { showHelp = false })
    editorTarget?.let { target ->
        CredentialsEditorDialog(
            editing = target.editing,
            onDismiss = { editorTarget = null },
            onSave = { edited ->
                controller.save(editingUuid = target.editing?.uuid, edited = edited)
                editorTarget = null
                coroutineScope.launch { snackbarHostState.showSnackbar(SAVED_MESSAGE) }
                onSaved()
            },
        )
    }
    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            credentials = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                controller.delete(target.uuid)
                deleteTarget = null
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = { ScreenTopBar(onHelpClick = { showHelp = true }) },
        floatingActionButton = { AddDeviceButton(onClick = { editorTarget = EditorTarget(editing = null) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        DeviceListContent(
            devices = controller.devices,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            onEdit = { editorTarget = EditorTarget(editing = it) },
            onDelete = { deleteTarget = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenTopBar(onHelpClick: () -> Unit) {
    TopAppBar(
        title = { Text(text = SCREEN_TITLE) },
        actions = { TextButton(onClick = onHelpClick) { Text(HELP_LABEL) } },
    )
}

@Composable
private fun AddDeviceButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
        text = { Text(ADD_LABEL) },
    )
}

/**
 * 画面本体（BL-177）。今月のAPI呼び出し回数・デバイスのカード・接続の設定を縦に並べる。
 * 画面全体が縦スクロールする（もとは`Column`の中に`LazyColumn`が入れ子になっており、
 * 一覧の外側はスクロールできなかった）。
 */
@Composable
private fun DeviceListContent(
    devices: List<SesameCredentials>,
    modifier: Modifier,
    onEdit: (SesameCredentials) -> Unit,
    onDelete: (SesameCredentials) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = ApiUsageCounter.label(rememberApiUsageCount()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (devices.isEmpty()) {
            item { Text(text = EMPTY_MESSAGE, style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(devices, key = { it.uuid }) { credentials ->
                DeviceCard(
                    credentials = credentials,
                    onEdit = { onEdit(credentials) },
                    onDelete = { onDelete(credentials) },
                )
            }
        }
        item { ConnectionSettingsSection() }
        // 最後のカードが追加ボタンに隠れないようにする。
        item { Spacer(modifier = Modifier.height(FAB_CLEARANCE_DP.dp)) }
    }
}

/**
 * 削除の確認（BL-179）。もとは確認なしで即座に消えていたが、削除すると資格情報と残存状態が
 * すべて消え、再登録にはSESAME BizからsecretKeyを取り直す必要があるため、誤タップの損失が大きい。
 */
@Composable
private fun DeleteConfirmDialog(
    credentials: SesameCredentials,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val name = credentials.displayName.ifBlank { credentials.uuid }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${name}を削除しますか？") },
        text = { Text(text = DELETE_WARNING) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(DELETE_LABEL) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(CANCEL_LABEL) } },
    )
}

/**
 * 今月のSesame Web API呼び出し回数（BL-147）。画面が再開するたび（`ON_RESUME`）に読み直す（BL-159）。
 * コンポジション生成時に1回だけ読むと、Activityが破棄されずに再表示された場合
 * （ホームへ退避してから戻った場合など）に、ウィジェット・ウォッチからの操作で増えた分が反映されない。
 */
@Composable
private fun rememberApiUsageCount(): Int {
    val context = LocalContext.current
    val counter = remember { ApiUsageCounter(SharedPreferencesKeyValueStore.forApiUsage(context)) }
    var count by remember { mutableStateOf(counter.countOf(System.currentTimeMillis())) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { count = counter.countOf(System.currentTimeMillis()) }
    return count
}

/** 追加・編集ダイアログの対象。[editing]がnullなら新規追加。 */
private data class EditorTarget(val editing: SesameCredentials?)

private const val SCREEN_TITLE = "Sesami Wear"
private const val HELP_LABEL = "ヘルプ"
private const val ADD_LABEL = "Sesameを追加"
private const val SAVED_MESSAGE = "保存しました"
private const val EMPTY_MESSAGE = "まだSesameが登録されていません。右下の「Sesameを追加」から登録してください。"
private const val DELETE_WARNING =
    "登録した資格情報と、このセサミの状態が端末から消えます。" +
        "登録し直すにはSESAME Bizからapikey・secretKeyを取り直す必要があります。"
private const val DELETE_LABEL = "削除"
private const val CANCEL_LABEL = "キャンセル"

/** 一覧の末尾に足す余白。追加ボタン（FAB）の高さ＋余白ぶん。 */
private const val FAB_CLEARANCE_DP = 72

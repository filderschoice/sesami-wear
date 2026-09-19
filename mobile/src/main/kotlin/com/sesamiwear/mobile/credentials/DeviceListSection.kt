package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.display.SesameDeviceStatusLine
import com.sesamiwear.mobile.state.LockStateStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

@Composable
internal fun DeviceList(
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = credentials.displayName.ifBlank { credentials.uuid })
                    Text(text = statusLineOf(credentials.uuid))
                }
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

/**
 * デバイス一覧の各行へ出す状態の1行（BL-169）。施錠状態・電池残量・角度・最終取得時刻・経路を並べる。
 *
 * 画面が再開するたび（`ON_RESUME`）に読み直す。ウィジェット・ウォッチからの操作で変わった分を
 * 反映するため（`rememberApiUsageCount`と同じ理由、BL-159）。
 *
 * **この表示のために状態取得のリクエストは送らない。** 最後に分かった値をそのまま出すだけで、
 * Sesame Web APIのリクエスト回数を消費しない（BL-142の方針を維持する）。
 */
@Composable
private fun statusLineOf(uuid: String): String {
    val context = LocalContext.current
    val store = remember { LockStateStore(SharedPreferencesKeyValueStore.forLockState(context)) }
    var line by remember { mutableStateOf(statusLine(store, uuid)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { line = statusLine(store, uuid) }
    return line
}

private fun statusLine(
    store: LockStateStore,
    uuid: String,
): String =
    SesameDeviceStatusLine.label(
        snapshot = store.load(uuid),
        nowEpochMillis = System.currentTimeMillis(),
        // 設定画面は情報量を許せる面のため、角度まで出す（BL-169）。
        includePosition = true,
    )

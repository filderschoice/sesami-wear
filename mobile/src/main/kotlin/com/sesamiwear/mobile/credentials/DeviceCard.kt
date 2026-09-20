package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.core.display.SesameDeviceStatusLine
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.mobile.state.LockStateStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * デバイス一覧の1台分（BL-177）。
 *
 * もとは1行のテキストと「編集」「削除」の文字ボタンだったが、施錠状態・電池・角度・最終取得・経路を
 * 1行へ詰め込むと端末の幅に収まらず折り返されていた。カードにして、
 * (1) 状態アイコンと名前、(2) 施錠状態・電池・角度、(3) 最終取得と経路、へ行を分ける。
 * 行の組み立ては`core.display.SesameDeviceStatusLine.lines`（Android非依存、ユニットテスト対象）。
 *
 * **この表示のために状態取得のリクエストは送らない。** 最後に分かった値をそのまま出すだけで、
 * Sesame Web APIのリクエスト回数を消費しない（BL-142の方針を維持する）。
 * 画面が再開するたび（`ON_RESUME`）に読み直すのは、ウィジェット・ウォッチからの操作で
 * 変わった分を反映するため（BL-159と同じ理由）。
 */
@Composable
internal fun DeviceCard(
    credentials: SesameCredentials,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val snapshot = rememberSnapshot(credentials.uuid)
    // 設定画面は情報量を許せる面のため、角度まで出す（BL-169）。
    val lines =
        SesameDeviceStatusLine.lines(
            snapshot = snapshot,
            nowEpochMillis = System.currentTimeMillis(),
            includePosition = true,
        )
    val state =
        TileDisplayStateResolver.resolve(
            isPhoneConnected = true,
            isCommandInProgress = false,
            isLocked = snapshot?.isLocked,
        )
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = SesameTileContent.statusIcon(state), style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = credentials.displayName.ifBlank { credentials.uuid },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lines.statusLine.isNotEmpty()) {
                    Text(text = lines.statusLine, style = MaterialTheme.typography.bodyMedium)
                }
                DetailLine(freshnessLine = lines.freshnessLine, snapshot = snapshot)
            }
            IconButton(onClick = onEdit) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = EDIT_DESCRIPTION)
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = DELETE_DESCRIPTION)
            }
        }
    }
}

/**
 * 最終取得（または直近の失敗の理由）と経路の行。
 * 経路は**語で書く**（「Bluetooth」「インターネット」）。この画面は表示領域に余裕があり、
 * アイコンだけより語のほうが誤解が無いため（Tile・ウィジェットは逆にアイコン、BL-168）。
 */
@Composable
private fun DetailLine(
    freshnessLine: String,
    snapshot: SesameStatusSnapshot?,
) {
    val route = snapshot?.lastRoute
    val text =
        if (route == null) freshnessLine else "$freshnessLine ・ ${SesameRouteLabel.name(route)}"
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 保存済みのスナップショット。画面が再開するたびに読み直す（BL-159）。 */
@Composable
private fun rememberSnapshot(uuid: String): SesameStatusSnapshot? {
    val context = LocalContext.current
    val store = remember { LockStateStore(SharedPreferencesKeyValueStore.forLockState(context)) }
    var snapshot by remember { mutableStateOf(store.load(uuid)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { snapshot = store.load(uuid) }
    return snapshot
}

private const val EDIT_DESCRIPTION = "編集"
private const val DELETE_DESCRIPTION = "削除"

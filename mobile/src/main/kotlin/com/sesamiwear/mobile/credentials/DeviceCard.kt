package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.core.display.SesameBleConnectionLabel
import com.sesamiwear.core.display.SesameDeviceStatusLine
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.core.display.SesameTileContent
import com.sesamiwear.mobile.R
import com.sesamiwear.mobile.ble.SesameBlePermissions
import com.sesamiwear.mobile.ble.SesameBleReachability
import com.sesamiwear.mobile.ble.SesameRoutePolicyStore
import com.sesamiwear.mobile.dial.DialActivity
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import com.sesamiwear.mobile.ui.SesameRouteIcon

/**
 * デバイス一覧の1台分（BL-177）。
 *
 * もとは1行のテキストと「編集」「削除」の文字ボタンだったが、施錠状態・電池・角度・最終取得・経路を
 * 1行へ詰め込むと端末の幅に収まらず折り返されていた。カードにして、
 * (1) 状態アイコンと名前、(2) 施錠状態・電池・角度、(3) 最終取得と経路、(4) Bluetoothの到達状況
 * （BL-190）、へ行を分ける。
 * BL-245で見た目を整理した: 状態は色付きの丸いバッジ、編集・削除はカード右上の⋮、
 * ダイヤル操作（BL-243）は文字付きのボタン。
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge(state = state)
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
                }
                CardMenu(onEdit = onEdit, onDelete = onDelete)
            }
            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DetailLine(freshnessLine = lines.freshnessLine, snapshot = snapshot)
                Text(
                    text = rememberBleConnectionLine(credentials.uuid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // ダイヤル操作（BL-243）。Bluetoothで直接回す、施錠・解錠が効かないときなどの手段。
            val context = LocalContext.current
            FilledTonalButton(
                onClick = { context.startActivity(DialActivity.createIntent(context, credentials.uuid)) },
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_route_bluetooth),
                    contentDescription = null,
                    modifier = Modifier.size(BUTTON_ICON_SIZE_DP.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = DIAL_LABEL)
            }
        }
    }
}

/**
 * 施錠状態の丸いバッジ（BL-245）。もとは絵文字（🔒など）を文字として置いていたが、端末のフォントで
 * 見た目が変わり、色でも状態を読めなかった。背景はTile・ウィジェットと同じ状態色
 * （`SesameTileContent`の緑・赤・紫・灰）を使い、錠の形のアイコンを重ねる。
 */
@Composable
private fun StatusBadge(state: TileDisplayState) {
    val iconRes = if (state == TileDisplayState.LOCKED) R.drawable.ic_shortcut_lock else R.drawable.ic_qs_lock_open
    Box(
        modifier =
            Modifier
                .size(BADGE_SIZE_DP.dp)
                .clip(CircleShape)
                .background(Color(SesameTileContent.backgroundColorArgb(state))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = SesameTileContent.statusLabel(state),
            tint = Color(SesameTileContent.statusTextColorArgb(state)),
            modifier = Modifier.size(BADGE_ICON_SIZE_DP.dp),
        )
    }
}

/** カード右上の⋮。編集・削除はたまにしか使わないため、ボタンを並べずにメニューへまとめる（BL-245）。 */
@Composable
private fun CardMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = MENU_DESCRIPTION)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(EDIT_DESCRIPTION) },
                leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(DELETE_DESCRIPTION) },
                leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * 最終取得（または直近の失敗の理由）と経路の行。
 *
 * 経路は**アイコンと語を併記する**（BL-176）。この画面は表示領域に余裕があり、語があれば
 * 誤解が無く（BL-168）、同時にTile・ウィジェットで見るアイコンの意味をここで覚えられる。
 * アイコンはMaterial Symbols（[SesameRouteIcon]）。
 */
@Composable
private fun DetailLine(
    freshnessLine: String,
    snapshot: SesameStatusSnapshot?,
) {
    val route = snapshot?.lastRoute
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // 失敗の理由（「通信エラー（電波状況を確認）」など）が長いと、経路の語だけが
        // 押し出されて「Bluet／ooth」のように途中で折り返っていた（2026-09-21の実機検証）。理由側を
        // 縮めて末尾を省略し、経路（アイコン＋語）は必ず1行に収める。
        Text(
            text = freshnessLine,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        SesameRouteIcon.drawableResOrNull(route)?.let { iconRes ->
            Text(text = SEPARATOR, style = MaterialTheme.typography.bodySmall, color = color)
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(ROUTE_ICON_SIZE_DP.dp),
            )
            Text(
                text = SesameRouteLabel.name(route),
                style = MaterialTheme.typography.bodySmall,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Bluetoothの到達状況の1行（BL-190）。
 *
 * 経路（[DetailLine]）が「最後にどちらで動いたか」を表すのに対し、こちらは「次にBLEを使えそうか」を
 * 表す。2026-09-20の実機検証（BL-165）で、内部状態としては圏外判定でもそれが画面のどこにも
 * 出ていないため、利用者が「なぜBluetoothにならないのか」を確認できないと分かったため追加した。
 *
 * 方針が「常にインターネット経由」または権限が無い場合は、到達確認そのものを行わないため
 * [SesameBleConnectionLabel.UNAVAILABLE_LABEL]を出す（「未確認」と書くと確認を待てば変わるように
 * 読めてしまう）。スナップショットと同じく画面が再開するたびに読み直す。
 */
@Composable
private fun rememberBleConnectionLine(uuid: String): String {
    val context = LocalContext.current
    val store = remember { SharedPreferencesKeyValueStore.forBleReachability(context) }
    val reachability = remember { SesameBleReachability(store) }
    val policyStore = remember { SesameRoutePolicyStore(store) }

    fun read(): String =
        if (!policyStore.load().allowsBle || SesameBlePermissions.missing(context).isNotEmpty()) {
            SesameBleConnectionLabel.UNAVAILABLE_LABEL
        } else {
            val now = System.currentTimeMillis()
            val status = reachability.status(uuid, now)
            SesameBleConnectionLabel.label(status.state, status.lastCheckedAtEpochMillis, now)
        }
    var line by remember { mutableStateOf(read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { line = read() }
    return line
}

/** 保存済みのスナップショット。画面が再開するたびに読み直す（BL-159）。 */
@Composable
private fun rememberSnapshot(uuid: String): SesameStatusSnapshot? {
    val context = LocalContext.current
    val store = remember { SesameDeviceStores.lockState(context) }
    var snapshot by remember { mutableStateOf(store.load(uuid)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { snapshot = store.load(uuid) }
    return snapshot
}

private const val DIAL_LABEL = "ダイヤル操作"
private const val MENU_DESCRIPTION = "このSesameのメニュー"
private const val EDIT_DESCRIPTION = "編集"
private const val DELETE_DESCRIPTION = "削除"
private const val SEPARATOR = "・"

private const val BADGE_SIZE_DP = 44
private const val BADGE_ICON_SIZE_DP = 24
private const val BUTTON_ICON_SIZE_DP = 18

/** 経路アイコンの大きさ。本文（bodySmall）の文字と並べて浮かない程度に抑える。 */
private const val ROUTE_ICON_SIZE_DP = 14

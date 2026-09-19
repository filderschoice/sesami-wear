package com.sesamiwear.wear.status

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sesamiwear.wear.messaging.SesameDeviceListReader
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader

/**
 * ウォッチのアプリ本体に出す、登録済みセサミの状態一覧（BL-170）。
 *
 * Tileは操作のための面で表示余白が無く、電池残量や経路までは入らない。アプリ本体はこれまで
 * 「Sesami Wear」と表示するだけのスタブだったため、ここを状態の確認先にする。
 *
 * **この画面から状態取得のリクエストは送らない。** 最後に同期された値（DataItem）をそのまま出す。
 * 開くたびにリクエストが飛ぶと、Sesame Web APIの月間リクエスト上限（BL-141）を画面を開いた回数だけ
 * 消費してしまうため（BL-142で自動取得を廃止した理由と同じ）。値の更新は、スマートフォンでの操作や
 * Tileからの操作が同期されるのを待つ。
 *
 * 画面が再開するたび（`ON_RESUME`）に読み直す。Tileで操作してからアプリへ戻ったときに追随させるため。
 * Compose画面のためユニットテスト対象外（行の組み立ては[SesameStatusListContent]でテスト済み）。
 */
@Composable
fun SesameStatusListScreen() {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<SesameStatusListContent.DeviceRow>>(emptyList()) }
    var reloadCount by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reloadCount++ }
    LaunchedEffect(reloadCount) { rows = loadRows(context) }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ScreenTitle() }
        if (rows.isEmpty()) {
            item { EmptyMessage() }
        } else {
            items(rows) { row -> DeviceStatusItem(row) }
        }
    }
}

/** 同期済みのデバイス一覧と、それぞれの最新スナップショットから表示行を組み立てる。 */
private suspend fun loadRows(context: Context): List<SesameStatusListContent.DeviceRow> {
    val devices = SesameDeviceListReader.readLatest(context)
    val snapshots = devices.associate { it.uuid to SesameStatusSnapshotReader.readLatest(context, it.uuid) }
    return SesameStatusListContent.rows(
        devices = devices,
        snapshotOf = { snapshots[it] },
        nowEpochMillis = System.currentTimeMillis(),
    )
}

@Composable
private fun ScreenTitle() {
    Text(
        text = SesameStatusListContent.TITLE,
        style = MaterialTheme.typography.title3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 一覧が空のときの案内。文言は[SesameStatusListContent]が円形画面の幅に収まる行へ分割済みで、
 * ここでは左右パディングと中央揃えだけを与える（`DeviceSelectionScreen`と同方針、BL-114）。
 */
@Composable
private fun EmptyMessage() {
    Text(
        text = SesameStatusListContent.emptyMessage,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = HORIZONTAL_PADDING_DP.dp),
    )
}

/** 1台分の表示。デバイス名・施錠状態と電池・最終取得時刻と経路を3行で出す。 */
@Composable
private fun DeviceStatusItem(row: SesameStatusListContent.DeviceRow) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HORIZONTAL_PADDING_DP.dp, vertical = ITEM_VERTICAL_PADDING_DP.dp),
    ) {
        Text(
            text = row.displayName,
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = row.statusLine,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = row.detailLine,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 円形画面の縁で文字が見切れないための左右パディング（dp、BL-114と同じ考え方）。 */
private const val HORIZONTAL_PADDING_DP = 12

private const val ITEM_VERTICAL_PADDING_DP = 4

package com.sesamiwear.mobile.radar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.mobile.ble.BleMultiProbeOperations
import com.sesamiwear.mobile.dial.DialActivity
import kotlinx.coroutines.launch

/**
 * 「まとめて接続確認」画面（BL-280、スマートフォン）。登録した全デバイスを1枚のレーダーへ名前つきで置き、
 * 台ごとの段階・距離を一覧にする。「全台の接続を試す」は、Sesameが同時に1接続しか持てないため
 * **1台ずつ順番に**接続・ログインまで確かめる。画面を開いている間（前面にいる間）だけスキャンし、
 * 接続確認中は探索を止める（単体の[BleRadarScreen]と同じ）。実行は[probe]、判定と文言はcoreにある。
 * 画面の組み立てだけのAndroid依存関数のためユニットテスト対象外。
 */
@Composable
internal fun BleRadarAllScreen(
    devices: List<SesameCredentials>,
    probe: BleMultiProbeOperations,
    onClose: () -> Unit,
) {
    val permission = rememberRadarPermission(probe.usesBluetooth)
    val resumed = rememberResumed()
    val initial =
        remember { SesameBleMultiProbeSnapshot.initial(devices.map { SesameDeviceSummary(it.uuid, it.displayName) }) }
    var observed by remember { mutableStateOf(initial) }
    var checking by remember { mutableStateOf<SesameBleMultiProbeSnapshot?>(null) }
    var results by remember { mutableStateOf<SesameBleMultiProbeSnapshot?>(null) }
    val scope = rememberCoroutineScope()

    // 前面にいて、権限があり、接続確認中でないときだけスキャンする（接続とスキャンを同時に走らせない）。
    LaunchedEffect(permission.granted, resumed, checking == null) {
        if (permission.granted && resumed && checking == null) {
            observed = initial
            probe.observe().collect { observed = it }
        }
    }

    val shown = checking ?: observed
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = ALL_TITLE, style = MaterialTheme.typography.headlineSmall)
        val context = LocalContext.current
        // 台の行・レーダーのラベルをタップすると、その台のダイヤル操作へ移る（BL-283）。
        val openDial = { uuid: String -> context.startActivity(DialActivity.createIntent(context, uuid)) }
        AllRadar(shown = shown, onOpenDial = openDial)
        shown.entries.forEach { entry ->
            DeviceRow(
                entry = entry,
                result = results?.entries?.find { it.uuid == entry.uuid },
                onOpenDial = { openDial(entry.uuid) },
            )
        }
        HoldHint(results)
        Text(
            text = "距離は電波の強さからの目安です。壁・人・向きで大きく変わります。接続の確認は1台ずつ順番に行います。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text(text = "閉じる") }
            Button(
                enabled = permission.granted && checking == null,
                onClick = {
                    scope.launch {
                        checking = observed
                        results = null
                        results = probe.checkConnections(observed) { checking = it }
                        checking = null
                    }
                },
            ) { Text(text = if (checking == null) "全台の接続を試す" else "確認中…") }
        }
        if (!permission.granted) {
            OutlinedButton(onClick = permission.request) { Text(text = "Bluetoothの権限を許可") }
        }
    }
}

/** 1台でも接続OKなら、接続を保持していることを説明する（BL-279）。 */
@Composable
private fun HoldHint(results: SesameBleMultiProbeSnapshot?) {
    if (results?.entries?.any { it.snapshot.stage == SesameBleProbeStage.CONNECTED } != true) return
    Text(
        text = HOLD_HINT,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * 全台のレーダー。名前つきのブリップを置き、ダイヤルを開ける台（[SesameBleProbeSnapshot.canOpenDial]）の
 * ブリップと名前ラベルはタップできる（BL-283）。
 */
@Composable
private fun AllRadar(
    shown: SesameBleMultiProbeSnapshot,
    onOpenDial: (String) -> Unit,
) {
    BleRadar(
        blips =
            shown.entries.mapIndexed { index, entry ->
                RadarBlip(
                    deviceUuid = entry.uuid,
                    label = entry.name.ifBlank { DEFAULT_NAME },
                    snapshot = entry.snapshot,
                    tappable = entry.snapshot.canOpenDial,
                    bearing = RadarHitTest.evenBearing(index, shown.entries.size),
                )
            },
        description = "レーダー。" + shown.entries.joinToString("。") { it.name + "、" + it.snapshot.summaryLine() },
        modifier = Modifier.fillMaxWidth(),
        onBlipClick = onOpenDial,
    )
}

/**
 * 1台ぶんの行。名前・段階と距離・（接続確認が済んでいれば）その結果。ダイヤルを開ける台
 * （[SesameBleProbeSnapshot.canOpenDial]）は行全体がタップでき、右に「ダイヤル操作 ›」を出す（BL-283）。
 */
@Composable
private fun DeviceRow(
    entry: SesameBleMultiProbeSnapshot.Entry,
    result: SesameBleMultiProbeSnapshot.Entry?,
    onOpenDial: () -> Unit,
) {
    val problem = MaterialTheme.colorScheme.error
    val canOpen = entry.snapshot.canOpenDial
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = canOpen, onClickLabel = DIAL_CLICK_LABEL, onClick = onOpenDial)
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.name.ifBlank { DEFAULT_NAME },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (canOpen) {
                Text(
                    text = DIAL_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = entry.snapshot.summaryLine(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (entry.snapshot.stage.isProblem) problem else MaterialTheme.colorScheme.onSurface,
        )
        result?.let {
            Text(
                text = "接続確認：${it.snapshot.stage.label}" + (it.snapshot.connectionDetail()?.let { d -> "（$d）" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.snapshot.stage.isProblem) problem else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val ALL_TITLE = "全デバイス"
private const val DEFAULT_NAME = "Sesame"
private const val DIAL_HINT = "ダイヤル操作 ›"
private const val DIAL_CLICK_LABEL = "ダイヤル操作を開く"

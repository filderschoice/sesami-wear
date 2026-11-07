package com.sesamiwear.mobile.radar

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.mobile.ble.BleMultiProbeOperations
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
        BleRadar(
            blips = shown.entries.map { RadarBlip(it.uuid, it.name.ifBlank { DEFAULT_NAME }, it.snapshot) },
            description = "レーダー。" + shown.entries.joinToString("。") { it.name + "、" + it.snapshot.summaryLine() },
            modifier = Modifier.fillMaxWidth(),
        )
        shown.entries.forEach { entry ->
            DeviceRow(entry = entry, result = results?.entries?.find { it.uuid == entry.uuid })
        }
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

/** 1台ぶんの行。名前・段階と距離・（接続確認が済んでいれば）その結果。 */
@Composable
private fun DeviceRow(
    entry: SesameBleMultiProbeSnapshot.Entry,
    result: SesameBleMultiProbeSnapshot.Entry?,
) {
    val problem = MaterialTheme.colorScheme.error
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = entry.name.ifBlank { DEFAULT_NAME }, style = MaterialTheme.typography.titleMedium)
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

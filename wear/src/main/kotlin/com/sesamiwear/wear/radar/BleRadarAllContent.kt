package com.sesamiwear.wear.radar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import kotlinx.coroutines.launch

/**
 * 「まとめて接続確認」（BL-280、ウォッチ）。全デバイスの段階と距離を、台数ぶんの1行リストで出す
 * （円形の小画面ではレーダーを描かず、台ごとに「名前」と「発見・約3m」の2行へ絞る）。
 * 末尾の「全台を試す」で、スマートフォンが全台の接続を1台ずつ順番に確かめる。
 * 探索と接続はスマートフォンが行い、ウォッチは進捗を受けて表示するだけ（[ProbeLink]）。
 * Compose画面のためユニットテスト対象外（行の文言はcoreでテスト済み）。
 */
@Composable
internal fun RadarAllContent(devices: List<SesameDeviceSummary>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(SesameBleMultiProbeSnapshot.initial(devices)) }
    var phoneMissing by remember { mutableStateOf(false) }
    val resumed = rememberResumed()

    ProbeLink(
        deviceUuid = SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
        resumed = resumed,
        onProgress = {},
        onPhoneMissing = { phoneMissing = it },
        onProgressAll = { snapshot = it },
    )

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Text(text = ALL_TITLE, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center) }
        if (phoneMissing) {
            item { Text(text = PHONE_MISSING, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
        } else {
            items(snapshot.entries) { entry -> EntryRow(entry) }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CHIP_PADDING_DP.dp),
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = {
                        scope.launch {
                            SesameConnectedNodeProvider.firstConnectedNodeId(context)?.let {
                                SesameCommandSenderProvider.create(context).requestBleProbe(
                                    it,
                                    SesameWearProtocol.PATH_BLE_PROBE_CONNECT,
                                    SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
                                )
                            }
                        }
                    },
                    label = { Text(text = TRY_ALL_LABEL, maxLines = 1) },
                )
            }
        }
    }
}

/** 1台ぶんの行。名前（1行）と、段階・距離（1行）。失敗の段階は赤で示す。 */
@Composable
private fun EntryRow(entry: SesameBleMultiProbeSnapshot.Entry) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_PADDING_DP.dp),
    ) {
        Text(
            text = entry.name.ifBlank { entry.uuid },
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
        )
        Text(
            text = entry.statusLine(),
            style = MaterialTheme.typography.caption2,
            color = rowColor(entry.snapshot),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
        )
    }
}

@Composable
private fun rowColor(snapshot: SesameBleProbeSnapshot) =
    if (snapshot.stage.isProblem) MaterialTheme.colors.error else MaterialTheme.colors.onBackground

private const val ALL_TITLE = "全デバイス"
private const val PHONE_MISSING = "スマホ未接続"
private const val TRY_ALL_LABEL = "全台を試す"
private const val CHIP_PADDING_DP = 24
private const val ROW_PADDING_DP = 20

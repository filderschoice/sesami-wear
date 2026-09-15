package com.sesamiwear.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.wear.messaging.SesameDeviceListReader

/**
 * mobile側に登録済みのSesameデバイス一覧から1台（または「全デバイス」）を選択する画面
 * （BL-052/BL-054共通）。Tile/Complication双方のConfiguration Activityから利用する。
 * 選択肢は[SesameDeviceListReader]でmobile側から同期されたデバイス一覧を読み取って表示する。
 * 登録済みデバイスが2台以上の場合のみ、先頭に「全デバイス」（`SesameWearProtocol.ALL_DEVICES_TARGET_UUID`）
 * の選択肢を表示する（BL-071、複数デバイス一括操作。1台のみの場合は個別選択と等価になり冗長なため出さない）。
 * 登録済みデバイスが0台（資格情報が未登録、またはまだ同期されていない）の場合は、
 * 選択肢としてデモ用デバイス（[SesameDemoMode]、BL-109）のみを提示する。Sesame実機を持たない
 * クローズドテストのテスターでも、Tile・Complicationの操作感を確認できるようにするため。
 * 選択肢の組み立て（上記の規則と並び順）は[SesameDeviceTargets.choices]（BL-119でcoreへ移設、
 * mobileのウィジェットと共通）が持つ。
 */
@Composable
fun DeviceSelectionScreen(onDeviceSelected: (String) -> Unit) {
    val context = LocalContext.current
    var registeredDevices by remember { mutableStateOf<List<SesameDeviceSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        registeredDevices = SesameDeviceListReader.readLatest(context)
    }

    val isDemoMode = SesameDemoMode.isAvailable(registeredDevices)
    val choices = SesameDeviceTargets.choices(registeredDevices)

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        if (isDemoMode) {
            item {
                DemoHint()
            }
        }
        items(choices) { choice ->
            Chip(
                label = { Text(text = choice.label) },
                onClick = { onDeviceSelected(choice.uuid) },
            )
        }
    }
}

/**
 * デモモード時にデバイス選択肢の上へ出す説明（BL-109/BL-114）。文言は
 * [DeviceSelectionContent]が円形画面の幅に収まる行へ分割済みで、ここでは左右パディングと
 * 中央揃えだけを与える。画面幅に折り返しを委ねると、円形画面の縁で行頭・行末の文字が
 * 見切れるため（BL-114）。
 */
@Composable
private fun DemoHint() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DeviceSelectionContent.HINT_HORIZONTAL_PADDING_DP.dp),
    ) {
        Text(
            text = DeviceSelectionContent.DEMO_HINT_TITLE,
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = DeviceSelectionContent.demoHintBody,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package com.sesamiwear.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.wear.messaging.SesameDeviceListReader

/**
 * mobile側に登録済みのSesameデバイス一覧から1台を選択する画面（BL-052/BL-054共通）。
 * Tile/Complication双方のConfiguration Activityから利用する。
 * 選択肢は[SesameDeviceListReader]でmobile側から同期されたデバイス一覧を読み取って表示する。
 */
@Composable
fun DeviceSelectionScreen(onDeviceSelected: (String) -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<SesameDeviceSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        devices = SesameDeviceListReader.readLatest(context)
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        if (devices.isEmpty()) {
            item { Text(text = "スマホでSesameを登録してください") }
        }
        items(devices) { device ->
            Chip(
                label = { Text(text = device.displayName.ifBlank { device.uuid }) },
                onClick = { onDeviceSelected(device.uuid) },
            )
        }
    }
}

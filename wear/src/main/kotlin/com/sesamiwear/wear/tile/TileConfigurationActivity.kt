package com.sesamiwear.wear.tile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tiles.TileService
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.wear.messaging.SesameDeviceListReader

/**
 * Tileごとに操作対象Sesameデバイスを1台選択する設定画面（BL-052、複数Tileインスタンス方式）。
 * 選択肢は[SesameDeviceListReader]でmobile側から同期されたデバイス一覧を読み取って表示する。
 * 選択結果は[TileDeviceAssignmentStore]へtileIdをキーとして永続化し、
 * 対象Tileの再描画を要求してから終了する。実際にこのActivityをtileId付きで起動する導線
 * （未設定Tileからのタップ誘導）はBL-053で実装する。
 */
class TileConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.getIntExtra(EXTRA_TILE_ID, INVALID_TILE_ID)
        if (tileId == INVALID_TILE_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                TileConfigurationScreen(
                    onDeviceSelected = { uuid ->
                        TileDeviceAssignmentStore(applicationContext).assignDevice(tileId, uuid)
                        TileService.getUpdater(applicationContext).requestUpdate(SesameTileService::class.java)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TILE_ID = "tile_id"
        private const val INVALID_TILE_ID = -1

        fun createIntent(
            context: Context,
            tileId: Int,
        ): Intent =
            Intent(context, TileConfigurationActivity::class.java).apply {
                putExtra(EXTRA_TILE_ID, tileId)
            }
    }
}

@Composable
private fun TileConfigurationScreen(onDeviceSelected: (String) -> Unit) {
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

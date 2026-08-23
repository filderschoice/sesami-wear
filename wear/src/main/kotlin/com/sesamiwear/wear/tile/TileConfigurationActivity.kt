package com.sesamiwear.wear.tile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.tiles.TileService
import com.sesamiwear.wear.messaging.SesameDeviceListReader
import com.sesamiwear.wear.ui.DeviceSelectionScreen

/**
 * Tileごとに操作対象Sesameデバイスを1台選択する設定画面（BL-052、複数Tileインスタンス方式）。
 * 選択肢は[SesameDeviceListReader]でmobile側から同期されたデバイス一覧を[DeviceSelectionScreen]
 * （Tile/Complication共通）で表示する。
 * 選択結果は[TileDeviceAssignmentStore]へtileIdをキーとして永続化し、
 * 対象Tileの再描画を要求してから終了する。未設定Tileからのタップ誘導は
 * [SesameTileService]がtileIdを文字列Extraとして渡して起動する（BL-053）。
 */
class TileConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.getStringExtra(EXTRA_TILE_ID)?.toIntOrNull()
        if (tileId == null) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                DeviceSelectionScreen(
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
        const val EXTRA_TILE_ID = "tile_id"

        fun createIntent(
            context: Context,
            tileId: Int,
        ): Intent =
            Intent(context, TileConfigurationActivity::class.java).apply {
                putExtra(EXTRA_TILE_ID, tileId.toString())
            }
    }
}

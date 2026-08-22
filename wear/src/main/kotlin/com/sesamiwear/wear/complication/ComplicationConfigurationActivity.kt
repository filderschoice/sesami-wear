package com.sesamiwear.wear.complication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.sesamiwear.wear.ui.DeviceSelectionScreen

/**
 * Complicationインスタンスごとに表示対象Sesameデバイスを1台選択する設定画面（BL-054、
 * 複数Complicationインスタンス方式）。選択肢は[com.sesamiwear.wear.messaging.SesameDeviceListReader]
 * でmobile側から同期されたデバイス一覧を[DeviceSelectionScreen]（Tile/Complication共通）で表示する。
 * 選択結果は[ComplicationDeviceAssignmentStore]へcomplicationInstanceIdをキーとして永続化し、
 * 対象Complicationの再描画を要求してから終了する。未設定Complicationからのタップ誘導は
 * [SesameComplicationDataSourceService]がtapActionでこのActivityを起動する。
 */
class ComplicationConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val complicationInstanceId = intent.getStringExtra(EXTRA_COMPLICATION_INSTANCE_ID)?.toIntOrNull()
        if (complicationInstanceId == null) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                DeviceSelectionScreen(
                    onDeviceSelected = { uuid ->
                        ComplicationDeviceAssignmentStore(applicationContext)
                            .assignDevice(complicationInstanceId, uuid)
                        requestComplicationUpdate(complicationInstanceId)
                        finish()
                    },
                )
            }
        }
    }

    private fun requestComplicationUpdate(complicationInstanceId: Int) {
        val componentName = ComponentName(applicationContext, SesameComplicationDataSourceService::class.java)
        ComplicationDataSourceUpdateRequester.create(applicationContext, componentName)
            .requestUpdate(complicationInstanceId)
    }

    companion object {
        const val EXTRA_COMPLICATION_INSTANCE_ID = "complication_instance_id"

        fun createIntent(
            context: Context,
            complicationInstanceId: Int,
        ): Intent =
            Intent(context, ComplicationConfigurationActivity::class.java).apply {
                putExtra(EXTRA_COMPLICATION_INSTANCE_ID, complicationInstanceId.toString())
            }
    }
}

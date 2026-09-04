package com.sesamiwear.wear.complication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.sesamiwear.wear.ui.DeviceSelectionScreen

/**
 * Complicationインスタンスごとに表示対象Sesameデバイスを1台選択する設定画面（BL-054、
 * 複数Complicationインスタンス方式）。選択肢は[com.sesamiwear.wear.messaging.SesameDeviceListReader]
 * でmobile側から同期されたデバイス一覧を[DeviceSelectionScreen]（Tile/Complication共通）で表示する。
 * 選択結果は[ComplicationDeviceAssignmentStore]へcomplicationInstanceIdをキーとして永続化し、
 * 対象Complicationの再描画を要求してから終了する。
 *
 * 起動経路は2つある（BL-073）。
 * 1. 文字盤のComplicationピッカーでデータソースを選んだ直後にシステムが起動する標準の設定導線
 *    （`AndroidManifest.xml`の`PROVIDER_CONFIG_ACTION` meta-dataとintent-filter）。対象は
 *    [ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID]でInt値として渡される。
 *    選択完了時に[RESULT_OK]を返さないと、文字盤側はデータソースの選択自体をキャンセル扱いにする。
 * 2. [SesameComplicationDataSourceService]が設定する`tapAction`からの起動。未設定枠の
 *    「タップして設定」と、設定済み枠のデバイス変更の双方で使う。対象は
 *    [EXTRA_COMPLICATION_INSTANCE_ID]で文字列として渡される。
 */
class ComplicationConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val complicationInstanceId = resolveComplicationInstanceId()
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
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            }
        }
    }

    /**
     * 対象のcomplicationInstanceIdを解決する。システム経由の起動（標準の設定導線）を優先し、
     * 無ければtapAction経由で渡された値を使う（BL-073）。
     */
    private fun resolveComplicationInstanceId(): Int? {
        val systemProvidedId =
            intent.getIntExtra(ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID, INVALID_INSTANCE_ID)
        return if (systemProvidedId != INVALID_INSTANCE_ID) {
            systemProvidedId
        } else {
            intent.getStringExtra(EXTRA_COMPLICATION_INSTANCE_ID)?.toIntOrNull()
        }
    }

    private fun requestComplicationUpdate(complicationInstanceId: Int) {
        val componentName = ComponentName(applicationContext, SesameComplicationDataSourceService::class.java)
        ComplicationDataSourceUpdateRequester.create(applicationContext, componentName)
            .requestUpdate(complicationInstanceId)
    }

    companion object {
        const val EXTRA_COMPLICATION_INSTANCE_ID = "complication_instance_id"

        private const val INVALID_INSTANCE_ID = -1

        fun createIntent(
            context: Context,
            complicationInstanceId: Int,
        ): Intent =
            Intent(context, ComplicationConfigurationActivity::class.java).apply {
                putExtra(EXTRA_COMPLICATION_INSTANCE_ID, complicationInstanceId.toString())
            }
    }
}

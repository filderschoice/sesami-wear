package com.sesamiwear.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 文字盤にSesame 5のロック状態を常時表示するComplication。
 * スマホ接続状態・ロック状態は[SesameTileService]と同じ仕組み（[SesameConnectedNodeProvider]・
 * [SesameStatusSnapshotReader]）から取得する（BL-015）。
 * Android Complications APIへの依存のためユニットテスト対象外
 * （表示文言ロジックは[SesameComplicationContent]でテスト済み、実機表示確認はBL-011で人手検証）。
 */
class SesameComplicationDataSourceService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val isPhoneConnected = SesameConnectedNodeProvider.firstConnectedNodeId(applicationContext) != null
            val snapshot = SesameStatusSnapshotReader.readLatest(applicationContext)
            val state =
                TileDisplayStateResolver.resolve(
                    isPhoneConnected = isPhoneConnected,
                    isCommandInProgress = false,
                    isLocked = snapshot?.isLocked,
                )
            listener.onComplicationData(buildComplicationData(state))
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return buildComplicationData(TileDisplayState.LOCKED)
    }

    private fun buildComplicationData(state: TileDisplayState): ComplicationData {
        val text = PlainComplicationText.Builder(SesameComplicationContent.shortText(state)).build()
        return ShortTextComplicationData.Builder(text = text, contentDescription = text).build()
    }
}

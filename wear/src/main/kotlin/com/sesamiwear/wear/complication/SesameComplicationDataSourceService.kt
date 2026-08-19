package com.sesamiwear.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver

/**
 * 文字盤にSesame 5のロック状態を常時表示するComplication。
 * 実データ（スマホ接続状態・ロック状態）との結線はBL-015で行う（現状は常にUNKNOWN状態）。
 * Android Complications APIへの依存のためユニットテスト対象外
 * （表示文言ロジックは[SesameComplicationContent]でテスト済み、実機表示確認はBL-011で人手検証）。
 */
class SesameComplicationDataSourceService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val state =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = false,
                isCommandInProgress = false,
                isLocked = null,
            )
        listener.onComplicationData(buildComplicationData(state))
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

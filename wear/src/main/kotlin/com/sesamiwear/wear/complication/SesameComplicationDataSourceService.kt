package com.sesamiwear.wear.complication

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 文字盤にSesame 5のロック状態を常時表示するComplication。1つのComplicationインスタンス
 * （[ComplicationRequest.complicationInstanceId]）が1台のSesameデバイスに対応する
 * 「複数Complicationインスタンス方式」を採る（BL-054、[com.sesamiwear.wear.tile.SesameTileService]
 * と同型）。対象デバイスは[ComplicationDeviceAssignmentStore]でinstanceIdごとに永続化され、
 * 未設定の場合はタップで[ComplicationConfigurationActivity]へ誘導するtapActionを設定する。
 * スマホ接続状態・ロック状態は[SesameConnectedNodeProvider]・[SesameStatusSnapshotReader]から
 * 取得する（BL-015）。Android Complications APIへの依存のためユニットテスト対象外
 * （表示文言ロジックは[SesameComplicationContent]でテスト済み、実機表示確認はBL-055で人手検証）。
 */
class SesameComplicationDataSourceService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val complicationInstanceId = request.complicationInstanceId
        CoroutineScope(Dispatchers.IO).launch {
            val deviceUuid =
                ComplicationDeviceAssignmentStore(applicationContext).assignedDeviceUuid(complicationInstanceId)
            val data =
                if (deviceUuid == null) {
                    buildUnconfiguredComplicationData(complicationInstanceId)
                } else {
                    buildConfiguredComplicationData(deviceUuid)
                }
            listener.onComplicationData(data)
        }
    }

    private suspend fun buildConfiguredComplicationData(deviceUuid: String): ComplicationData {
        val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(applicationContext)
        val snapshot = SesameStatusSnapshotReader.readLatest(applicationContext, deviceUuid)
        // コマンド実行直後の巻き戻り防止のため、DataItemが一定時間以上古い場合のみ状態取得を
        // リクエストする（BL-061/BL-063、SesameTileServiceと同様の対応）。
        val isSnapshotStale =
            snapshot == null ||
                System.currentTimeMillis() - snapshot.updatedAtEpochMillis > STATUS_STALE_THRESHOLD_MILLIS
        if (nodeId != null && isSnapshotStale) {
            SesameCommandSenderProvider.create(applicationContext).requestStatus(nodeId, deviceUuid)
        }
        val state =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = nodeId != null,
                isCommandInProgress = false,
                isLocked = snapshot?.isLocked,
            )
        return buildComplicationData(SesameComplicationContent.shortText(state))
    }

    private fun buildUnconfiguredComplicationData(complicationInstanceId: Int): ComplicationData {
        val intent = ComplicationConfigurationActivity.createIntent(applicationContext, complicationInstanceId)
        val tapAction =
            PendingIntent.getActivity(
                applicationContext,
                complicationInstanceId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return buildComplicationData(text = "タップして設定", tapAction = tapAction)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return buildComplicationData(SesameComplicationContent.shortText(TileDisplayState.LOCKED))
    }

    private fun buildComplicationData(
        text: String,
        tapAction: PendingIntent? = null,
    ): ComplicationData {
        val complicationText: ComplicationText = PlainComplicationText.Builder(text).build()
        return ShortTextComplicationData.Builder(text = complicationText, contentDescription = complicationText)
            .setTapAction(tapAction)
            .build()
    }

    private companion object {
        const val STATUS_STALE_THRESHOLD_MILLIS = 30_000L
    }
}

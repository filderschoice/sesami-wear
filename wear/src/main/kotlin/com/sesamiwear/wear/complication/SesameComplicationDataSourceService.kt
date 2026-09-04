package com.sesamiwear.wear.complication

import android.app.PendingIntent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.tile.SesameTileStateResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 文字盤にSesame 5のロック状態を常時表示するComplication。1つのComplicationインスタンス
 * （[ComplicationRequest.complicationInstanceId]）が1台のSesameデバイスに対応する
 * 「複数Complicationインスタンス方式」を採る（BL-054、[com.sesamiwear.wear.tile.SesameTileService]
 * と同型）。対象デバイスは[ComplicationDeviceAssignmentStore]でinstanceIdごとに永続化され、
 * 未設定の場合はタップで[ComplicationConfigurationActivity]へ誘導するtapActionを設定する。
 * スマホ接続状態・ロック状態は[SesameConnectedNodeProvider]・[SesameTileStateResolver]から
 * 取得する（BL-015）。対象デバイスuuidが`SesameWearProtocol.ALL_DEVICES_TARGET_UUID`
 * （「全デバイス」選択）の場合は登録済み全デバイスの状態を集約表示する（BL-071、複数デバイス
 * 一括操作。Complicationは設定済み状態ではtapActionを持たない読み取り専用表示のため、
 * 集約表示のみでコマンド送信は行わない）。
 *
 * 状態文言が文字盤へ一切表示されない不具合（BL-072）への対応として、以下3点を実装している。
 * いずれも「システムへデータを返せない状態」を作らないための防御的な措置であり、原因を実機ログで
 * 特定できるよう[Log]も併せて出力する。
 * 1. 対応する[ComplicationType]に`LONG_TEXT`を追加し、要求された型に応じてデータを作り分ける
 *    （`SHORT_TEXT`しか返せないと、`LONG_TEXT`枠へ配置された場合に型不一致でデータが破棄される）。
 * 2. 状態解決（Wearable API呼び出し）を[withTimeout]で打ち切り、例外・タイムアウト時も
 *    「不明」表示のデータを返す（従来は例外時に`listener.onComplicationData`が呼ばれず空欄のまま
 *    だった）。
 * 3. マニフェストの`UPDATE_PERIOD_SECONDS`を定期更新ありに変更し、更新要求が届かない場合でも
 *    次の定期更新で表示が回復するようにする。
 *
 * Android Complications APIへの依存のためユニットテスト対象外（表示文言ロジックは
 * [SesameComplicationContent]でテスト済み、実機表示確認はBL-072/BL-055で人手検証）。
 */
class SesameComplicationDataSourceService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val complicationInstanceId = request.complicationInstanceId
        val type = request.complicationType
        Log.d(TAG, "onComplicationRequest id=$complicationInstanceId type=$type")
        CoroutineScope(Dispatchers.IO).launch {
            val data = resolveComplicationData(complicationInstanceId, type)
            Log.d(TAG, "onComplicationRequest id=$complicationInstanceId hasData=${data != null}")
            listener.onComplicationData(data)
        }
    }

    /**
     * 要求された[type]に対して返すデータを決める。対応外の型のみ`null`（データなし）を返し、
     * それ以外は必ず表示可能なデータを返す。状態解決に失敗した場合も、空欄のままにせず
     * 「不明」表示へフォールバックする（BL-072）。
     */
    private suspend fun resolveComplicationData(
        complicationInstanceId: Int,
        type: ComplicationType,
    ): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT && type != ComplicationType.LONG_TEXT) {
            Log.w(TAG, "unsupported complication type=$type id=$complicationInstanceId")
            return null
        }
        val deviceUuid =
            ComplicationDeviceAssignmentStore(applicationContext).assignedDeviceUuid(complicationInstanceId)
        return if (deviceUuid == null) {
            buildUnconfiguredComplicationData(complicationInstanceId, type)
        } else {
            runCatching {
                withTimeout(RESOLVE_TIMEOUT_MILLIS) { buildConfiguredComplicationData(deviceUuid, type) }
            }.getOrElse { error ->
                Log.w(TAG, "failed to resolve state id=$complicationInstanceId", error)
                buildComplicationData(type, SesameComplicationContent.shortText(TileDisplayState.UNKNOWN))
            }
        }
    }

    private suspend fun buildConfiguredComplicationData(
        deviceUuid: String,
        type: ComplicationType,
    ): ComplicationData {
        val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(applicationContext)
        val state = SesameTileStateResolver.resolveState(applicationContext, deviceUuid, nodeId)
        Log.d(TAG, "buildConfiguredComplicationData nodeId=${nodeId != null} state=$state")
        val text =
            if (type == ComplicationType.LONG_TEXT) {
                val displayName = SesameTileStateResolver.resolveDisplayName(applicationContext, deviceUuid)
                SesameComplicationContent.longText(displayName, state)
            } else {
                SesameComplicationContent.shortText(state)
            }
        return buildComplicationData(type, text)
    }

    private fun buildUnconfiguredComplicationData(
        complicationInstanceId: Int,
        type: ComplicationType,
    ): ComplicationData {
        val intent = ComplicationConfigurationActivity.createIntent(applicationContext, complicationInstanceId)
        val tapAction =
            PendingIntent.getActivity(
                applicationContext,
                complicationInstanceId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return buildComplicationData(type, text = UNCONFIGURED_TEXT, tapAction = tapAction)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                buildComplicationData(type, SesameComplicationContent.shortText(TileDisplayState.LOCKED))

            ComplicationType.LONG_TEXT ->
                buildComplicationData(
                    type,
                    SesameComplicationContent.longText(PREVIEW_DISPLAY_NAME, TileDisplayState.LOCKED),
                )

            else -> null
        }

    private fun buildComplicationData(
        type: ComplicationType,
        text: String,
        tapAction: PendingIntent? = null,
    ): ComplicationData {
        val complicationText: ComplicationText = PlainComplicationText.Builder(text).build()
        return if (type == ComplicationType.LONG_TEXT) {
            LongTextComplicationData.Builder(text = complicationText, contentDescription = complicationText)
                .setTapAction(tapAction)
                .build()
        } else {
            ShortTextComplicationData.Builder(text = complicationText, contentDescription = complicationText)
                .setTapAction(tapAction)
                .build()
        }
    }

    private companion object {
        const val TAG = "SesameComplication"

        // 未設定時にタップで設定画面へ誘導する文言。SHORT_TEXT枠の表示可能文字数（7文字程度）に収める。
        const val UNCONFIGURED_TEXT = "タップして設定"

        // Complicationピッカーのプレビューで使うダミーのデバイス名（実データは参照しない）。
        const val PREVIEW_DISPLAY_NAME = "玄関"

        // 状態解決（Wearable APIの往復）を打ち切る上限。Complicationのデータは
        // onComplicationRequestから概ね20秒以内に返す必要があるため、余裕を持って短めに設定する。
        const val RESOLVE_TIMEOUT_MILLIS = 10_000L
    }
}

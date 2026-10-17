package com.sesamiwear.wear.complication

import android.app.PendingIntent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
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
 * 未設定・設定済みのいずれもタップで[ComplicationConfigurationActivity]へ誘導するtapActionを
 * 設定する（設定済み枠のデバイス変更導線、BL-073）。
 * スマホ接続状態・ロック状態は[SesameConnectedNodeProvider]・[SesameTileStateResolver]から
 * 取得する（BL-015）。対象デバイスuuidが`SesameWearProtocol.ALL_DEVICES_TARGET_UUID`
 * （「全デバイス」選択）の場合は登録済み全デバイスの状態を集約表示する（BL-071、複数デバイス
 * 一括操作。Complicationのタップは設定画面を開くだけで施錠/解錠のコマンド送信は行わないため、
 * 集約表示のみとする。操作導線はTile側に限定する）。
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
 * 文字の入らない小さな枠向けに、アイコンだけの型（`MONOCHROMATIC_IMAGE` / `SMALL_IMAGE`）と、電池残量を
 * ゲージで出す型（`RANGED_VALUE`、ゲージの中央に状態のアイコン）にも対応する（BL-256）。
 * どの型でも通信は増えない（同じスナップショットから作り分けるだけ）。
 *
 * Android Complications APIへの依存のためユニットテスト対象外（表示文言ロジックは
 * [SesameComplicationContent]でテスト済み、実機表示はBL-072/BL-055の人手検証で2026-09-05に
 * 確認済み）。
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
        if (type !in SUPPORTED_TYPES) {
            Log.w(TAG, "unsupported complication type=$type id=$complicationInstanceId")
            return null
        }
        val deviceUuid =
            ComplicationDeviceAssignmentStore(applicationContext).assignedDeviceUuid(complicationInstanceId)
        return if (deviceUuid == null) {
            buildUnconfiguredComplicationData(complicationInstanceId, type)
        } else {
            runCatching {
                withTimeout(RESOLVE_TIMEOUT_MILLIS) {
                    buildConfiguredComplicationData(complicationInstanceId, deviceUuid, type)
                }
            }.getOrElse { error ->
                Log.w(TAG, "failed to resolve state id=$complicationInstanceId", error)
                buildComplicationData(
                    type,
                    SesameComplicationContent.shortText(TileDisplayState.UNKNOWN),
                    TileDisplayState.UNKNOWN,
                    tapAction = configurationTapAction(complicationInstanceId),
                )
            }
        }
    }

    private suspend fun buildConfiguredComplicationData(
        complicationInstanceId: Int,
        deviceUuid: String,
        type: ComplicationType,
    ): ComplicationData {
        val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(applicationContext)
        val status = SesameTileStateResolver.resolveStatus(applicationContext, deviceUuid, nodeId)
        Log.d(TAG, "buildConfiguredComplicationData nodeId=${nodeId != null} state=${status.state}")
        // SHORT_TEXT以外（LONG_TEXTと画像・ゲージの型の読み上げ文言）はデバイス名も含める。
        val text =
            if (type != ComplicationType.SHORT_TEXT) {
                val displayName = SesameTileStateResolver.resolveDisplayName(applicationContext, deviceUuid)
                SesameComplicationContent.longText(displayName, status.state, status.detailLabelWithRouteIcon)
            } else {
                SesameComplicationContent.shortText(status.state)
            }
        return buildComplicationData(
            type,
            text,
            status.state,
            status.batteryPercentage,
            configurationTapAction(complicationInstanceId),
        )
    }

    private fun buildUnconfiguredComplicationData(
        complicationInstanceId: Int,
        type: ComplicationType,
    ): ComplicationData =
        buildComplicationData(
            type,
            text = UNCONFIGURED_TEXT,
            state = TileDisplayState.UNKNOWN,
            tapAction = configurationTapAction(complicationInstanceId),
        )

    /**
     * タップで[ComplicationConfigurationActivity]を開く[PendingIntent]。未設定枠だけでなく設定済み枠へも
     * 付与し、枠ごとの対象デバイスをあとから変更できるようにする（BL-073）。施錠/解錠のコマンド送信は
     * 行わない（Complicationは読み取り専用表示とし、操作導線はTile側に限定する方針を維持する）。
     */
    private fun configurationTapAction(complicationInstanceId: Int): PendingIntent {
        val intent = ComplicationConfigurationActivity.createIntent(applicationContext, complicationInstanceId)
        return PendingIntent.getActivity(
            applicationContext,
            complicationInstanceId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                buildComplicationData(
                    type,
                    SesameComplicationContent.shortText(TileDisplayState.LOCKED),
                    TileDisplayState.LOCKED,
                )

            in SUPPORTED_TYPES ->
                buildComplicationData(
                    type,
                    SesameComplicationContent.longText(PREVIEW_DISPLAY_NAME, TileDisplayState.LOCKED),
                    TileDisplayState.LOCKED,
                    PREVIEW_BATTERY_PERCENTAGE,
                )

            else -> null
        }

    private fun buildComplicationData(
        type: ComplicationType,
        text: String,
        state: TileDisplayState,
        batteryPercentage: Int? = null,
        tapAction: PendingIntent? = null,
    ): ComplicationData =
        SesameComplicationDataBuilder(applicationContext).build(type, text, state, batteryPercentage, tapAction)

    private companion object {
        const val TAG = "SesameComplication"

        // 未設定時にタップで設定画面へ誘導する文言。SHORT_TEXT枠の表示可能文字数（7文字程度）に収める。
        const val UNCONFIGURED_TEXT = "タップして設定"

        // Complicationピッカーのプレビューで使うダミーのデバイス名（実データは参照しない）。
        const val PREVIEW_DISPLAY_NAME = "玄関"

        // Complicationピッカーのプレビューで使うダミーの電池残量（RANGED_VALUE、BL-256）。
        const val PREVIEW_BATTERY_PERCENTAGE = 85

        // 対応する型。マニフェストのSUPPORTED_TYPESと揃える（BL-072でLONG_TEXT、BL-256で画像とゲージを追加）。
        val SUPPORTED_TYPES =
            setOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.LONG_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE,
                ComplicationType.RANGED_VALUE,
            )

        // 状態解決（Wearable APIの往復）を打ち切る上限。Complicationのデータは
        // onComplicationRequestから概ね20秒以内に返す必要があるため、余裕を持って短めに設定する。
        const val RESOLVE_TIMEOUT_MILLIS = 10_000L
    }
}

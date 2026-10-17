package com.sesamiwear.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.wear.R

/**
 * [SesameComplicationDataSourceService]が返す[ComplicationData]を、要求された型ごとに組み立てる。
 * 文字の型（`SHORT_TEXT` / `LONG_TEXT`）に加え、アイコンだけの型（`MONOCHROMATIC_IMAGE` / `SMALL_IMAGE`）と
 * 電池残量のゲージ（`RANGED_VALUE`）を作る（BL-256）。サービス本体の関数が増えすぎないよう分けた。
 *
 * Android Complications APIへの依存のためユニットテスト対象外（文言・アイコンの種類・ゲージの値は
 * [SesameComplicationContent]でテスト済み）。
 */
internal class SesameComplicationDataBuilder(
    private val context: Context,
) {
    /**
     * 要求された[type]のデータを作る。[text]は文字の型では表示する文言、画像・ゲージの型では読み上げ文言。
     * [state]は画像・ゲージの型のアイコン、[batteryPercentage]は`RANGED_VALUE`のゲージに使う（BL-256）。
     */
    fun build(
        type: ComplicationType,
        text: String,
        state: TileDisplayState,
        batteryPercentage: Int? = null,
        tapAction: PendingIntent? = null,
    ): ComplicationData {
        val complicationText: ComplicationText = plainText(text)
        return when (type) {
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text = complicationText, contentDescription = complicationText)
                    .setTapAction(tapAction)
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(monochromaticImageOf(state), complicationText)
                    .setTapAction(tapAction)
                    .build()

            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(stateIconOf(state), SmallImageType.ICON).build(),
                    complicationText,
                ).setTapAction(tapAction)
                    .build()

            ComplicationType.RANGED_VALUE -> buildBatteryGaugeData(text, state, batteryPercentage, tapAction)

            else ->
                ShortTextComplicationData.Builder(text = complicationText, contentDescription = complicationText)
                    .setTapAction(tapAction)
                    .build()
        }
    }

    /** 電池残量のゲージ（`RANGED_VALUE`）。ゲージの中央に状態のアイコン、下に「85%」を出す（BL-256）。 */
    private fun buildBatteryGaugeData(
        text: String,
        state: TileDisplayState,
        batteryPercentage: Int?,
        tapAction: PendingIntent?,
    ): ComplicationData {
        val gauge = SesameComplicationContent.batteryGauge(batteryPercentage)
        return RangedValueComplicationData.Builder(
            value = gauge.value,
            min = SesameComplicationContent.BATTERY_MIN_VALUE,
            max = SesameComplicationContent.BATTERY_MAX_VALUE,
            contentDescription = plainText(SesameComplicationContent.batteryDescription(text, batteryPercentage)),
        ).setText(plainText(gauge.text))
            .setMonochromaticImage(monochromaticImageOf(state))
            .setTapAction(tapAction)
            .build()
    }

    private fun plainText(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

    private fun monochromaticImageOf(state: TileDisplayState): MonochromaticImage =
        MonochromaticImage.Builder(stateIconOf(state)).build()

    private fun stateIconOf(state: TileDisplayState): Icon {
        val resId =
            when (SesameComplicationContent.stateIcon(state)) {
                SesameComplicationContent.StateIcon.LOCKED -> R.drawable.ic_state_locked
                SesameComplicationContent.StateIcon.UNLOCKED -> R.drawable.ic_state_unlocked
                SesameComplicationContent.StateIcon.IN_PROGRESS -> R.drawable.ic_state_in_progress
                SesameComplicationContent.StateIcon.DISCONNECTED -> R.drawable.ic_state_disconnected
                SesameComplicationContent.StateIcon.UNKNOWN -> R.drawable.ic_state_unknown
            }
        return Icon.createWithResource(context, resId)
    }
}

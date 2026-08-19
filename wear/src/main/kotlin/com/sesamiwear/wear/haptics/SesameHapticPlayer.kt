package com.sesamiwear.wear.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * [HapticPattern] をVibrator APIで再生する薄いアダプタ。Android依存のためユニットテスト対象外
 * （パターン選択ロジックは[SesameHapticPatternResolver]でテスト済み、実機での体感確認はBL-011で人手検証）。
 */
class SesameHapticPlayer(context: Context) {
    private val vibrator: Vibrator = resolveVibrator(context)

    fun play(pattern: HapticPattern) {
        vibrator.vibrate(effectFor(pattern))
    }

    private fun effectFor(pattern: HapticPattern): VibrationEffect =
        when (pattern) {
            HapticPattern.SUCCESS -> VibrationEffect.createWaveform(SUCCESS_TIMINGS_MS, NO_REPEAT)
            HapticPattern.FAILURE -> VibrationEffect.createWaveform(FAILURE_TIMINGS_MS, NO_REPEAT)
        }

    private companion object {
        val SUCCESS_TIMINGS_MS = longArrayOf(0, 80, 40, 80)
        val FAILURE_TIMINGS_MS = longArrayOf(0, 200)
        const val NO_REPEAT = -1

        fun resolveVibrator(context: Context): Vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
    }
}

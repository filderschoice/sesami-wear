package com.sesamiwear.mobile.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sesamiwear.core.haptics.HapticPattern

/**
 * [HapticPattern] をVibrator APIで再生する薄いアダプタ（BL-129）。
 *
 * ホーム画面ウィジェットの操作結果を、画面の表示だけでなく振動でも伝える。wearのTile経由の操作
 * （BL-011）と同じ手触りにするため、波形は`core.haptics.HapticPattern`が定義する共通の値を使う。
 * mobileはwearへ依存できないため、Android依存のこのアダプタだけをwear側と同じ実装で持つ。
 * Android依存のためユニットテスト対象外（どのパターンを鳴らすかの判定は
 * `com.sesamiwear.mobile.widget.WidgetHapticResolver`でテスト済み）。
 */
class SesameHapticPlayer(context: Context) {
    private val vibrator: Vibrator = resolveVibrator(context)

    fun play(pattern: HapticPattern) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern.timingsMillis.toLongArray(), NO_REPEAT))
    }

    private companion object {
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

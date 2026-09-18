package com.sesamiwear.mobile.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
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
 *
 * 振動の用途（[VibrationAttributes]）を必ず指定する（BL-156）。ウィジェットの操作はアプリが
 * バックグラウンドのまま実行されるため、用途が`USAGE_UNKNOWN`だとシステムが振動を破棄する
 * （`VibratorManagerService: Ignoring incoming vibration as process ... is background`）。
 * バックグラウンドからの振動が許可される用途のうち、利用者のタップに対する手応えという意味に
 * 最も近い`USAGE_HARDWARE_FEEDBACK`を使う（通知音・着信音の設定やサイレントモードに左右されず、
 * 触覚フィードバックの設定にのみ従う）。用途を指定できるオーバーロードはAndroid 13（API 33）
 * からのため、それ未満では[AudioAttributes]版を使う（`USAGE_NOTIFICATION`へ写像され、これも
 * バックグラウンドからの振動が許可される用途になる。`USAGE_HARDWARE_FEEDBACK`へ写像できる
 * [AudioAttributes]の用途は存在しない）。
 * wear側（`wear.haptics.SesameHapticPlayer`）は前面のActivityから鳴らすため用途の指定が不要で、
 * 触覚フィードバックの設定による差が出ないよう従来どおりの実装を維持する。
 */
class SesameHapticPlayer(context: Context) {
    private val vibrator: Vibrator = resolveVibrator(context)

    fun play(pattern: HapticPattern) {
        val effect = VibrationEffect.createWaveform(pattern.timingsMillis.toLongArray(), NO_REPEAT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, backgroundAllowedAudioAttributes())
        }
    }

    private companion object {
        const val NO_REPEAT = -1

        /** Android 13未満で用途を伝えるための[AudioAttributes]（`USAGE_NOTIFICATION`へ写像される）。 */
        fun backgroundAllowedAudioAttributes(): AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

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

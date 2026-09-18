package com.sesamiwear.core.haptics

/**
 * 施錠/解錠結果を画面を見なくても区別できるようにするハプティクスパターン。
 *
 * wearのTile経由の操作（BL-011）とmobileのホーム画面ウィジェット経由の操作（BL-129）で
 * 同じ手触りにするため、波形の定義をcoreへ置く（`SesameTileContent`と同方針、BL-119。
 * mobileはwearへ依存できないため、共有先はcoreしかない）。
 * 実際の再生は各モジュールのAndroid依存アダプタ（`SesameHapticPlayer`）が行う。
 *
 * @property timingsMillis `VibrationEffect.createWaveform`へ渡す時間の並び。
 * 先頭は待機時間で、以降は振動・待機を交互に表す。成功は短い2回、失敗は長い1回にして、
 * 画面を見なくても区別できるようにする。
 */
enum class HapticPattern(val timingsMillis: List<Long>) {
    SUCCESS(listOf(0L, 80L, 40L, 80L)),
    FAILURE(listOf(0L, 200L)),
}

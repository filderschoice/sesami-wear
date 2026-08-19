package com.sesamiwear.wear.haptics

/**
 * 施錠/解錠結果を画面を見なくても区別できるようにするハプティクスパターン。
 * 実際の振動波形は[SesameHapticPlayer]（Android依存アダプタ）が定義する。
 */
enum class HapticPattern {
    SUCCESS,
    FAILURE,
}

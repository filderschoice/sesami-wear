package com.sesamiwear.core

/**
 * セサミの電池電圧から残量（%）を求める換算表（BL-166）。
 *
 * 値は`meronepy/gomalock`（MIT）の`VOLTAGE_LEVELS` / `BATTERY_PERCENTAGES`と同じで、
 * 公式アプリの表示に合わせた非線形な対応になっている。区間ごとに線形補間する。
 *
 * **電池残量はBLE専用の情報ではない。** Sesame Web APIの状態取得レスポンス
 * （`core.api.SesameStatus`）も`batteryVoltage`を返すため、経路によらず同じ換算で残量を出せる。
 * もとは`mobile.ble.SesameBleMechStatus`が持っていたが、Web API経由でも使うため`core`へ移した。
 *
 * Android非依存のためユニットテスト対象。
 */
object SesameBatteryLevel {
    /**
     * 電圧と残量の対応表（降順）。[VOLTAGE_LEVELS]の各要素に[BATTERY_PERCENTAGES]の同じ添字が対応する。
     */
    private val VOLTAGE_LEVELS =
        doubleArrayOf(5.85, 5.82, 5.79, 5.76, 5.73, 5.70, 5.65, 5.60, 5.55, 5.50, 5.40, 5.20, 5.10, 5.0, 4.8, 4.6)

    private val BATTERY_PERCENTAGES =
        doubleArrayOf(100.0, 95.0, 90.0, 85.0, 80.0, 70.0, 60.0, 50.0, 40.0, 32.0, 21.0, 13.0, 10.0, 7.0, 3.0, 0.0)

    /**
     * [voltage]（V）に対応する残量（%、0〜100）。
     * 表の範囲外は上下限へ丸める（新品直後に上限を超える値が来ても100%として扱う）。
     */
    fun percentageOf(voltage: Double): Int {
        val clamped = voltage.coerceIn(VOLTAGE_LEVELS.last(), VOLTAGE_LEVELS.first())
        val index =
            (0 until VOLTAGE_LEVELS.size - 1).firstOrNull { i ->
                clamped > VOLTAGE_LEVELS[i + 1] && clamped <= VOLTAGE_LEVELS[i]
            }
        return if (index == null) {
            BATTERY_PERCENTAGES.last().toInt()
        } else {
            interpolate(clamped, index)
        }
    }

    private fun interpolate(
        voltage: Double,
        index: Int,
    ): Int {
        val upper = VOLTAGE_LEVELS[index]
        val lower = VOLTAGE_LEVELS[index + 1]
        val ratio = (voltage - lower) / (upper - lower)
        return (
            (BATTERY_PERCENTAGES[index] - BATTERY_PERCENTAGES[index + 1]) * ratio +
                BATTERY_PERCENTAGES[index + 1]
        ).toInt()
    }
}

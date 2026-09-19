package com.sesamiwear.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 電池電圧から残量（%）への換算の検証（BL-166）。
 *
 * 換算表は`meronepy/gomalock`（MIT）と同じ値で、境界と補間の結果を固定する。
 * BLE経由（機構状態の生値から算出）とWeb API経由（レスポンスの`batteryVoltage`）の
 * どちらも同じ換算を通るため、経路によって表示がずれないことをここで担保する。
 */
class SesameBatteryLevelTest {
    @Test
    fun `the top of the table is 100 percent`() {
        assertEquals(100, SesameBatteryLevel.percentageOf(5.85))
    }

    @Test
    fun `a voltage above the table is clamped to 100 percent`() {
        assertEquals(100, SesameBatteryLevel.percentageOf(6.0))
    }

    @Test
    fun `the bottom of the table is 0 percent`() {
        assertEquals(0, SesameBatteryLevel.percentageOf(4.6))
    }

    @Test
    fun `a voltage below the table is clamped to 0 percent`() {
        assertEquals(0, SesameBatteryLevel.percentageOf(3.0))
    }

    @Test
    fun `values between two levels are interpolated`() {
        // 5.79V=90% と 5.82V=95% の間。5.80Vは (5.80-5.79)/(5.82-5.79)=1/3 で約91.66% → 91。
        assertEquals(91, SesameBatteryLevel.percentageOf(5.8))
    }

    @Test
    fun `each table boundary returns its own percentage`() {
        assertEquals(95, SesameBatteryLevel.percentageOf(5.82))
        assertEquals(90, SesameBatteryLevel.percentageOf(5.79))
        assertEquals(50, SesameBatteryLevel.percentageOf(5.60))
        assertEquals(21, SesameBatteryLevel.percentageOf(5.40))
        assertEquals(7, SesameBatteryLevel.percentageOf(5.0))
    }

    @Test
    fun `the result never leaves the 0 to 100 range`() {
        val samples = generateSequence(4.0) { it + 0.01 }.takeWhile { it <= 6.0 }
        samples.forEach { voltage ->
            val percentage = SesameBatteryLevel.percentageOf(voltage)
            assert(percentage in 0..100) { "voltage=$voltage percentage=$percentage" }
        }
    }
}

package com.sesamiwear.core.ble

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * RSSIの揺れを抑える指数移動平均（BL-271）。
 *
 * 広告1回ごとのRSSIは数dBから十数dB揺れるため、そのまま距離へ変換するとブリップが跳ね回る。
 * 直近の値を重く見つつ、大きく変わったとき（Sesameへ近づいた・離れた）は追従を速める。
 * 状態を持つので、確認1回につき1つ作る。Android非依存のためユニットテスト対象。
 */
class SesameBleRssiSmoother(
    private val alpha: Double = DEFAULT_ALPHA,
    private val fastAlpha: Double = FAST_ALPHA,
) {
    private var current: Double? = null

    /** 平滑化後のRSSI（dBm、四捨五入）。まだ値が無ければnull。 */
    val value: Int? get() = current?.roundToInt()

    /** 観測値[rssiDbm]を取り込み、平滑化後の値を返す。 */
    fun add(rssiDbm: Int): Int {
        val previous = current
        val next =
            if (previous == null) {
                rssiDbm.toDouble()
            } else {
                val weight = if (abs(rssiDbm - previous) >= JUMP_DB) fastAlpha else alpha
                previous + weight * (rssiDbm - previous)
            }
        current = next
        return next.roundToInt()
    }

    /** 蓄えた値を捨てる（見失ったあと、古い値へ引きずられないようにする）。 */
    fun reset() {
        current = null
    }

    companion object {
        /** 通常の追従の速さ。 */
        const val DEFAULT_ALPHA = 0.3

        /** 大きく変わったときの追従の速さ。 */
        const val FAST_ALPHA = 0.7

        /** 「大きく変わった」とみなすRSSIの差（dB）。 */
        const val JUMP_DB = 12
    }
}

package com.sesamiwear.mobile.dial

import com.sesamiwear.core.api.SesameCommand
import kotlin.math.abs
import kotlin.math.atan2

/**
 * ダイヤル操作（BL-243）の円形スワイプの判定。Android非依存のためユニットテスト対象。
 *
 * 指がダイヤルの中心の周りを回った角度を積み上げ、どちら回りでも[TRIGGER_DEGREES]以上回したら操作を確定する。
 * 回す向きで施錠/解錠を分けないのは、サムターンが回る向きが扉への取り付け方で変わり、アプリからは
 * どちら回りが施錠なのか分からないため。確定したときに送るコマンドは、そのときの状態の反対
 * （施錠中なら解錠、解錠中なら施錠）とする（[commandFor]）。
 *
 * 一定以上回す操作そのものを意図の確認とみなし、解錠でも確認ダイアログを挟まない（BL-243の根拠）。
 */
class DialSweep {
    private var lastAngle: Double? = null

    /** 指を置いてから回した角度（度）。時計回りが正、反時計回りが負。 */
    var sweptDegrees: Double = 0.0
        private set

    /** どちら回りでも[TRIGGER_DEGREES]以上回したか。 */
    val isTriggered: Boolean get() = abs(sweptDegrees) >= TRIGGER_DEGREES

    /** 進み具合（0〜1）。ダイヤルの円弧の表示に使う。 */
    val progress: Float get() = (abs(sweptDegrees) / TRIGGER_DEGREES).coerceAtMost(1.0).toFloat()

    /** 指を置いた。中心からの位置（画面座標。yは下向きが正）を渡す。 */
    fun start(
        dx: Float,
        dy: Float,
    ) {
        lastAngle = angleOf(dx, dy)
        sweptDegrees = 0.0
    }

    /** 指を動かした。前回の位置からの最短の回転量を積み上げる。 */
    fun move(
        dx: Float,
        dy: Float,
    ) {
        val previous = lastAngle ?: return start(dx, dy)
        val current = angleOf(dx, dy)
        sweptDegrees += shortestDelta(previous, current)
        lastAngle = current
    }

    /** 指を離した・取り消した。 */
    fun reset() {
        lastAngle = null
        sweptDegrees = 0.0
    }

    companion object {
        /** 操作を確定させるのに要る回転量（度）。誤って触れたくらいでは届かない大きさにする。 */
        const val TRIGGER_DEGREES = 150.0

        private const val FULL_TURN = 360.0
        private const val HALF_TURN = 180.0

        /** 中心からの位置を、真上を0度とした時計回りの角度（0以上360未満）へ変える。 */
        fun angleOf(
            dx: Float,
            dy: Float,
        ): Double = normalize(Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())))

        /** [from]から[to]への最短の回転量（-180より大きく180以下）。 */
        fun shortestDelta(
            from: Double,
            to: Double,
        ): Double {
            val delta = normalize(to - from)
            return if (delta > HALF_TURN) delta - FULL_TURN else delta
        }

        /**
         * セサミが返す角度（`position`）を、ダイヤルの針の向き（0以上360未満の度）へ変える。
         * 実測（DESIGN.md、施錠-180・解錠-270）では施錠と解錠の差が90で、サムターンの回転量と
         * 合うため度として扱う（BL-243）。
         */
        fun pointerDegreesOf(position: Int): Double = normalize(position.toDouble())

        /** 確定したときに送るコマンド。状態が分からなければnull（何も送らない）。 */
        fun commandFor(isLocked: Boolean?): SesameCommand? =
            when (isLocked) {
                true -> SesameCommand.UNLOCK
                false -> SesameCommand.LOCK
                null -> null
            }

        private fun normalize(degrees: Double): Double = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
    }
}

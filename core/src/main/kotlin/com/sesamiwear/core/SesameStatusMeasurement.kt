package com.sesamiwear.core

/**
 * 施錠/解錠・状態取得で分かった、施錠状態以外の実測値（BL-166）。
 *
 * すべて任意。分からなかった項目はnullのままにし、保存側（`mobile.state.LockStateStore`）は
 * nullの項目について**前回の値を残す**。Sesame Web API経由の施錠/解錠は状態を返さないため、
 * 電池残量・角度はnullで届く。BLE経由の場合はログイン直後に届く機構状態から両方とも分かる。
 *
 * 「電池切れ間近」フラグ（BLEのみ取得可能）は持たない。残量（%）があれば表示の判断には足り、
 * スナップショットの項目数を増やすと引数の数の上限（detektの`LongParameterList`）に触れるため。
 */
data class SesameStatusMeasurement(
    /** 電池残量（%、0〜100）。電圧からの換算は[SesameBatteryLevel]。 */
    val batteryPercentage: Int? = null,
    /** サムターンの角度。セサミが返す生の値をそのまま持つ。 */
    val position: Int? = null,
    /** この値が分かった経路。 */
    val route: SesameStatusRoute? = null,
) {
    /** 何も分かっていない（保存しても前回の値が変わらない）か。 */
    val isEmpty: Boolean get() = batteryPercentage == null && position == null && route == null

    companion object {
        /** 経路だけが分かっている場合（Web API経由の施錠/解錠など）。 */
        fun ofRoute(route: SesameStatusRoute): SesameStatusMeasurement = SesameStatusMeasurement(route = route)
    }
}

/**
 * 状態取得の結果（BL-166）。施錠状態と、同時に分かった実測値をまとめて運ぶ。
 * 取得できなかった場合は呼び出し側がnullを使う。
 */
data class SesameStatusReading(
    val isLocked: Boolean,
    val measurement: SesameStatusMeasurement,
)

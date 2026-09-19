package com.sesamiwear.core

/**
 * DataClient（DataItem）でMobile側からWear側へ同期する、ロック状態のスナップショット。
 *
 * @property isLocked 最後に分かった施錠状態。nullは「まだ一度も分かっていない」ことを表す。
 * @property updatedAtEpochMillis [isLocked]が分かった時刻。[isLocked]がnullならnull。
 * @property lastFailure 直近の状態取得・施錠/解錠が失敗していればその分類、成功していればnull（BL-140）。
 * 失敗しても[isLocked]は最後に分かった値のまま残す（BL-142で「最後に分かった状態を出し続ける」
 * 設計にしたことと揃える）。
 * @property batteryPercentage 最後に分かった電池残量（%）。未取得ならnull（BL-166）。
 * 経路によらず分かる（Sesame Web APIの状態取得も電圧を返す）。
 * @property position 最後に分かったサムターンの角度。未取得ならnull（BL-166）。
 * @property lastRoute 最後に施錠/解錠・状態取得を実行した経路。不明ならnull（BL-166 / BL-168）。
 */
data class SesameStatusSnapshot(
    val isLocked: Boolean?,
    val updatedAtEpochMillis: Long?,
    val lastFailure: SesameStatusFailure? = null,
    val batteryPercentage: Int? = null,
    val position: Int? = null,
    val lastRoute: SesameStatusRoute? = null,
) {
    /**
     * [measurement]で分かった値だけを上書きした写しを返す（BL-166）。
     * nullの項目は前回の値を残す。Web API経由の施錠/解錠は状態を返さないため、
     * 電池残量・角度は前回の値のまま経路だけが更新される。
     */
    fun merge(measurement: SesameStatusMeasurement): SesameStatusSnapshot =
        copy(
            batteryPercentage = measurement.batteryPercentage ?: batteryPercentage,
            position = measurement.position ?: position,
            lastRoute = measurement.route ?: lastRoute,
        )
}

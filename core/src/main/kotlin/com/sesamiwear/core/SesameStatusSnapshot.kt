package com.sesamiwear.core

/**
 * DataClient（DataItem）でMobile側からWear側へ同期する、ロック状態のスナップショット。
 *
 * @property isLocked 最後に分かった施錠状態。nullは「まだ一度も分かっていない」ことを表す。
 * @property updatedAtEpochMillis [isLocked]が分かった時刻。[isLocked]がnullならnull。
 * @property lastFailure 直近の状態取得・施錠/解錠が失敗していればその分類、成功していればnull（BL-140）。
 * 失敗しても[isLocked]は最後に分かった値のまま残す（BL-142で「最後に分かった状態を出し続ける」
 * 設計にしたことと揃える）。
 */
data class SesameStatusSnapshot(
    val isLocked: Boolean?,
    val updatedAtEpochMillis: Long?,
    val lastFailure: SesameStatusFailure? = null,
)

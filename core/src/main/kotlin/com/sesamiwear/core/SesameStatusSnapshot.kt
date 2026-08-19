package com.sesamiwear.core

/**
 * DataClient（DataItem）でMobile側からWear側へ同期する、ロック状態のスナップショット。
 * isLockedがnull（DataItem自体が未作成）の場合は「まだ一度も同期されていない」ことを表す。
 */
data class SesameStatusSnapshot(
    val isLocked: Boolean?,
    val updatedAtEpochMillis: Long,
)

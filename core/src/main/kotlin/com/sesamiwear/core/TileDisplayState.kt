package com.sesamiwear.core

/**
 * Wear OS Tileが表示すべき状態。
 * PLAN.mdのUX要件（ロック状態のアイコン・色表示、通信中表示、スマホ未接続時の明示）に対応する。
 */
enum class TileDisplayState {
    LOCKED,
    UNLOCKED,

    /**
     * 複数デバイス一括操作（BL-071、「全デバイス」選択時）で、施錠中/解錠中のデバイスが
     * 混在している状態。
     */
    MIXED,
    IN_PROGRESS,
    DISCONNECTED,
    UNKNOWN,
    ;

    val isActionable: Boolean
        get() = this == LOCKED || this == UNLOCKED || this == MIXED
}

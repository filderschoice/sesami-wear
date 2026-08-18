package com.sesamiwear.core

/**
 * Wear OS Tileが表示すべき状態。
 * PLAN.mdのUX要件（ロック状態のアイコン・色表示、通信中表示、スマホ未接続時の明示）に対応する。
 */
enum class TileDisplayState {
    LOCKED,
    UNLOCKED,
    IN_PROGRESS,
    DISCONNECTED,
    UNKNOWN,
    ;

    val isActionable: Boolean
        get() = this == LOCKED || this == UNLOCKED
}

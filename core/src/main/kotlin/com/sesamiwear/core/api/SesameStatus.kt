package com.sesamiwear.core.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sesame 5状態取得APIのレスポンス。
 * フィールド名はCANDY HOUSE Sesame API（sesame2系）の一般的な構造からの推測であり、
 * 実機での疎通確認（BL-010、人手検証）で実際のレスポンスとの整合を確認すること（未確認）。
 */
@Serializable
data class SesameStatus(
    val batteryVoltage: Double,
    val isBatteryCritical: Boolean,
    val position: Int,
    @SerialName("CHSesame2Status")
    val lockStatus: String,
    val isInLockRange: Boolean,
    val isInUnlockRange: Boolean,
)

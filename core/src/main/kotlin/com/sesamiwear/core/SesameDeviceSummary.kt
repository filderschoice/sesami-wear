package com.sesamiwear.core

import kotlinx.serialization.Serializable

/**
 * Wear側へ同期する登録済みSesameデバイスの要約情報（BL-052、複数デバイス対応）。
 * wear側は資格情報（apiKey/secretKey）を保持しない設計方針（PLAN.mdアーキテクチャ方針）のため、
 * Tile Configuration Activityでのデバイス選択に必要なuuid/displayNameのみを持つ。
 */
@Serializable
data class SesameDeviceSummary(
    val uuid: String,
    val displayName: String,
)

package com.sesamiwear.core.diagnostics

import com.sesamiwear.core.SesameStatusRoute
import kotlinx.serialization.Serializable

/** 診断ログへ残す操作（BL-188）。 */
enum class SesameDiagnosticsOperation(val label: String) {
    LOCK("施錠"),
    UNLOCK("解錠"),
    STATUS("状態取得"),
}

/** 診断ログの結果（BL-188）。[SKIPPED]は連打として無視した（通信していない）ことを表す。 */
enum class SesameDiagnosticsOutcome(val label: String) {
    SUCCESS("成功"),
    FAILURE("失敗"),
    SKIPPED("重複として無視"),
}

/**
 * 診断ログ1件（BL-188）。
 *
 * 通信エラーなどが起きたときに、利用者から内容を連携してもらって解析するためのもの。
 * **uuid・apikey・secretKeyは持たない。** 対象は[deviceName]（利用者が付けた表示名）だけで示す
 * （2026-09-20のユーザー判断。そのまま貼って送っても資格情報が漏れない状態を保つため。
 * `rules/guardrails-unified.v1.md` 3.3）。同名のデバイスがあると区別できないが、
 * 資格情報を外へ出さないことを優先する。
 *
 * [reason]は失敗の理由（「認証エラー」等）。成功時はnull。
 * ここにも応答本文やURLは入れない（[com.sesamiwear.core.api.SesameApiFailureLog]と同じ方針）。
 */
@Serializable
data class SesameDiagnosticsEntry(
    val epochMillis: Long,
    val operation: SesameDiagnosticsOperation,
    val deviceName: String,
    val outcome: SesameDiagnosticsOutcome,
    val route: SesameStatusRoute? = null,
    val reason: String? = null,
)

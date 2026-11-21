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

/**
 * 診断ログの「通信の詳細」1行（BL-302）。
 *
 * これまで`Log.w`（タグ`SesameApi`）にしか出していなかった行（HTTPの応答コードと本文長、失敗の理由、
 * 経路と成否、BLEのどの段階で失敗したか）を、利用者の端末で再現したときにも操作履歴から確かめられるよう残す。
 * logcatは利用者が取り出せないため。施錠・解錠の1件（[SesameDiagnosticsEntry]）とは別に、時刻順の行として持つ。
 *
 * [message]は[sanitize]を通した後の値だけを持つ（uuid・BLEアドレス・資格情報を含めない）。
 */
@Serializable
data class SesameDiagnosticsDetail(
    val epochMillis: Long,
    val message: String,
) {
    companion object {
        /** 1行の上限。想定外に長い行（例外の文言など）で保存値が膨らまないようにする。 */
        const val MAX_MESSAGE_LENGTH = 200

        // `Log.w`の行にはuuidの先頭8文字（`device=xxxxxxxx`）が入るものがある。logcatは端末から出ないが、
        // 操作履歴は利用者がそのまま貼って送るため、uuidの断片も残さない（2026-09-20のユーザー判断と同じ方針）。
        private val DEVICE_TOKEN = Regex("""\s*\bdevice=\S*""")

        // 念のため、uuid（8-4-4-4-12の16進数）・BLEアドレス（xx:xx:xx:xx:xx:xx）・16進数16文字以上の並び
        // （secretKeyなど）の形をした部分は伏せる。いまの呼び出し元はこれらを渡さないが、将来の行の追加で
        // 混入しても外へ出さないため。
        private val SENSITIVE_PATTERNS =
            listOf(
                Regex("""[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}"""),
                Regex("""(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"""),
                Regex("""\b[0-9A-Fa-f]{16,}\b"""),
            )

        private const val MASK = "***"

        /** 保存してよい形へ直す（`device=`の除去・資格情報らしい値の伏せ字・改行の除去・長さの上限）。 */
        fun sanitize(message: String): String {
            var result = DEVICE_TOKEN.replace(message, "")
            SENSITIVE_PATTERNS.forEach { result = it.replace(result, MASK) }
            return result.replace(Regex("""\s+"""), " ").trim().take(MAX_MESSAGE_LENGTH)
        }
    }
}

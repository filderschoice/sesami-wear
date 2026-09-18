package com.sesamiwear.core.api

/** ログへ残す操作の種別（BL-139）。表示用ではなくlogcatへの出力専用のため英字の短い識別子にする。 */
enum class SesameApiOperation(val logLabel: String) {
    STATUS("status"),
    LOCK("lock"),
    UNLOCK("unlock"),
}

/**
 * Sesame APIの失敗をlogcatへ1行で残すための文言を組み立てる（BL-139）。
 *
 * リリースビルドでは`-assumenosideeffects`により`Log.d` / `Log.v`が除去されるため（BL-083）、
 * 失敗の切り分けに使える出力は`Log.w`以上に限られる。ここで組み立てた文字列だけを`Log.w`へ渡す。
 *
 * 出力してよいのは「どの操作が」「どの種類の失敗で」落ちたかだけで、
 * apikey・secretKey・uuid・URL・応答本文は一切含めない（`rules/guardrails-unified.v1.md` 3.3）。
 * そのため[SesameApiException.message]自体は使わず、HTTPステータスコードと原因例外の型名だけを組み立てる。
 */
object SesameApiFailureLog {
    /** logcatのタグ。資格情報を含まない固定文字列。 */
    const val TAG = "SesameApi"

    /** 原因例外をたどる深さの上限。循環した参照でも停止させるための保険。 */
    private const val MAX_CAUSE_DEPTH = 10

    /**
     * [operation]が[failure]で失敗したことを表す1行を返す。
     * HTTPのエラー応答ならステータスコード、それ以外は原因例外の型名を理由として載せる。
     */
    fun describe(
        operation: SesameApiOperation,
        failure: SesameApiException,
    ): String = "${operation.logLabel} failed: ${reasonOf(failure)}"

    private fun reasonOf(failure: SesameApiException): String {
        val statusCode = failure.httpStatusCode
        if (statusCode != null) return "HTTP $statusCode"
        return (rootCauseOf(failure) ?: failure).javaClass.simpleName
    }

    /**
     * 原因例外をたどって最も内側の例外を返す。kotlinx.coroutinesは`withContext`をまたぐ例外を
     * スタックトレース復元のために複製し、複製の`cause`へ元の例外を入れるため、`cause`を1段
     * 見るだけでは正規化前の例外（[java.io.IOException]等）にたどり着けない。
     */
    private fun rootCauseOf(throwable: Throwable): Throwable? {
        var current = throwable.cause
        var remaining = MAX_CAUSE_DEPTH
        while (remaining > 0) {
            current = current?.cause ?: break
            remaining--
        }
        return current
    }
}

package com.sesamiwear.core.api

/**
 * 施錠/解錠コマンドのHTTP 2xx応答の控え（診断ログ専用）。
 *
 * Web APIの2xxは「クラウドが受理した」ことしか示さず、Hub 3経由でSesameが実際に動いたかは分からない。
 * 切り分けのため、ステータスコードと応答本文の長さだけを残す。本文の中身は保持しない。
 *
 * @param httpStatusCode 応答のHTTPステータスコード（2xx）。
 * @param bodyLength 応答本文のバイト数。読み取りに失敗した場合はnull。
 */
data class SesameCommandReceipt(
    val httpStatusCode: Int,
    val bodyLength: Int?,
)

/**
 * [SesameCommandReceipt]をlogcatへ1行で残す文言にする。[SesameApiFailureLog]と同じ方針で、
 * uuid・apikey・sign・history・応答本文は含めない。
 */
object SesameApiResponseLog {
    /** 例: `lock accepted: HTTP 200 body=2B`。本文の長さが不明なら`body=unknown`。 */
    fun describe(
        operation: SesameApiOperation,
        receipt: SesameCommandReceipt,
    ): String {
        val body = receipt.bodyLength?.let { "${it}B" } ?: "unknown"
        return "${operation.logLabel} accepted: HTTP ${receipt.httpStatusCode} body=$body"
    }
}

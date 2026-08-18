package com.sesamiwear.core

/**
 * Mobile側での施錠/解錠実行結果を、Data Layer APIのメッセージペイロード（1バイト）として
 * Wear側へ伝えるための表現。
 */
enum class SesameCommandResult(private val code: Byte) {
    SUCCESS(1),
    FAILURE(0),
    ;

    fun toPayload(): ByteArray = byteArrayOf(code)

    companion object {
        fun fromPayload(payload: ByteArray): SesameCommandResult {
            val firstByte = payload.firstOrNull()
            return entries.find { it.code == firstByte } ?: FAILURE
        }
    }
}

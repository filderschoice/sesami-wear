package com.sesamiwear.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class SesameApiFailureLogTest {
    @Test
    fun `http error is described by its status code`() {
        val failure = SesameApiException("Sesame API error: HTTP 403", httpStatusCode = HTTP_FORBIDDEN)

        assertEquals("status failed: HTTP 403", SesameApiFailureLog.describe(SesameApiOperation.STATUS, failure))
    }

    @Test
    fun `connection failure is described by the root cause type name`() {
        // kotlinx.coroutinesが`withContext`をまたぐ例外を複製する状況を模し、原因を2段にする。
        val failure =
            SesameApiException(
                "Sesame API call failed: IOException",
                cause = IllegalStateException("copy", IOException("boom")),
            )

        assertEquals("lock failed: IOException", SesameApiFailureLog.describe(SesameApiOperation.LOCK, failure))
    }

    @Test
    fun `failure without cause is described by its own type name`() {
        val failure = SesameApiException("Empty response body (HTTP 200)")

        assertEquals(
            "unlock failed: SesameApiException",
            SesameApiFailureLog.describe(SesameApiOperation.UNLOCK, failure),
        )
    }

    @Test
    fun `description never contains credentials, url or response body`() {
        val secrets =
            listOf(
                "8A2BE31C-0000-0000-0000-000000000000",
                "test-api-key",
                "2b7e151628aed2a6abf7158809cf4f3c",
                "candyhouse",
                "not authorized",
            )
        // 資格情報・URL・応答本文を素通しする経路が無いことを、それらを埋め込んだ例外で確かめる。
        val failure =
            SesameApiException(
                message = secrets.joinToString(separator = " "),
                cause = IOException(secrets.joinToString(separator = " ")),
                httpStatusCode = HTTP_FORBIDDEN,
            )

        SesameApiOperation.entries.forEach { operation ->
            val described = SesameApiFailureLog.describe(operation, failure)
            secrets.forEach { secret -> assertFalse(described, described.contains(secret)) }
        }
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}

package com.sesamiwear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameStatusFailureTest {
    @Test
    fun `auth and quota status codes are classified as the actionable failure`() {
        listOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS).forEach { statusCode ->
            assertEquals(
                statusCode.toString(),
                SesameStatusFailure.AUTH_OR_QUOTA,
                SesameStatusFailure.of(statusCode),
            )
        }
    }

    @Test
    fun `other status codes and connection failures are communication failures`() {
        assertEquals(SesameStatusFailure.COMMUNICATION, SesameStatusFailure.of(HTTP_SERVER_ERROR))
        assertEquals(SesameStatusFailure.COMMUNICATION, SesameStatusFailure.of(HTTP_NOT_FOUND))
        // 通信そのものの失敗・応答の解析失敗ではステータスコードを持たない（BL-139）。
        assertEquals(SesameStatusFailure.COMMUNICATION, SesameStatusFailure.of(null))
    }

    @Test
    fun `labels are short enough for the tile and carry guidance on the widget`() {
        SesameStatusFailure.entries.forEach { failure ->
            assertEquals(failure.name, MAX_SHORT_LABEL_LENGTH, failure.shortLabel.length)
            assertEquals(failure.name, true, failure.detailedLabel.startsWith(failure.shortLabel))
            assertEquals(failure.name, true, failure.detailedLabel.length > failure.shortLabel.length)
        }
    }

    @Test
    fun `restores from the stored name and ignores unknown values`() {
        assertEquals(SesameStatusFailure.AUTH_OR_QUOTA, SesameStatusFailure.ofNameOrNull("AUTH_OR_QUOTA"))
        assertNull(SesameStatusFailure.ofNameOrNull("RETIRED_VALUE"))
        assertNull(SesameStatusFailure.ofNameOrNull(null))
    }

    @Test
    fun `aggregate prefers the failure the user can act on`() {
        assertEquals(
            SesameStatusFailure.AUTH_OR_QUOTA,
            SesameStatusFailure.worstOf(
                listOf(SesameStatusFailure.COMMUNICATION, null, SesameStatusFailure.AUTH_OR_QUOTA),
            ),
        )
        assertEquals(
            SesameStatusFailure.COMMUNICATION,
            SesameStatusFailure.worstOf(listOf(null, SesameStatusFailure.COMMUNICATION)),
        )
        assertNull(SesameStatusFailure.worstOf(listOf(null, null)))
        assertNull(SesameStatusFailure.worstOf(emptyList()))
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val MAX_SHORT_LABEL_LENGTH = 5
    }
}

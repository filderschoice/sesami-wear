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
    fun `background restriction is only used when no response was received`() {
        assertEquals(
            SesameStatusFailure.BACKGROUND_RESTRICTED,
            SesameStatusFailure.of(null, backgroundDataRestricted = true),
        )
        // 応答が返っている時点でOSの制限は掛かっていないため、端末設定のせいにしない。
        assertEquals(
            SesameStatusFailure.COMMUNICATION,
            SesameStatusFailure.of(HTTP_SERVER_ERROR, backgroundDataRestricted = true),
        )
        // 資格情報・上限の失敗は、制限の有無に関わらず利用者の対処先が変わらない。
        assertEquals(
            SesameStatusFailure.AUTH_OR_QUOTA,
            SesameStatusFailure.of(HTTP_FORBIDDEN, backgroundDataRestricted = true),
        )
        // 制限が掛かっていなければ従来どおり。
        assertEquals(
            SesameStatusFailure.COMMUNICATION,
            SesameStatusFailure.of(null, backgroundDataRestricted = false),
        )
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
        // 端末設定の制限は利用者が対処できるため、ただの通信エラーより優先して見せる（BL-192）。
        assertEquals(
            SesameStatusFailure.BACKGROUND_RESTRICTED,
            SesameStatusFailure.worstOf(
                listOf(SesameStatusFailure.COMMUNICATION, SesameStatusFailure.BACKGROUND_RESTRICTED),
            ),
        )
        assertEquals(
            SesameStatusFailure.AUTH_OR_QUOTA,
            SesameStatusFailure.worstOf(
                listOf(SesameStatusFailure.BACKGROUND_RESTRICTED, SesameStatusFailure.AUTH_OR_QUOTA),
            ),
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

package com.sesamiwear.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SesameApiResponseLogTest {
    @Test
    fun `accepted response is described by status code and body length`() {
        val receipt = SesameCommandReceipt(httpStatusCode = HTTP_OK, bodyLength = 2)

        assertEquals("lock accepted: HTTP 200 body=2B", SesameApiResponseLog.describe(SesameApiOperation.LOCK, receipt))
    }

    @Test
    fun `unknown body length is described as unknown`() {
        val receipt = SesameCommandReceipt(httpStatusCode = HTTP_OK, bodyLength = null)

        assertEquals(
            "unlock accepted: HTTP 200 body=unknown",
            SesameApiResponseLog.describe(SesameApiOperation.UNLOCK, receipt),
        )
    }

    private companion object {
        const val HTTP_OK = 200
    }
}

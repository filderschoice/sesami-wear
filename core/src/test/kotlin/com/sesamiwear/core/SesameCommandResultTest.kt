package com.sesamiwear.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SesameCommandResultTest {
    @Test
    fun `round-trips success through payload bytes`() {
        assertArrayEquals(byteArrayOf(1), SesameCommandResult.SUCCESS.toPayload())
        assertEquals(SesameCommandResult.SUCCESS, SesameCommandResult.fromPayload(byteArrayOf(1)))
    }

    @Test
    fun `round-trips failure through payload bytes`() {
        assertArrayEquals(byteArrayOf(0), SesameCommandResult.FAILURE.toPayload())
        assertEquals(SesameCommandResult.FAILURE, SesameCommandResult.fromPayload(byteArrayOf(0)))
    }

    @Test
    fun `treats unknown or empty payload as failure`() {
        assertEquals(SesameCommandResult.FAILURE, SesameCommandResult.fromPayload(byteArrayOf(99)))
        assertEquals(SesameCommandResult.FAILURE, SesameCommandResult.fromPayload(ByteArray(0)))
    }
}

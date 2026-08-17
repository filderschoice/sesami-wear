package com.sesamiwear.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SesameWearProtocolTest {
    @Test
    fun `message paths are stable string constants`() {
        assertEquals("/sesami-wear/lock", SesameWearProtocol.PATH_LOCK_REQUEST)
        assertEquals("/sesami-wear/unlock", SesameWearProtocol.PATH_UNLOCK_REQUEST)
        assertEquals("/sesami-wear/result", SesameWearProtocol.PATH_COMMAND_RESULT)
    }
}

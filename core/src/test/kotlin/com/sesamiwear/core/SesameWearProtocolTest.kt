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

    @Test
    fun `decodeDeviceUuid round-trips a value encoded by encodeDeviceUuid`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        val decoded = SesameWearProtocol.decodeDeviceUuid(SesameWearProtocol.encodeDeviceUuid(uuid))

        assertEquals(uuid, decoded)
    }

    @Test
    fun `decodeDeviceUuid returns empty string for empty payload`() {
        val decoded = SesameWearProtocol.decodeDeviceUuid(ByteArray(0))

        assertEquals("", decoded)
    }
}

package com.sesamiwear.mobile.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * アドバタイズの製造者データの解釈の検証（BL-151）。
 * uuidはダミー値で、実デバイスのuuidは含まない。
 */
class SesameBleAdvertisementTest {
    private val dummyUuidBytes = "1234567890ABCDEF1234567890ABCDEF"
    private val dummyUuid = "12345678-90ab-cdef-1234-567890abcdef"

    @Test
    fun `parses a registered sesame 5`() {
        val advertisement = SesameBleAdvertisement.parse(hexToBytes("050001$dummyUuidBytes"))!!
        assertEquals(5, advertisement.productModelCode)
        assertTrue(advertisement.isRegistered)
        assertTrue(advertisement.isSesame5Family)
        assertEquals(dummyUuid, advertisement.deviceUuid)
    }

    @Test
    fun `parses sesame 5 pro and sesame 5 us as the same family`() {
        assertTrue(SesameBleAdvertisement.parse(hexToBytes("070001$dummyUuidBytes"))!!.isSesame5Family)
        assertTrue(SesameBleAdvertisement.parse(hexToBytes("100001$dummyUuidBytes"))!!.isSesame5Family)
    }

    @Test
    fun `other product models are not treated as the sesame 5 family`() {
        // 0x000A = Sesame Touch 1。本アプリの操作対象ではない。
        val advertisement = SesameBleAdvertisement.parse(hexToBytes("0A0001$dummyUuidBytes"))!!
        assertFalse(advertisement.isSesame5Family)
    }

    @Test
    fun `reports an unregistered device`() {
        val advertisement = SesameBleAdvertisement.parse(hexToBytes("050000$dummyUuidBytes"))!!
        assertFalse(advertisement.isRegistered)
    }

    @Test
    fun `returns null when the payload length does not match`() {
        assertNull(SesameBleAdvertisement.parse(ByteArray(0)))
        assertNull(SesameBleAdvertisement.parse(ByteArray(SesameBleAdvertisement.PAYLOAD_SIZE - 1)))
        assertNull(SesameBleAdvertisement.parse(ByteArray(SesameBleAdvertisement.PAYLOAD_SIZE + 1)))
    }

    @Test
    fun `formats the uuid in lower case so it can be compared with stored credentials`() {
        val advertisement = SesameBleAdvertisement.parse(hexToBytes("050001FFEEDDCCBBAA99887766554433221100"))!!
        assertEquals("ffeeddcc-bbaa-9988-7766-554433221100", advertisement.deviceUuid)
        assertEquals(dummyUuid.length, advertisement.deviceUuid.length)
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

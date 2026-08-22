package com.sesamiwear.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.HexFormat

class SesameCredentialsTest {
    @Test
    fun `displayName defaults to empty string when not specified`() {
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyHex = "")

        assertEquals("", credentials.displayName)
    }

    @Test
    fun `displayName is retained when specified`() {
        val credentials =
            SesameCredentials(uuid = "u", apiKey = "a", secretKeyHex = "", displayName = "玄関")

        assertEquals("玄関", credentials.displayName)
    }

    @Test
    fun `secretKeyBytesOrNull decodes a valid 16-byte hex key`() {
        val secretKeyHex = HexFormat.of().formatHex(ByteArray(16) { it.toByte() })
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyHex = secretKeyHex)

        assertArrayEquals(ByteArray(16) { it.toByte() }, credentials.secretKeyBytesOrNull)
    }

    @Test
    fun `secretKeyBytesOrNull decodes uppercase hex`() {
        val lowercaseHex = "2ebc2c087c1501480834538ff72139bc"
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyHex = lowercaseHex.uppercase())

        assertArrayEquals(HexFormat.of().parseHex(lowercaseHex), credentials.secretKeyBytesOrNull)
    }

    @Test
    fun `secretKeyBytesOrNull returns null for invalid hex`() {
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyHex = "not-valid-hex-string!!!")

        assertNull(credentials.secretKeyBytesOrNull)
    }

    @Test
    fun `secretKeyBytesOrNull returns null when decoded length is not 16 bytes`() {
        val tooShortHex = HexFormat.of().formatHex(ByteArray(8))
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyHex = tooShortHex)

        assertNull(credentials.secretKeyBytesOrNull)
    }

    @Test
    fun `secretKeyBytesOrNull returns null for blank input`() {
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyHex = "")

        assertNull(credentials.secretKeyBytesOrNull)
    }
}

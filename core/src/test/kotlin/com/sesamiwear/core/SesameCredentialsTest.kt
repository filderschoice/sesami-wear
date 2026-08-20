package com.sesamiwear.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class SesameCredentialsTest {
    @Test
    fun `secretKeyBytesOrNull decodes a valid 16-byte key`() {
        val secretKeyBase64 = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyBase64 = secretKeyBase64)

        assertArrayEquals(ByteArray(16) { it.toByte() }, credentials.secretKeyBytesOrNull)
    }

    @Test
    fun `secretKeyBytesOrNull returns null for invalid base64`() {
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyBase64 = "not-valid-base64!!!")

        assertNull(credentials.secretKeyBytesOrNull)
    }

    @Test
    fun `secretKeyBytesOrNull returns null when decoded length is not 16 bytes`() {
        val tooShortBase64 = Base64.getEncoder().encodeToString(ByteArray(8))
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyBase64 = tooShortBase64)

        assertNull(credentials.secretKeyBytesOrNull)
    }

    @Test
    fun `secretKeyBytesOrNull returns null for blank input`() {
        val credentials = SesameCredentials(uuid = "u", apiKey = "a", secretKeyBase64 = "")

        assertNull(credentials.secretKeyBytesOrNull)
    }
}

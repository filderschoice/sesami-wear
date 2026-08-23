package com.sesamiwear.mobile.credentials

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.HexFormat

class CredentialsInputValidatorTest {
    private val validSecretKeyHex = HexFormat.of().formatHex(ByteArray(16))

    @Test
    fun `valid when all fields are non-blank and secret key is a valid 16-byte key`() {
        assertTrue(CredentialsInputValidator.isValid("uuid", "key", validSecretKeyHex))
    }

    @Test
    fun `invalid when uuid is blank`() {
        assertFalse(CredentialsInputValidator.isValid("", "key", validSecretKeyHex))
    }

    @Test
    fun `invalid when api key is blank`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "", validSecretKeyHex))
    }

    @Test
    fun `invalid when secret key is blank`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "key", ""))
    }

    @Test
    fun `invalid when a field is whitespace only`() {
        assertFalse(CredentialsInputValidator.isValid("   ", "key", validSecretKeyHex))
    }

    @Test
    fun `invalid when secret key is not valid hex`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "key", "not-valid-hex-string!!!"))
    }

    @Test
    fun `invalid when secret key decodes to the wrong length`() {
        val wrongLengthHex = HexFormat.of().formatHex(ByteArray(8))
        assertFalse(CredentialsInputValidator.isValid("uuid", "key", wrongLengthHex))
    }
}

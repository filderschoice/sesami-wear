package com.sesamiwear.mobile.credentials

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CredentialsInputValidatorTest {
    private val validSecretKeyBase64 = Base64.getEncoder().encodeToString(ByteArray(16))

    @Test
    fun `valid when all fields are non-blank and secret key is a valid 16-byte key`() {
        assertTrue(CredentialsInputValidator.isValid("uuid", "key", validSecretKeyBase64))
    }

    @Test
    fun `invalid when uuid is blank`() {
        assertFalse(CredentialsInputValidator.isValid("", "key", validSecretKeyBase64))
    }

    @Test
    fun `invalid when api key is blank`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "", validSecretKeyBase64))
    }

    @Test
    fun `invalid when secret key is blank`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "key", ""))
    }

    @Test
    fun `invalid when a field is whitespace only`() {
        assertFalse(CredentialsInputValidator.isValid("   ", "key", validSecretKeyBase64))
    }

    @Test
    fun `invalid when secret key is not valid base64`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "key", "not-valid-base64!!!"))
    }

    @Test
    fun `invalid when secret key decodes to the wrong length`() {
        val wrongLengthBase64 = Base64.getEncoder().encodeToString(ByteArray(8))
        assertFalse(CredentialsInputValidator.isValid("uuid", "key", wrongLengthBase64))
    }
}

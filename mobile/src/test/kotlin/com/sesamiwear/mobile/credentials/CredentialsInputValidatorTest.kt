package com.sesamiwear.mobile.credentials

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsInputValidatorTest {
    @Test
    fun `valid when all fields are non-blank`() {
        assertTrue(CredentialsInputValidator.isValid("uuid", "key", "secret"))
    }

    @Test
    fun `invalid when uuid is blank`() {
        assertFalse(CredentialsInputValidator.isValid("", "key", "secret"))
    }

    @Test
    fun `invalid when api key is blank`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "", "secret"))
    }

    @Test
    fun `invalid when secret key is blank`() {
        assertFalse(CredentialsInputValidator.isValid("uuid", "key", ""))
    }

    @Test
    fun `invalid when a field is whitespace only`() {
        assertFalse(CredentialsInputValidator.isValid("   ", "key", "secret"))
    }
}

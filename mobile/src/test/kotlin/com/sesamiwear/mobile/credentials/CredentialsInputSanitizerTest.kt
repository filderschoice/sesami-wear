package com.sesamiwear.mobile.credentials

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialsInputSanitizerTest {
    @Test
    fun `uuid converts full-width alphanumerics to half-width`() {
        assertEquals(
            "12345678-90AB-CDEF-1234-567890ABCDEF",
            CredentialsInputSanitizer.sanitizeUuid("１２３４５６７８－90AB-CDEF-1234-567890ABCDEF"),
        )
    }

    @Test
    fun `uuid drops characters that cannot appear in a uuid`() {
        assertEquals("abc-123", CredentialsInputSanitizer.sanitizeUuid("abc あ　-1 2 3"))
    }

    @Test
    fun `uuid maps dash like characters to a half-width hyphen`() {
        assertEquals("a-b-c-d", CredentialsInputSanitizer.sanitizeUuid("a\u2010b\u2212c\u30FCd"))
    }

    @Test
    fun `apikey converts full-width characters and drops spaces`() {
        assertEquals("Abc123+/=", CredentialsInputSanitizer.sanitizeApiKey("Ａbc１２３ ＋／＝"))
    }

    @Test
    fun `apikey drops non ascii characters`() {
        assertEquals("key", CredentialsInputSanitizer.sanitizeApiKey("k鍵e\ny\t"))
    }

    @Test
    fun `secret key keeps only hex digits`() {
        assertEquals("0123abcdEF", CredentialsInputSanitizer.sanitizeSecretKeyHex("0123 abcd-EF ぐ"))
    }

    @Test
    fun `secret key converts full-width hex digits`() {
        assertEquals("00ff", CredentialsInputSanitizer.sanitizeSecretKeyHex("００ｆｆ"))
    }

    @Test
    fun `secret key is truncated to the key length`() {
        val input = "0".repeat(CredentialsInputSanitizer.SECRET_KEY_HEX_LENGTH + 8)
        assertEquals(
            CredentialsInputSanitizer.SECRET_KEY_HEX_LENGTH,
            CredentialsInputSanitizer.sanitizeSecretKeyHex(input).length,
        )
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", CredentialsInputSanitizer.sanitizeUuid(""))
        assertEquals("", CredentialsInputSanitizer.sanitizeApiKey("　 "))
        assertEquals("", CredentialsInputSanitizer.sanitizeSecretKeyHex("ぐ"))
    }

    @Test
    fun `valid half-width values are left untouched`() {
        val uuid = "AB12CD34-5678-90EF-AB12-CD34567890EF"
        val apiKey = "abcDEF0123456789"
        val secretKey = "0123456789abcdef0123456789ABCDEF"
        assertEquals(uuid, CredentialsInputSanitizer.sanitizeUuid(uuid))
        assertEquals(apiKey, CredentialsInputSanitizer.sanitizeApiKey(apiKey))
        assertEquals(secretKey, CredentialsInputSanitizer.sanitizeSecretKeyHex(secretKey))
    }
}

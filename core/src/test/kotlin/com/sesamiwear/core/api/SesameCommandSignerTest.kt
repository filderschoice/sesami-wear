package com.sesamiwear.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 期待値はpycryptodome（CMAC.new(key, ciphermod=AES)）による独立計算で検証したもの。
 * 鍵はRFC 4493 Appendix Aのダミー鍵（実資格情報ではない）。
 */
class SesameCommandSignerTest {
    @Test
    fun `sign matches independently computed AES-CMAC for a fixed timestamp`() {
        val key = hexToBytes("2b7e151628aed2a6abf7158809cf4f3c")
        val unixTimeSeconds = 305419896L // 0x12345678

        val sign = SesameCommandSigner.sign(key, unixTimeSeconds)

        assertEquals("dee3ab3d39b8792946dfeb7f81754b82", sign)
    }

    @Test
    fun `sign is deterministic for the same timestamp`() {
        val key = hexToBytes("2b7e151628aed2a6abf7158809cf4f3c")

        val first = SesameCommandSigner.sign(key, 1000L)
        val second = SesameCommandSigner.sign(key, 1000L)

        assertEquals(first, second)
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex string must have an even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

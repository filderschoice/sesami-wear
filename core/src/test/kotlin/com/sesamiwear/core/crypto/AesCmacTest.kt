package com.sesamiwear.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * RFC 4493 Appendix A に掲載された公開テストベクタ（AES-128）による検証。
 * 鍵・平文・MACはすべてRFC記載値で、実資格情報は含まない。
 */
class AesCmacTest {
    private val key = hexToBytes("2b7e151628aed2a6abf7158809cf4f3c")

    @Test
    fun `example 1 - empty message`() {
        val mac = AesCmac.compute(key, ByteArray(0))
        assertArrayEquals(hexToBytes("bb1d6929e95937287fa37d129b756746"), mac)
    }

    @Test
    fun `example 2 - 16 byte message`() {
        val message = hexToBytes("6bc1bee22e409f96e93d7e117393172a")
        val mac = AesCmac.compute(key, message)
        assertArrayEquals(hexToBytes("070a16b46b4d4144f79bdd9dd04a287c"), mac)
    }

    @Test
    fun `example 3 - 40 byte message`() {
        val message =
            hexToBytes(
                "6bc1bee22e409f96e93d7e117393172a" +
                    "ae2d8a571e03ac9c9eb76fac45af8e51" +
                    "30c81c46a35ce411",
            )
        val mac = AesCmac.compute(key, message)
        assertArrayEquals(hexToBytes("dfa66747de9ae63030ca32611497c827"), mac)
    }

    @Test
    fun `example 4 - 64 byte message`() {
        val message =
            hexToBytes(
                "6bc1bee22e409f96e93d7e117393172a" +
                    "ae2d8a571e03ac9c9eb76fac45af8e51" +
                    "30c81c46a35ce411e5fbc1191a0a52ef" +
                    "f69f2445df4f9b17ad2b417be66c3710",
            )
        val mac = AesCmac.compute(key, message)
        assertArrayEquals(hexToBytes("51f0bebf7e3b9d92fc49741779363cfe"), mac)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-128-bit keys`() {
        AesCmac.compute(ByteArray(8), ByteArray(16))
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex string must have an even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

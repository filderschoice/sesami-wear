package com.sesamiwear.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RFC 3610 Section 8 に掲載された公開テストベクタ（AES-128 CCM）による検証。
 * 鍵・nonce・平文・暗号文はすべてRFC記載値で、実資格情報は含まない。
 *
 * RFCの「Total packet length」は先頭8オクテットの平文ヘッダ（＝AAD）を含む長さのため、
 * 本テストでは`aad`（8バイト）と`plaintext`（残り）へ分けて渡している。
 */
class AesCcmTest {
    private val key = hexToBytes("C0C1C2C3C4C5C6C7C8C9CACBCCCDCECF")
    private val aad = hexToBytes("0001020304050607")

    @Test
    fun `packet vector 1 - 23 byte payload with 8 byte tag`() {
        assertRoundTrip(
            nonce = "00000003020100A0A1A2A3A4A5",
            plaintext = "08090A0B0C0D0E0F101112131415161718191A1B1C1D1E",
            expected = "588C979A61C663D2F066D0C2C0F989806D5F6B61DAC38417E8D12CFDF926E0",
            tagLength = 8,
        )
    }

    @Test
    fun `packet vector 2 - payload that is an exact block multiple`() {
        assertRoundTrip(
            nonce = "00000004030201A0A1A2A3A4A5",
            plaintext = "08090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            expected = "72C91A36E135F8CF291CA894085C87E3CC15C439C9E43A3BA091D56E10400916",
            tagLength = 8,
        )
    }

    @Test
    fun `packet vector 3 - payload spanning an extra partial block`() {
        assertRoundTrip(
            nonce = "00000005040302A0A1A2A3A4A5",
            plaintext = "08090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20",
            expected = "51B1E5F44A197D1DA46B0F8E2D282AE871E838BB64DA8596574ADAA76FBD9FB0C5",
            tagLength = 8,
        )
    }

    @Test
    fun `packet vector 7 - same payload with a 10 byte tag`() {
        assertRoundTrip(
            nonce = "00000003020100A0A1A2A3A4A5",
            plaintext = "08090A0B0C0D0E0F101112131415161718191A1B1C1D1E",
            expected =
                "588C979A61C663D2F066D0C2C0F98980" +
                    "6D5F6B61DAC384FEA4B050E8727D0D2CB3",
            tagLength = 10,
        )
    }

    /**
     * Sesame OS3のBLE通信が使うパラメータ（13バイトnonce・4バイトタグ・AADは`0x00`の1バイト）での検証。
     * 期待値はPyCryptodome 3.x（`AES.MODE_CCM`）で生成した。鍵・トークンはダミー値。
     */
    @Test
    fun `sesame os3 parameters - 13 byte nonce with 4 byte tag`() {
        val sessionKey = hexToBytes("1D7C7F1D52493A30CC2EE841F461AFAF")
        val sesameAad = byteArrayOf(0)
        val cases =
            listOf(
                Triple("00000000000000000011223344", "5200", "EB9F574E4557"),
                Triple("01000000000000000011223344", "530568656C6C6F", "7F03AF77177E563F04A816"),
                Triple("07000000000000000011223344", "51", "671F07727F"),
            )
        cases.forEach { (nonce, plaintext, expected) ->
            val encrypted =
                AesCcm.encrypt(sessionKey, hexToBytes(nonce), sesameAad, hexToBytes(plaintext), TAG_LENGTH_SESAME)
            assertArrayEquals(hexToBytes(expected), encrypted)
            val decrypted = AesCcm.decrypt(sessionKey, hexToBytes(nonce), sesameAad, encrypted, TAG_LENGTH_SESAME)
            assertArrayEquals(hexToBytes(plaintext), decrypted)
        }
    }

    @Test
    fun `decrypt returns null when the tag was tampered with`() {
        val nonce = hexToBytes("00000003020100A0A1A2A3A4A5")
        val encrypted = AesCcm.encrypt(key, nonce, aad, hexToBytes("08090A0B"), TAG_LENGTH_SESAME)
        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        assertNull(AesCcm.decrypt(key, nonce, aad, tampered, TAG_LENGTH_SESAME))
    }

    @Test
    fun `decrypt returns null when the ciphertext was tampered with`() {
        val nonce = hexToBytes("00000003020100A0A1A2A3A4A5")
        val encrypted = AesCcm.encrypt(key, nonce, aad, hexToBytes("08090A0B"), TAG_LENGTH_SESAME)
        val tampered = encrypted.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertNull(AesCcm.decrypt(key, nonce, aad, tampered, TAG_LENGTH_SESAME))
    }

    @Test
    fun `decrypt returns null when the input is shorter than the tag`() {
        val nonce = hexToBytes("00000003020100A0A1A2A3A4A5")
        assertNull(AesCcm.decrypt(key, nonce, aad, ByteArray(3), TAG_LENGTH_SESAME))
    }

    @Test
    fun `encrypts an empty payload`() {
        val nonce = hexToBytes("00000003020100A0A1A2A3A4A5")
        val encrypted = AesCcm.encrypt(key, nonce, aad, ByteArray(0), TAG_LENGTH_SESAME)
        assertArrayEquals(ByteArray(0), AesCcm.decrypt(key, nonce, aad, encrypted, TAG_LENGTH_SESAME))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-128-bit keys`() {
        AesCcm.encrypt(ByteArray(8), ByteArray(13), aad, ByteArray(1), TAG_LENGTH_SESAME)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects nonces outside the supported range`() {
        AesCcm.encrypt(key, ByteArray(14), aad, ByteArray(1), TAG_LENGTH_SESAME)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects odd tag lengths`() {
        AesCcm.encrypt(key, ByteArray(13), aad, ByteArray(1), 5)
    }

    private fun assertRoundTrip(
        nonce: String,
        plaintext: String,
        expected: String,
        tagLength: Int,
    ) {
        val nonceBytes = hexToBytes(nonce)
        val plaintextBytes = hexToBytes(plaintext)
        val encrypted = AesCcm.encrypt(key, nonceBytes, aad, plaintextBytes, tagLength)
        assertArrayEquals(hexToBytes(expected), encrypted)
        assertArrayEquals(plaintextBytes, AesCcm.decrypt(key, nonceBytes, aad, encrypted, tagLength))
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex string must have an even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        /** Sesame OS3が使う認証タグ長（バイト）。 */
        const val TAG_LENGTH_SESAME = 4
    }
}

package com.sesamiwear.mobile.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * セッション鍵の導出とAES-CCMによる暗号化・復号の検証（BL-151）。
 *
 * 鍵・セッショントークンはすべてダミー値で、実資格情報は含まない
 * （rules/guardrails-unified.v1.md 12.5）。期待値はPyCryptodome 3.x（`AES.MODE_CCM` /
 * `CMAC`）で生成し、同じ手順を`meronepy/gomalock`（MIT）の`_os3_cipher.py`が実装している。
 */
class SesameBleSessionTest {
    private val secretKey = hexToBytes("000102030405060708090a0b0c0d0e0f")
    private val sessionToken = hexToBytes("11223344")

    @Test
    fun `login payload is the first four bytes of the derived session key`() {
        // sessionKey = AES-CMAC(secretKey, sessionToken) = 1D7C7F1D52493A30CC2EE841F461AFAF
        assertArrayEquals(hexToBytes("1D7C7F1D"), newSession().loginPayload)
    }

    @Test
    fun `encrypt matches the reference implementation and advances the counter`() {
        val session = newSession()
        assertArrayEquals(hexToBytes("EB9F574E4557"), session.encrypt(hexToBytes("5200")))
        assertArrayEquals(hexToBytes("7F03AF77177E563F04A816"), session.encrypt(hexToBytes("530568656C6C6F")))
    }

    @Test
    fun `decrypt uses its own counter independent of the encrypt counter`() {
        val session = newSession()
        session.encrypt(hexToBytes("5200"))
        session.encrypt(hexToBytes("5200"))
        // 受信側カウンタは0のままなので、カウンタ0で暗号化した値が復号できる。
        assertArrayEquals(hexToBytes("5200"), session.decrypt(hexToBytes("EB9F574E4557")))
    }

    @Test
    fun `a device and an app session can exchange messages in both directions`() {
        val app = newSession()
        val device = newSession()
        val first = app.encrypt(hexToBytes("5200"))
        assertArrayEquals(hexToBytes("5200"), device.decrypt(first))
        val second = app.encrypt(hexToBytes("51"))
        assertArrayEquals(hexToBytes("51"), device.decrypt(second))
    }

    @Test
    fun `decrypt returns null and keeps the counter when authentication fails`() {
        val session = newSession()
        val valid = hexToBytes("EB9F574E4557")
        val tampered = valid.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        assertNull(session.decrypt(tampered))
        // カウンタが進んでいなければ、同じカウンタ値の正しい暗号文を続けて復号できる。
        assertArrayEquals(hexToBytes("5200"), session.decrypt(valid))
    }

    @Test
    fun `a different secret key cannot decrypt the message`() {
        val other = SesameBleSession(hexToBytes("0f0e0d0c0b0a09080706050403020100"), sessionToken)
        assertNull(other.decrypt(hexToBytes("EB9F574E4557")))
    }

    @Test
    fun `a different session token derives a different session key`() {
        val other = SesameBleSession(secretKey, hexToBytes("44332211"))
        assertFalse(other.loginPayload.contentEquals(newSession().loginPayload))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects session tokens of the wrong length`() {
        SesameBleSession(secretKey, hexToBytes("112233"))
    }

    private fun newSession() = SesameBleSession(secretKey, sessionToken)

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

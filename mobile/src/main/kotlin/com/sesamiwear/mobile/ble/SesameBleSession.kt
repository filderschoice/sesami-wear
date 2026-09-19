package com.sesamiwear.mobile.ble

import com.sesamiwear.core.crypto.AesCcm
import com.sesamiwear.core.crypto.AesCmac
import com.sesamiwear.mobile.ble.SesameBleProtocol.SESSION_TOKEN_LENGTH
import com.sesamiwear.mobile.ble.SesameBleProtocol.TAG_LENGTH

/**
 * BLE接続1回ぶんの暗号セッション（BL-151）。
 *
 * 接続直後にデバイスが`INITIAL`通知で送ってくる4バイトのセッショントークンと、保持中のsecretKeyから
 * セッション鍵をAES-CMACで導出し、以降のコマンド・通知をAES-CCMで暗号化・復号する。
 * secretKeyはWeb APIの署名（`core.api.SesameCommandSigner`）で使うものと同一で、
 * BL-150で実機実証済み（DESIGN.md「BLE直接操作の併用方針」論点1）。
 *
 * nonceは「送受信それぞれのカウンタ（8バイトリトルエンディアン）＋`0x00`＋セッショントークン」の
 * 13バイト。カウンタは送信用と受信用で独立し、1メッセージごとに1増える。順序がずれると復号が
 * 失敗するため、復号に失敗した通知は捨ててカウンタを進めない（[decrypt]がnullを返す）。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 * secretKeyもセッション鍵もログへ出さない（rules/guardrails-unified.v1.md 3.3）。
 */
class SesameBleSession(
    secretKey: ByteArray,
    private val sessionToken: ByteArray,
) {
    init {
        require(sessionToken.size == SESSION_TOKEN_LENGTH) {
            "Session token must be $SESSION_TOKEN_LENGTH bytes"
        }
    }

    /** セッション鍵。secretKeyでセッショントークンをAES-CMACした値（16バイト）。 */
    private val sessionKey: ByteArray = AesCmac.compute(secretKey, sessionToken)

    private var encryptCounter = 0L
    private var decryptCounter = 0L

    /**
     * ログインコマンドのペイロード。セッション鍵の先頭4バイトを平文で送り、
     * デバイス側が同じ鍵を導出できたことを相互に確認する。
     */
    val loginPayload: ByteArray get() = sessionKey.copyOfRange(0, SESSION_TOKEN_LENGTH)

    /** [plaintext]を暗号化し、送信カウンタを1進める。 */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val result = AesCcm.encrypt(sessionKey, nonce(encryptCounter), AAD, plaintext, TAG_LENGTH)
        encryptCounter++
        return result
    }

    /**
     * [ciphertext]を復号し、受信カウンタを1進める。認証に失敗した場合はnullを返し、
     * カウンタを進めない（次の通知で同じカウンタ値をもう一度試せるようにするため）。
     */
    fun decrypt(ciphertext: ByteArray): ByteArray? {
        val result = AesCcm.decrypt(sessionKey, nonce(decryptCounter), AAD, ciphertext, TAG_LENGTH) ?: return null
        decryptCounter++
        return result
    }

    private fun nonce(counter: Long): ByteArray {
        val bytes = ByteArray(NONCE_LENGTH)
        for (i in 0 until COUNTER_LENGTH) {
            bytes[i] = ((counter ushr (Byte.SIZE_BITS * i)) and BYTE_MASK).toByte()
        }
        sessionToken.copyInto(bytes, destinationOffset = COUNTER_LENGTH + 1)
        return bytes
    }

    private companion object {
        /** AES-CCMの追加認証データ。Sesame OS3では`0x00`の1バイト固定。 */
        val AAD = byteArrayOf(0)
        const val COUNTER_LENGTH = 8
        const val NONCE_LENGTH = COUNTER_LENGTH + 1 + SESSION_TOKEN_LENGTH
        const val BYTE_MASK = 0xFFL
    }
}

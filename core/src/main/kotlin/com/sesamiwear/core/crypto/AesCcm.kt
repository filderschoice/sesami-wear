package com.sesamiwear.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** AES-CCMのブロック長（バイト）。AESのブロック長と同じ。 */
private const val CCM_BLOCK_SIZE = 16

/** AES-128の鍵長（バイト）。本アプリはAES-128のみを扱う。 */
private const val CCM_KEY_SIZE = 16

private const val CCM_NONCE_MIN_SIZE = 7
private const val CCM_NONCE_MAX_SIZE = 13
private const val CCM_TAG_MIN_SIZE = 4
private const val CCM_AAD_LENGTH_PREFIX_SIZE = 2
private const val CCM_AAD_MAX_SHORT_LENGTH = 0xFF00
private const val CCM_BYTE_MASK = 0xFF
private const val CCM_ADATA_FLAG = 0x40

/**
 * RFC 3610 (Counter with CBC-MAC) のAES-CCM実装。
 * Sesame 5のBLE直接操作（BL-151）で、ログイン後の全コマンド・全通知の暗号化に使用する。
 *
 * AndroidのJCEプロバイダ（Conscrypt）は`AES/CCM/NoPadding`を提供しないため、
 * どの端末でも確実に使える`AES/ECB/NoPadding`（1ブロック単位のAES素関数）の上へ自前で組み立てる。
 * BLE固有の要素を含まない純粋な暗号プリミティブのため、[AesCmac]と同じ`core.crypto`へ置く
 * （BLE実装そのものは`mobile.ble`にあり、`core`のAndroid非依存制約は維持している）。
 * 検証はRFC 3610の公開テストベクタで行う（rules/guardrails-unified.v1.md 12.5）。
 */
object AesCcm {
    /**
     * [plaintext]を暗号化し、末尾に[tagLength]バイトの認証タグを連結して返す。
     *
     * [nonce]の長さは7〜13バイト。CCMの仕様上、長さフィールドのバイト数`L`は`15 - nonce.size`で
     * 決まり、平文長は`2^(8L)`未満でなければならない。[tagLength]は4〜16の偶数。
     */
    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
        tagLength: Int,
    ): ByteArray {
        val core = CcmCore(key, nonce, plaintext.size, tagLength)
        return core.applyKeyStream(plaintext) + core.authenticationTag(associatedData, plaintext)
    }

    /**
     * [ciphertextWithTag]（暗号文＋末尾[tagLength]バイトのタグ）を復号して平文を返す。
     * 認証タグが一致しない場合、長さが足りない場合はnullを返す（例外を投げない）。
     * 受信データは相手方から来るため、改ざん・ノイズを異常系ではなく戻り値で扱う。
     */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
        ciphertextWithTag: ByteArray,
        tagLength: Int,
    ): ByteArray? {
        if (ciphertextWithTag.size < tagLength) return null
        val boundary = ciphertextWithTag.size - tagLength
        val core = CcmCore(key, nonce, boundary, tagLength)
        val plaintext = core.applyKeyStream(ciphertextWithTag.copyOfRange(0, boundary))
        val expected = core.authenticationTag(associatedData, plaintext)
        val received = ciphertextWithTag.copyOfRange(boundary, ciphertextWithTag.size)
        return if (core.constantTimeEquals(expected, received)) plaintext else null
    }
}

/**
 * 1回の暗号化・復号ぶんのCCM計算をまとめた内部クラス。
 * 鍵・nonce・タグ長は呼び出しごとに固定のため、状態として持たせることで各処理の引数を減らしている。
 */
private class CcmCore(
    key: ByteArray,
    private val nonce: ByteArray,
    payloadSize: Int,
    private val tagLength: Int,
) {
    private val lengthFieldSize = CCM_BLOCK_SIZE - 1 - nonce.size
    private val cipher: Cipher

    init {
        require(key.size == CCM_KEY_SIZE) { "AES-CCM key must be 128 bits ($CCM_KEY_SIZE bytes)" }
        require(nonce.size in CCM_NONCE_MIN_SIZE..CCM_NONCE_MAX_SIZE) { "AES-CCM nonce must be 7..13 bytes" }
        require(tagLength in CCM_TAG_MIN_SIZE..CCM_BLOCK_SIZE && tagLength % 2 == 0) {
            "AES-CCM tag length must be an even 4..16"
        }
        require(fitsInLengthField(payloadSize)) { "Payload is too long for this nonce length" }
        cipher =
            Cipher.getInstance("AES/ECB/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            }
    }

    /** B0・AAD・平文をCBC-MACへ通し、カウンタ0の鍵ストリームでマスクした認証タグ。 */
    fun authenticationTag(
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val blocks =
            firstBlock(associatedData.isNotEmpty(), plaintext.size) +
                encodeAssociatedData(associatedData) +
                padToBlockSize(plaintext)
        var state = ByteArray(CCM_BLOCK_SIZE)
        for (offset in blocks.indices step CCM_BLOCK_SIZE) {
            state = cipher.doFinal(xor(state, blocks, CCM_BLOCK_SIZE, offset))
        }
        return xor(state, keyStreamBlock(0), tagLength)
    }

    /** CTRモードの鍵ストリームを[data]へXORする（暗号化・復号で同じ処理）。 */
    fun applyKeyStream(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (offset in data.indices step CCM_BLOCK_SIZE) {
            val block = keyStreamBlock(offset / CCM_BLOCK_SIZE + 1)
            for (i in offset until minOf(offset + CCM_BLOCK_SIZE, data.size)) {
                result[i] = (data[i].toInt() xor block[i - offset].toInt()).toByte()
            }
        }
        return result
    }

    /** タグ比較は内容によって所要時間が変わらない形で行う（早期returnしない）。 */
    fun constantTimeEquals(
        left: ByteArray,
        right: ByteArray,
    ): Boolean {
        if (left.size != right.size) return false
        var diff = 0
        for (i in left.indices) {
            diff = diff or (left[i].toInt() xor right[i].toInt())
        }
        return diff == 0
    }

    private fun fitsInLengthField(payloadSize: Int): Boolean =
        lengthFieldSize >= Integer.BYTES || payloadSize < (1L shl (Byte.SIZE_BITS * lengthFieldSize))

    /** CCMのB0ブロック（フラグ＋nonce＋平文長）。 */
    private fun firstBlock(
        hasAssociatedData: Boolean,
        payloadSize: Int,
    ): ByteArray {
        val block = ByteArray(CCM_BLOCK_SIZE)
        val adataFlag = if (hasAssociatedData) CCM_ADATA_FLAG else 0
        block[0] = (adataFlag or (((tagLength - 2) / 2) shl 3) or (lengthFieldSize - 1)).toByte()
        nonce.copyInto(block, destinationOffset = 1)
        writeBigEndian(block, CCM_BLOCK_SIZE - lengthFieldSize, payloadSize, lengthFieldSize)
        return block
    }

    /**
     * AADを「長さ（2バイトのビッグエンディアン）＋本体」でエンコードし、ブロック長へ0埋めする。
     * 本実装が扱うAADは常に1バイト（Sesameの仕様で`0x00`固定）のため、RFC 3610が定義する
     * より長いAAD向けの可変長エンコードは実装しない。
     */
    private fun encodeAssociatedData(associatedData: ByteArray): ByteArray {
        if (associatedData.isEmpty()) return ByteArray(0)
        require(associatedData.size < CCM_AAD_MAX_SHORT_LENGTH) {
            "Associated data longer than 0xFF00 is not supported"
        }
        val encoded = ByteArray(CCM_AAD_LENGTH_PREFIX_SIZE + associatedData.size)
        writeBigEndian(encoded, 0, associatedData.size, CCM_AAD_LENGTH_PREFIX_SIZE)
        associatedData.copyInto(encoded, destinationOffset = CCM_AAD_LENGTH_PREFIX_SIZE)
        return padToBlockSize(encoded)
    }

    /** CCMのA_iブロックを暗号化した鍵ストリーム1ブロック。[counter]が0のブロックはタグのマスクに使う。 */
    private fun keyStreamBlock(counter: Int): ByteArray {
        val block = ByteArray(CCM_BLOCK_SIZE)
        block[0] = (lengthFieldSize - 1).toByte()
        nonce.copyInto(block, destinationOffset = 1)
        writeBigEndian(block, CCM_BLOCK_SIZE - lengthFieldSize, counter, lengthFieldSize)
        return cipher.doFinal(block)
    }

    private fun writeBigEndian(
        destination: ByteArray,
        offset: Int,
        value: Int,
        size: Int,
    ) {
        for (i in 0 until size) {
            destination[offset + size - 1 - i] = ((value ushr (Byte.SIZE_BITS * i)) and CCM_BYTE_MASK).toByte()
        }
    }

    private fun padToBlockSize(data: ByteArray): ByteArray {
        val remainder = data.size % CCM_BLOCK_SIZE
        if (data.isEmpty() || remainder == 0) return data
        return data.copyOf(data.size + (CCM_BLOCK_SIZE - remainder))
    }

    private fun xor(
        left: ByteArray,
        right: ByteArray,
        size: Int,
        rightOffset: Int = 0,
    ): ByteArray {
        val result = ByteArray(size)
        for (i in 0 until size) {
            result[i] = (left[i].toInt() xor right[rightOffset + i].toInt()).toByte()
        }
        return result
    }
}

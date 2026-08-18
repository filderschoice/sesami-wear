package com.sesamiwear.core.api

import com.sesamiwear.core.crypto.AesCmac
import java.time.Instant

/**
 * Sesame API施錠/解錠コマンドの署名（`sign`フィールド）を生成する。
 * アルゴリズムはpysesame3（https://github.com/mochipon/pysesame3）の
 * pysesame3/cloud.py `SesameCloud.getSign`を参照した：
 * 現在のUnixタイムスタンプ（秒）を4バイト・リトルエンディアンにエンコードし、
 * 先頭バイトを除いた3バイト（インデックス1〜3）をメッセージとしてAES-CMACを計算し、
 * 16バイトの出力全体を32文字のhex文字列にする（先頭数バイトへの切り詰めは行わない）。
 */
internal object SesameCommandSigner {
    private const val TIMESTAMP_BYTE_LENGTH = 4
    private const val MESSAGE_START_INDEX = 1

    fun sign(
        secretKey: ByteArray,
        unixTimeSeconds: Long = Instant.now().epochSecond,
    ): String {
        val message = timestampMessageBytes(unixTimeSeconds)
        val mac = AesCmac.compute(secretKey, message)
        return mac.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private fun timestampMessageBytes(unixTimeSeconds: Long): ByteArray {
        val littleEndian = ByteArray(TIMESTAMP_BYTE_LENGTH)
        var remaining = unixTimeSeconds
        for (i in 0 until TIMESTAMP_BYTE_LENGTH) {
            littleEndian[i] = (remaining and 0xFF).toByte()
            remaining = remaining shr Byte.SIZE_BITS
        }
        return littleEndian.copyOfRange(MESSAGE_START_INDEX, TIMESTAMP_BYTE_LENGTH)
    }
}

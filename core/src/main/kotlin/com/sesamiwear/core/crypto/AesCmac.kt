package com.sesamiwear.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 4493 (AES-CMAC) の実装。
 * Sesame APIの施錠/解錠コマンド署名（secretKeyによるコマンド署名）に使用する。
 */
object AesCmac {
    private const val BLOCK_SIZE = 16
    private const val KEY_SIZE = 16
    private const val CONST_RB: Byte = 0x87.toByte()
    private const val MSB_MASK = 0x80

    fun compute(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray {
        require(key.size == KEY_SIZE) { "AES-CMAC key must be 128 bits ($KEY_SIZE bytes)" }

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        val k1 = generateSubkey(cipher.doFinal(ByteArray(BLOCK_SIZE)))
        val k2 = generateSubkey(k1)

        val blockCount = if (message.isEmpty()) 1 else (message.size + BLOCK_SIZE - 1) / BLOCK_SIZE
        val isLastBlockComplete = message.isNotEmpty() && message.size % BLOCK_SIZE == 0
        val lastBlockOffset = (blockCount - 1) * BLOCK_SIZE
        val lastBlock =
            if (isLastBlockComplete) {
                xorBlocks(message.copyOfRange(lastBlockOffset, lastBlockOffset + BLOCK_SIZE), k1)
            } else {
                xorBlocks(padBlock(message, lastBlockOffset), k2)
            }

        var x = ByteArray(BLOCK_SIZE)
        for (i in 0 until blockCount - 1) {
            val block = message.copyOfRange(i * BLOCK_SIZE, (i + 1) * BLOCK_SIZE)
            x = cipher.doFinal(xorBlocks(x, block))
        }
        return cipher.doFinal(xorBlocks(x, lastBlock))
    }

    private fun padBlock(
        message: ByteArray,
        offset: Int,
    ): ByteArray {
        val block = ByteArray(BLOCK_SIZE)
        val remaining = message.size - offset
        message.copyInto(block, destinationOffset = 0, startIndex = offset, endIndex = offset + remaining)
        block[remaining] = MSB_MASK.toByte()
        return block
    }

    private fun generateSubkey(input: ByteArray): ByteArray {
        val msbSet = (input[0].toInt() and MSB_MASK) != 0
        val shifted = shiftLeftOneBit(input)
        return if (msbSet) xorBlocks(shifted, rbBlock()) else shifted
    }

    private fun rbBlock(): ByteArray {
        val block = ByteArray(BLOCK_SIZE)
        block[BLOCK_SIZE - 1] = CONST_RB
        return block
    }

    private fun shiftLeftOneBit(input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        var carry = 0
        for (i in input.indices.reversed()) {
            val current = input[i].toInt() and 0xFF
            output[i] = ((current shl 1) or carry).toByte()
            carry = (current shr 7) and 0x01
        }
        return output
    }

    private fun xorBlocks(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray {
        val result = ByteArray(a.size)
        for (i in a.indices) {
            result[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return result
    }
}

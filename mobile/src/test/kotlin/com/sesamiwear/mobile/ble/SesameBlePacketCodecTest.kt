package com.sesamiwear.mobile.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLEパケットの分割と再組み立ての検証（BL-151）。
 * ヘッダのビット配置はgomalock（MIT）の`PacketType`に合わせている。
 */
class SesameBlePacketCodecTest {
    @Test
    fun `short payload fits in a single packet flagged as beginning and end`() {
        val packets = SesameBlePacketCodec.split(byteArrayOf(1, 2, 3), isEncrypted = false)
        assertEquals(1, packets.size)
        // BEGINNING(0b001) | PLAINTEXT_END(0b010) = 0b011
        assertArrayEquals(byteArrayOf(0b011, 1, 2, 3), packets[0])
    }

    @Test
    fun `encrypted payload marks the last packet with the encrypted end flag`() {
        val packets = SesameBlePacketCodec.split(byteArrayOf(1), isEncrypted = true)
        // BEGINNING(0b001) | ENCRYPTED_END(0b100) = 0b101
        assertArrayEquals(byteArrayOf(0b101, 1), packets[0])
    }

    @Test
    fun `payload longer than the mtu is split with only the last packet flagged as end`() {
        val data = ByteArray(SesameBlePacketCodec.MAX_PAYLOAD_SIZE * 2 + 1) { it.toByte() }
        val packets = SesameBlePacketCodec.split(data, isEncrypted = false)
        assertEquals(3, packets.size)
        assertEquals(0b001, packets[0][0].toInt())
        assertEquals(0b000, packets[1][0].toInt())
        assertEquals(0b010, packets[2][0].toInt())
        assertTrue(packets.all { it.size <= SesameBleProtocol.MTU_SIZE })
        assertArrayEquals(data, packets.fold(ByteArray(0)) { acc, p -> acc + p.copyOfRange(1, p.size) })
    }

    @Test
    fun `payload that is an exact multiple of the mtu does not emit a trailing empty packet`() {
        val data = ByteArray(SesameBlePacketCodec.MAX_PAYLOAD_SIZE * 2) { it.toByte() }
        val packets = SesameBlePacketCodec.split(data, isEncrypted = false)
        assertEquals(2, packets.size)
        assertEquals(0b010, packets[1][0].toInt())
    }

    @Test
    fun `empty payload still emits one header only packet`() {
        val packets = SesameBlePacketCodec.split(ByteArray(0), isEncrypted = false)
        assertEquals(1, packets.size)
        assertArrayEquals(byteArrayOf(0b011), packets[0])
    }

    @Test
    fun `assembler returns the message only once the end packet arrives`() {
        val assembler = SesameBlePacketAssembler()
        assertNull(assembler.append(byteArrayOf(0b001, 1, 2)))
        assertNull(assembler.append(byteArrayOf(0b000, 3)))
        val message = assembler.append(byteArrayOf(0b010, 4))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), message?.payload)
        assertFalse(message!!.isEncrypted)
    }

    @Test
    fun `assembler reports the encrypted flag from the end packet`() {
        val assembler = SesameBlePacketAssembler()
        val message = assembler.append(byteArrayOf(0b101, 9))
        assertTrue(message!!.isEncrypted)
        assertArrayEquals(byteArrayOf(9), message.payload)
    }

    @Test
    fun `a new beginning packet discards a half assembled message`() {
        val assembler = SesameBlePacketAssembler()
        assembler.append(byteArrayOf(0b001, 1, 2))
        val message = assembler.append(byteArrayOf(0b011, 7))
        assertArrayEquals(byteArrayOf(7), message?.payload)
    }

    @Test
    fun `assembler starts a fresh message after returning one`() {
        val assembler = SesameBlePacketAssembler()
        assembler.append(byteArrayOf(0b011, 1))
        val second = assembler.append(byteArrayOf(0b011, 2))
        assertArrayEquals(byteArrayOf(2), second?.payload)
    }

    @Test
    fun `reset discards the partially assembled buffer`() {
        val assembler = SesameBlePacketAssembler()
        assembler.append(byteArrayOf(0b001, 1, 2))
        assembler.reset()
        val message = assembler.append(byteArrayOf(0b010, 3))
        assertArrayEquals(byteArrayOf(3), message?.payload)
    }

    @Test
    fun `empty packets are ignored`() {
        assertNull(SesameBlePacketAssembler().append(ByteArray(0)))
    }
}

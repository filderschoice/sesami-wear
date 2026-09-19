package com.sesamiwear.mobile.ble

import com.sesamiwear.mobile.ble.SesameBleProtocol.ItemCode
import com.sesamiwear.mobile.ble.SesameBleProtocol.ResultCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 受信メッセージの解釈と送信コマンドの組み立ての検証（BL-151）。
 * 未知のコード・短すぎるメッセージは例外ではなくnullで返すことを確認する。
 */
class SesameBleMessageTest {
    @Test
    fun `parses a successful response with its payload`() {
        // op=RESPONSE(0x07), item=LOGIN(2), result=SUCCESS(0), payload=0A0B
        val parsed = SesameBleMessage.parse(byteArrayOf(0x07, 2, 0, 0x0A, 0x0B))
        val response = (parsed as SesameBleMessage.Parsed.AsResponse).response
        assertEquals(ItemCode.LOGIN, response.itemCode)
        assertEquals(ResultCode.SUCCESS, response.resultCode)
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), response.payload)
    }

    @Test
    fun `parses a rejected response`() {
        val parsed = SesameBleMessage.parse(byteArrayOf(0x07, 82, 4))
        val response = (parsed as SesameBleMessage.Parsed.AsResponse).response
        assertEquals(ItemCode.LOCK, response.itemCode)
        assertEquals(ResultCode.INVALID_SIG, response.resultCode)
        assertArrayEquals(ByteArray(0), response.payload)
    }

    @Test
    fun `parses a publish notification`() {
        // op=PUBLISH(0x08), item=INITIAL(14), payload=セッショントークン4バイト
        val parsed = SesameBleMessage.parse(byteArrayOf(0x08, 14, 0x11, 0x22, 0x33, 0x44))
        val publish = (parsed as SesameBleMessage.Parsed.AsPublish).publish
        assertEquals(ItemCode.INITIAL, publish.itemCode)
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33, 0x44), publish.payload)
    }

    @Test
    fun `returns null for opcodes the app does not handle`() {
        assertNull(SesameBleMessage.parse(byteArrayOf(0x01, 2, 0)))
    }

    @Test
    fun `returns null for item codes the app does not handle`() {
        // item=104 (RESET) は本アプリの定義に無い。
        assertNull(SesameBleMessage.parse(byteArrayOf(0x07, 104, 0)))
    }

    @Test
    fun `returns null for unknown result codes`() {
        assertNull(SesameBleMessage.parse(byteArrayOf(0x07, 2, 99)))
    }

    @Test
    fun `returns null for messages that are too short`() {
        assertNull(SesameBleMessage.parse(ByteArray(0)))
        assertNull(SesameBleMessage.parse(byteArrayOf(0x07, 2)))
        assertNull(SesameBleMessage.parse(byteArrayOf(0x08)))
    }

    @Test
    fun `encodes a command as the item code followed by the payload`() {
        assertArrayEquals(
            byteArrayOf(82, 1, 2),
            SesameBleMessage.encodeCommand(ItemCode.LOCK, byteArrayOf(1, 2)),
        )
        assertArrayEquals(byteArrayOf(81), SesameBleMessage.encodeCommand(ItemCode.MECH_STATUS))
    }

    @Test
    fun `encodes a history tag with a leading length byte`() {
        val encoded = SesameBleMessage.encodeHistoryTag("abc")
        assertArrayEquals(byteArrayOf(3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte()), encoded)
    }

    @Test
    fun `truncates a long ascii history tag to the maximum length`() {
        val encoded = SesameBleMessage.encodeHistoryTag("a".repeat(40))
        assertEquals(SesameBleProtocol.HISTORY_TAG_MAX_LENGTH, encoded[0].toInt())
        assertEquals(SesameBleProtocol.HISTORY_TAG_MAX_LENGTH + 1, encoded.size)
    }

    @Test
    fun `truncates a multibyte history tag without splitting a character`() {
        // 「あ」はUTF-8で3バイト。20バイト上限には6文字（18バイト）まで入る。
        val encoded = SesameBleMessage.encodeHistoryTag("あ".repeat(10))
        assertEquals(18, encoded[0].toInt())
        val decoded = String(encoded.copyOfRange(1, encoded.size), Charsets.UTF_8)
        assertEquals("あ".repeat(6), decoded)
    }

    @Test
    fun `encodes an empty history tag`() {
        assertArrayEquals(byteArrayOf(0), SesameBleMessage.encodeHistoryTag(""))
    }
}

package com.sesamiwear.core.display

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameControlTileContentTest {
    private fun device(n: Int) = SesameDeviceSummary(uuid = "uuid-$n", displayName = "玄関$n")

    private fun devices(count: Int) = (1..count).map(::device)

    @Test
    fun `no devices gives no content`() {
        assertNull(SesameControlTileContent.build(emptyList(), { null }, phoneConnected = true))
    }

    @Test
    fun `single device uses plain labels and targets that device`() {
        val content = SesameControlTileContent.build(devices(1), { true }, phoneConnected = true)!!
        assertEquals("施錠", content.lockLabel)
        assertEquals("解錠", content.unlockLabel)
        assertEquals("uuid-1", content.targetUuid)
        assertEquals(1, content.rows.size)
        assertEquals(0, content.overflowCount)
        assertNull(content.overflowText)
    }

    @Test
    fun `two devices use all-device labels and target`() {
        val content = SesameControlTileContent.build(devices(2), { true }, phoneConnected = true)!!
        assertEquals("全施錠", content.lockLabel)
        assertEquals("全解錠", content.unlockLabel)
        assertEquals(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, content.targetUuid)
        assertEquals(listOf("uuid-1", "uuid-2"), content.rows.map { it.uuid })
        assertEquals(0, content.overflowCount)
    }

    @Test
    fun `more devices than the row limit collapse the last row into an overflow count`() {
        val content = SesameControlTileContent.build(devices(5), { true }, phoneConnected = true)!!
        assertEquals(SesameControlTileContent.MAX_ROWS - 1, content.rows.size)
        assertEquals(listOf("uuid-1"), content.rows.map { it.uuid })
        assertEquals(4, content.overflowCount)
        assertEquals("他4台", content.overflowText)
        assertEquals(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, content.targetUuid)
    }

    @Test
    fun `exactly the row limit shows every device without overflow`() {
        val count = SesameControlTileContent.MAX_ROWS
        val content = SesameControlTileContent.build(devices(count), { true }, phoneConnected = true)!!
        assertEquals(count, content.rows.size)
        assertEquals(0, content.overflowCount)
    }

    @Test
    fun `row state follows the last synced lock state`() {
        val locks = mapOf("uuid-1" to true, "uuid-2" to false)
        val rows = SesameControlTileContent.build(devices(2), { locks[it] }, phoneConnected = true)!!.rows
        assertEquals(TileDisplayState.LOCKED, rows[0].state)
        assertEquals("🔒施錠中", rows[0].statusText)
        assertEquals(TileDisplayState.UNLOCKED, rows[1].state)
        assertEquals("🔓解錠中", rows[1].statusText)
        assertTrue(rows.all { it.tappable })
    }

    @Test
    fun `unknown state stays tappable so the chooser can still be opened`() {
        val row = SesameControlTileContent.build(devices(1), { null }, phoneConnected = true)!!.rows.single()
        assertEquals(TileDisplayState.UNKNOWN, row.state)
        assertTrue(row.tappable)
    }

    @Test
    fun `disconnected phone disables buttons and rows`() {
        val content = SesameControlTileContent.build(devices(2), { true }, phoneConnected = false)!!
        assertFalse(content.buttonsEnabled)
        assertTrue(content.rows.all { it.state == TileDisplayState.DISCONNECTED && !it.tappable })
        assertTrue(content.lockDescription.contains("スマホ未接続"))
    }

    @Test
    fun `blank display name falls back to uuid`() {
        val blank = listOf(SesameDeviceSummary(uuid = "uuid-x", displayName = " "))
        val row = SesameControlTileContent.build(blank, { true }, phoneConnected = true)!!.rows.single()
        assertEquals("uuid-x", row.name)
    }

    @Test
    fun `descriptions tell lock runs immediately and unlock goes through confirmation`() {
        val content = SesameControlTileContent.build(devices(2), { true }, phoneConnected = true)!!
        assertEquals("全施錠 タップですぐに施錠", content.lockDescription)
        assertEquals("全解錠 タップして確認へ", content.unlockDescription)
        assertTrue(content.rows[0].description.contains("施錠・解錠を選ぶ"))
    }
}

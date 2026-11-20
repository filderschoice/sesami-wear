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
    fun `tier widths shrink toward the circle edge and set the chip capacities`() {
        // 2段（全体ボタン＋チップ1段）: 中心から50dpの辺の弦163dp − 余白8dp。
        assertEquals(155, SesameControlTileContent.tierWidthDp(2, 0))
        assertEquals(155, SesameControlTileContent.tierWidthDp(2, 1))
        // 3段: 上下の端（中心から76dp）は狭く、中段（24dp）は広い。
        assertEquals(109, SesameControlTileContent.tierWidthDp(3, 0))
        assertEquals(177, SesameControlTileContent.tierWidthDp(3, 1))
        assertEquals(109, SesameControlTileContent.tierWidthDp(3, 2))
        assertEquals(listOf(3), SesameControlTileContent.chipRowCapacities(2))
        assertEquals(listOf(3, 2), SesameControlTileContent.chipRowCapacities(3))
        assertEquals(3, SesameControlTileContent.singleRowCapacity)
        assertEquals(5, SesameControlTileContent.maxChips)
    }

    @Test
    fun `every chip is at least the tap target width and height`() {
        for (tiers in 2..3) {
            val capacities = SesameControlTileContent.chipRowCapacities(tiers)
            capacities.forEachIndexed { i, n ->
                val width = SesameControlTileContent.tierWidthDp(tiers, i + 1)
                val chipWidth = (width - SesameControlTileContent.GAP_DP * (n - 1)) / n
                assertTrue(
                    "tiers=$tiers row=$i width=$chipWidth",
                    chipWidth >= SesameControlTileContent.MIN_CHIP_WIDTH_DP,
                )
            }
        }
        assertTrue(SesameControlTileContent.TIER_HEIGHT_DP >= 48)
    }

    @Test
    fun `single device uses plain labels and targets that device`() {
        val content = SesameControlTileContent.build(devices(1), { true }, phoneConnected = true)!!
        assertEquals("施錠", content.lockLabel)
        assertEquals("解錠", content.unlockLabel)
        assertEquals("uuid-1", content.targetUuid)
        assertEquals(1, content.chips.size)
        assertEquals(1, content.chipRows.size)
        assertEquals(0, content.overflowCount)
    }

    @Test
    fun `up to three devices sit side by side in one row`() {
        val content = SesameControlTileContent.build(devices(3), { true }, phoneConnected = true)!!
        assertEquals("全施錠", content.lockLabel)
        assertEquals("全解錠", content.unlockLabel)
        assertEquals(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, content.targetUuid)
        assertEquals(1, content.chipRows.size)
        assertEquals(listOf("uuid-1", "uuid-2", "uuid-3"), content.chipRows[0].chips.map { it.uuid })
        assertEquals(155, content.buttonRowWidthDp)
        assertEquals(155, content.chipRows[0].widthDp)
        assertEquals(0, content.overflowCount)
    }

    @Test
    fun `four devices use two balanced rows`() {
        val content = SesameControlTileContent.build(devices(4), { true }, phoneConnected = true)!!
        assertEquals(listOf(2, 2), content.chipRows.map { it.chips.size })
        assertEquals(listOf(177, 109), content.chipRows.map { it.widthDp })
        assertEquals(109, content.buttonRowWidthDp)
        assertEquals(0, content.overflowCount)
    }

    @Test
    fun `exactly the chip limit shows every device without overflow`() {
        val content = SesameControlTileContent.build(devices(5), { true }, phoneConnected = true)!!
        assertEquals(listOf(3, 2), content.chipRows.map { it.chips.size })
        assertEquals(0, content.overflowCount)
        assertNull(content.chipRows.last().overflowText)
    }

    @Test
    fun `more devices than the chip limit turn the last chip into an overflow count`() {
        val content = SesameControlTileContent.build(devices(7), { true }, phoneConnected = true)!!
        assertEquals(SesameControlTileContent.maxChips - 1, content.chips.size)
        assertEquals(listOf(3, 1), content.chipRows.map { it.chips.size })
        assertEquals(3, content.overflowCount)
        assertNull(content.chipRows.first().overflowText)
        assertEquals("他3台", content.chipRows.last().overflowText)
        assertEquals(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, content.targetUuid)
    }

    @Test
    fun `six devices show four chips and an overflow of two`() {
        val content = SesameControlTileContent.build(devices(6), { true }, phoneConnected = true)!!
        assertEquals(4, content.chips.size)
        assertEquals(2, content.overflowCount)
        assertEquals(listOf(3, 1), content.chipRows.map { it.chips.size })
        assertEquals("他2台", content.chipRows.last().overflowText)
    }

    @Test
    fun `chip state follows the last synced lock state`() {
        val locks = mapOf("uuid-1" to true, "uuid-2" to false)
        val chips = SesameControlTileContent.build(devices(2), { locks[it] }, phoneConnected = true)!!.chips
        assertEquals(TileDisplayState.LOCKED, chips[0].state)
        assertEquals("🔒", chips[0].icon)
        assertEquals(TileDisplayState.UNLOCKED, chips[1].state)
        assertEquals("🔓", chips[1].icon)
        assertTrue(chips.all { it.tappable })
    }

    @Test
    fun `unknown state stays tappable so the chooser can still be opened`() {
        val chip = SesameControlTileContent.build(devices(1), { null }, phoneConnected = true)!!.chips.single()
        assertEquals(TileDisplayState.UNKNOWN, chip.state)
        assertTrue(chip.tappable)
    }

    @Test
    fun `disconnected phone disables buttons and chips`() {
        val content = SesameControlTileContent.build(devices(2), { true }, phoneConnected = false)!!
        assertFalse(content.buttonsEnabled)
        assertTrue(content.chips.all { it.state == TileDisplayState.DISCONNECTED && !it.tappable })
        assertTrue(content.lockDescription.contains("スマホ未接続"))
    }

    @Test
    fun `blank display name falls back to uuid`() {
        val blank = listOf(SesameDeviceSummary(uuid = "uuid-x", displayName = " "))
        val chip = SesameControlTileContent.build(blank, { true }, phoneConnected = true)!!.chips.single()
        assertEquals("uuid-x", chip.name)
    }

    @Test
    fun `short names are kept and long names are cut with an ellipsis`() {
        assertEquals("玄関", SesameControlTileContent.shortName("玄関"))
        assertEquals("裏口ドア", SesameControlTileContent.shortName("裏口ドア"))
        assertEquals("Garage", SesameControlTileContent.shortName("Garage"))
        assertEquals("倉庫の…", SesameControlTileContent.shortName("倉庫の奥の勝手口"))
        assertEquals("Front …", SesameControlTileContent.shortName("Front door"))
    }

    @Test
    fun `short names never split a surrogate pair`() {
        val door = "🚪"
        // 絵文字は2幅。幅6（=8-省略記号2）に3個まで入り、4個目で省略する。
        assertEquals(door.repeat(3) + "…", SesameControlTileContent.shortName(door.repeat(5)))
        // ちょうど4個（幅8）は省略しない。
        assertEquals(door.repeat(4), SesameControlTileContent.shortName(door.repeat(4)))
        // 半角1＋絵文字2の繰り返し。途中で切っても、孤立したサロゲートを残さない。
        val cut = SesameControlTileContent.shortName(("a" + door).repeat(6))
        assertTrue(cut.endsWith("…"))
        assertEquals(("a" + door).repeat(2) + "…", cut)
    }

    @Test
    fun `halfwidth katakana counts as one unit and fullwidth as two`() {
        // 半角カナ8文字は幅8でちょうど収まる。
        assertEquals("ｱｲｳｴｵｶｷｸ", SesameControlTileContent.shortName("ｱｲｳｴｵｶｷｸ"))
        // 9文字目で超えるので幅6=6文字＋省略記号。
        assertEquals("ｱｲｳｴｵｶ…", SesameControlTileContent.shortName("ｱｲｳｴｵｶｷｸｹ"))
        // 全角は2幅（4文字で8、5文字で省略）。
        assertEquals("あいうえ", SesameControlTileContent.shortName("あいうえ"))
        assertEquals("あいう…", SesameControlTileContent.shortName("あいうえお"))
    }

    @Test
    fun `descriptions use the full name and tell lock runs immediately and unlock goes through confirmation`() {
        val named = listOf(SesameDeviceSummary(uuid = "u1", displayName = "倉庫の奥の勝手口"), device(2))
        val content = SesameControlTileContent.build(named, { true }, phoneConnected = true)!!
        assertEquals("全施錠 タップですぐに施錠", content.lockDescription)
        assertEquals("全解錠 タップして確認へ", content.unlockDescription)
        assertEquals("倉庫の奥の勝手口 施錠中 タップして施錠・解錠を選ぶ", content.chips[0].description)
        assertEquals("倉庫の…", content.chips[0].name)
    }
}

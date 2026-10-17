package com.sesamiwear.wear.complication

import com.sesamiwear.core.TileDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameComplicationContentTest {
    @Test
    fun `state icons distinguish locked from anything that is open`() {
        assertEquals(
            SesameComplicationContent.StateIcon.LOCKED,
            SesameComplicationContent.stateIcon(TileDisplayState.LOCKED),
        )
        assertEquals(
            SesameComplicationContent.StateIcon.UNLOCKED,
            SesameComplicationContent.stateIcon(TileDisplayState.UNLOCKED),
        )
        // 一部解錠は施錠のアイコンにしない（全台施錠済みと誤読させない、BL-256）。
        assertEquals(
            SesameComplicationContent.StateIcon.UNLOCKED,
            SesameComplicationContent.stateIcon(TileDisplayState.MIXED),
        )
        assertEquals(
            SesameComplicationContent.StateIcon.UNKNOWN,
            SesameComplicationContent.stateIcon(TileDisplayState.UNKNOWN),
        )
    }

    @Test
    fun `battery gauge shows the percentage`() {
        assertEquals(SesameComplicationContent.BatteryGauge(85f, "85%"), SesameComplicationContent.batteryGauge(85))
    }

    @Test
    fun `battery gauge without a reading is empty and shows no number`() {
        assertEquals(
            SesameComplicationContent.BatteryGauge(0f, SesameComplicationContent.BATTERY_UNKNOWN_TEXT),
            SesameComplicationContent.batteryGauge(null),
        )
    }

    @Test
    fun `battery gauge clamps values out of range`() {
        assertEquals(100f, SesameComplicationContent.batteryGauge(120).value)
        assertEquals(0f, SesameComplicationContent.batteryGauge(-5).value)
    }

    @Test
    fun `battery description reads the state and the battery`() {
        assertEquals("玄関 施錠 電池85%", SesameComplicationContent.batteryDescription("玄関 施錠", 85))
        assertEquals("玄関 施錠 電池残量不明", SesameComplicationContent.batteryDescription("玄関 施錠", null))
    }

    @Test
    fun `short text is defined for every state`() {
        TileDisplayState.entries.forEach { state ->
            assertTrue(SesameComplicationContent.shortText(state).isNotBlank())
        }
    }

    @Test
    fun `short text distinguishes locked and unlocked`() {
        assertEquals("施錠", SesameComplicationContent.shortText(TileDisplayState.LOCKED))
        assertEquals("解錠", SesameComplicationContent.shortText(TileDisplayState.UNLOCKED))
    }

    @Test
    fun `short text for mixed state`() {
        assertEquals("混在", SesameComplicationContent.shortText(TileDisplayState.MIXED))
    }

    @Test
    fun `long text includes display name and state`() {
        assertEquals("玄関 施錠", SesameComplicationContent.longText("玄関", TileDisplayState.LOCKED))
        assertEquals("全デバイス 混在", SesameComplicationContent.longText("全デバイス", TileDisplayState.MIXED))
    }

    @Test
    fun `long text appends the freshness label when given`() {
        assertEquals(
            "玄関 施錠（3分前）",
            SesameComplicationContent.longText("玄関", TileDisplayState.LOCKED, "3分前"),
        )
        assertEquals(
            "玄関 不明（未取得）",
            SesameComplicationContent.longText("玄関", TileDisplayState.UNKNOWN, "未取得"),
        )
    }

    @Test
    fun `long text omits the freshness label for the demo device`() {
        assertEquals("デモ 施錠", SesameComplicationContent.longText("デモ", TileDisplayState.LOCKED, null))
    }

    @Test
    fun `long text is defined for every state`() {
        TileDisplayState.entries.forEach { state ->
            assertTrue(SesameComplicationContent.longText("玄関", state).isNotBlank())
        }
    }
}

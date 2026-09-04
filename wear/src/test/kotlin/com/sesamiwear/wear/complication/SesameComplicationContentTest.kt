package com.sesamiwear.wear.complication

import com.sesamiwear.core.TileDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameComplicationContentTest {
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
    fun `long text is defined for every state`() {
        TileDisplayState.entries.forEach { state ->
            assertTrue(SesameComplicationContent.longText("玄関", state).isNotBlank())
        }
    }
}

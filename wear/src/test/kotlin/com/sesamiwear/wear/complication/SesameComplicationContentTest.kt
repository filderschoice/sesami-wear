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
}

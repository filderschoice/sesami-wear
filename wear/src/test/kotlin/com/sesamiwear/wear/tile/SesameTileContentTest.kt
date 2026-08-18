package com.sesamiwear.wear.tile

import com.sesamiwear.core.TileDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameTileContentTest {
    @Test
    fun `status label is defined for every state`() {
        TileDisplayState.entries.forEach { state ->
            assertEquals(true, SesameTileContent.statusLabel(state).isNotBlank())
        }
    }

    @Test
    fun `action label offers the opposite action for actionable states`() {
        assertEquals("タップで解錠", SesameTileContent.actionLabel(TileDisplayState.LOCKED))
        assertEquals("タップで施錠", SesameTileContent.actionLabel(TileDisplayState.UNLOCKED))
    }

    @Test
    fun `action label is absent for non-actionable states`() {
        assertNull(SesameTileContent.actionLabel(TileDisplayState.IN_PROGRESS))
        assertNull(SesameTileContent.actionLabel(TileDisplayState.DISCONNECTED))
        assertNull(SesameTileContent.actionLabel(TileDisplayState.UNKNOWN))
    }
}

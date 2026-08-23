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
    fun `status icon is defined for every state`() {
        TileDisplayState.entries.forEach { state ->
            assertEquals(true, SesameTileContent.statusIcon(state).isNotBlank())
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

    @Test
    fun `mixed state offers lock-all as the action`() {
        assertEquals("タップで全施錠", SesameTileContent.actionLabel(TileDisplayState.MIXED))
    }

    @Test
    fun `isAllDevices prefixes locked and unlocked labels with zen`() {
        assertEquals("全施錠中", SesameTileContent.statusLabel(TileDisplayState.LOCKED, isAllDevices = true))
        assertEquals("全解錠中", SesameTileContent.statusLabel(TileDisplayState.UNLOCKED, isAllDevices = true))
        assertEquals("タップで全解錠", SesameTileContent.actionLabel(TileDisplayState.LOCKED, isAllDevices = true))
        assertEquals("タップで全施錠", SesameTileContent.actionLabel(TileDisplayState.UNLOCKED, isAllDevices = true))
    }

    @Test
    fun `background color distinguishes locked, unlocked, mixed and in-progress`() {
        val locked = SesameTileContent.backgroundColorArgb(TileDisplayState.LOCKED)
        val unlocked = SesameTileContent.backgroundColorArgb(TileDisplayState.UNLOCKED)
        val mixed = SesameTileContent.backgroundColorArgb(TileDisplayState.MIXED)
        val inProgress = SesameTileContent.backgroundColorArgb(TileDisplayState.IN_PROGRESS)

        assertEquals(setOf(locked, unlocked, mixed, inProgress).size, 4)
    }

    @Test
    fun `disconnected and unknown share the same neutral color`() {
        assertEquals(
            SesameTileContent.backgroundColorArgb(TileDisplayState.DISCONNECTED),
            SesameTileContent.backgroundColorArgb(TileDisplayState.UNKNOWN),
        )
    }

    @Test
    fun `status text color differs between in-progress and other states`() {
        val inProgressText = SesameTileContent.statusTextColorArgb(TileDisplayState.IN_PROGRESS)
        val lockedText = SesameTileContent.statusTextColorArgb(TileDisplayState.LOCKED)

        assertEquals(false, inProgressText == lockedText)
    }

    @Test
    fun `status text color is defined for every state`() {
        TileDisplayState.entries.forEach { state ->
            SesameTileContent.statusTextColorArgb(state)
        }
    }
}

package com.sesamiwear.wear.tile

import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameTileActionsTest {
    @Test
    fun `locked state offers unlock`() {
        assertEquals(SesameCommand.UNLOCK, SesameTileActions.commandForState(TileDisplayState.LOCKED))
    }

    @Test
    fun `unlocked state offers lock`() {
        assertEquals(SesameCommand.LOCK, SesameTileActions.commandForState(TileDisplayState.UNLOCKED))
    }

    @Test
    fun `non-actionable states offer no command`() {
        assertNull(SesameTileActions.commandForState(TileDisplayState.IN_PROGRESS))
        assertNull(SesameTileActions.commandForState(TileDisplayState.DISCONNECTED))
        assertNull(SesameTileActions.commandForState(TileDisplayState.UNKNOWN))
    }
}

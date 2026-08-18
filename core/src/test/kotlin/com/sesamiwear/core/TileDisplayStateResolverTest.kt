package com.sesamiwear.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TileDisplayStateResolverTest {
    @Test
    fun `disconnected takes precedence over everything else`() {
        val result =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = false,
                isCommandInProgress = true,
                isLocked = true,
            )
        assertEquals(TileDisplayState.DISCONNECTED, result)
    }

    @Test
    fun `in progress takes precedence over lock state when connected`() {
        val result =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = true,
                isCommandInProgress = true,
                isLocked = false,
            )
        assertEquals(TileDisplayState.IN_PROGRESS, result)
    }

    @Test
    fun `resolves locked state`() {
        val result =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = true,
                isCommandInProgress = false,
                isLocked = true,
            )
        assertEquals(TileDisplayState.LOCKED, result)
    }

    @Test
    fun `resolves unlocked state`() {
        val result =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = true,
                isCommandInProgress = false,
                isLocked = false,
            )
        assertEquals(TileDisplayState.UNLOCKED, result)
    }

    @Test
    fun `resolves unknown state when lock state has not been fetched yet`() {
        val result =
            TileDisplayStateResolver.resolve(
                isPhoneConnected = true,
                isCommandInProgress = false,
                isLocked = null,
            )
        assertEquals(TileDisplayState.UNKNOWN, result)
    }

    @Test
    fun `only locked and unlocked states are actionable`() {
        assertEquals(true, TileDisplayState.LOCKED.isActionable)
        assertEquals(true, TileDisplayState.UNLOCKED.isActionable)
        assertEquals(false, TileDisplayState.IN_PROGRESS.isActionable)
        assertEquals(false, TileDisplayState.DISCONNECTED.isActionable)
        assertEquals(false, TileDisplayState.UNKNOWN.isActionable)
    }
}

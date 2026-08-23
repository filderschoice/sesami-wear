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
    fun `locked, unlocked and mixed states are actionable`() {
        assertEquals(true, TileDisplayState.LOCKED.isActionable)
        assertEquals(true, TileDisplayState.UNLOCKED.isActionable)
        assertEquals(true, TileDisplayState.MIXED.isActionable)
        assertEquals(false, TileDisplayState.IN_PROGRESS.isActionable)
        assertEquals(false, TileDisplayState.DISCONNECTED.isActionable)
        assertEquals(false, TileDisplayState.UNKNOWN.isActionable)
    }

    @Test
    fun `aggregate resolves locked when every device is locked`() {
        val result =
            TileDisplayStateResolver.resolveAggregate(
                isPhoneConnected = true,
                isCommandInProgress = false,
                lockStates = listOf(true, true, true),
            )
        assertEquals(TileDisplayState.LOCKED, result)
    }

    @Test
    fun `aggregate resolves unlocked when every device is unlocked`() {
        val result =
            TileDisplayStateResolver.resolveAggregate(
                isPhoneConnected = true,
                isCommandInProgress = false,
                lockStates = listOf(false, false),
            )
        assertEquals(TileDisplayState.UNLOCKED, result)
    }

    @Test
    fun `aggregate resolves mixed when devices disagree`() {
        val result =
            TileDisplayStateResolver.resolveAggregate(
                isPhoneConnected = true,
                isCommandInProgress = false,
                lockStates = listOf(true, false, true),
            )
        assertEquals(TileDisplayState.MIXED, result)
    }

    @Test
    fun `aggregate resolves unknown when any device state is unknown`() {
        val result =
            TileDisplayStateResolver.resolveAggregate(
                isPhoneConnected = true,
                isCommandInProgress = false,
                lockStates = listOf(true, null),
            )
        assertEquals(TileDisplayState.UNKNOWN, result)
    }

    @Test
    fun `aggregate resolves unknown when there are no devices`() {
        val result =
            TileDisplayStateResolver.resolveAggregate(
                isPhoneConnected = true,
                isCommandInProgress = false,
                lockStates = emptyList(),
            )
        assertEquals(TileDisplayState.UNKNOWN, result)
    }

    @Test
    fun `aggregate disconnected takes precedence over lock states`() {
        val result =
            TileDisplayStateResolver.resolveAggregate(
                isPhoneConnected = false,
                isCommandInProgress = false,
                lockStates = listOf(true, true),
            )
        assertEquals(TileDisplayState.DISCONNECTED, result)
    }

    @Test
    fun `aggregate in progress takes precedence over lock states when connected`() {
        val result =
            TileDisplayStateResolver.resolveAggregate(
                isPhoneConnected = true,
                isCommandInProgress = true,
                lockStates = listOf(true, false),
            )
        assertEquals(TileDisplayState.IN_PROGRESS, result)
    }
}

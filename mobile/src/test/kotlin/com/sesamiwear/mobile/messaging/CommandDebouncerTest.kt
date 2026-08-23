package com.sesamiwear.mobile.messaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDebouncerTest {
    @Test
    fun `first call for a key is always processed`() {
        val debouncer = CommandDebouncer(windowMillis = 2000L) { 1_000L }

        assertTrue(debouncer.shouldProcess("uuid-1"))
    }

    @Test
    fun `call within the window for the same key is rejected`() {
        var now = 1_000L
        val debouncer = CommandDebouncer(windowMillis = 2000L) { now }

        assertTrue(debouncer.shouldProcess("uuid-1"))
        now += 500L
        assertFalse(debouncer.shouldProcess("uuid-1"))
    }

    @Test
    fun `call after the window for the same key is processed again`() {
        var now = 1_000L
        val debouncer = CommandDebouncer(windowMillis = 2000L) { now }

        assertTrue(debouncer.shouldProcess("uuid-1"))
        now += 2_001L
        assertTrue(debouncer.shouldProcess("uuid-1"))
    }

    @Test
    fun `different keys are debounced independently`() {
        val debouncer = CommandDebouncer(windowMillis = 2000L) { 1_000L }

        assertTrue(debouncer.shouldProcess("uuid-1"))
        assertTrue(debouncer.shouldProcess("uuid-2"))
    }
}

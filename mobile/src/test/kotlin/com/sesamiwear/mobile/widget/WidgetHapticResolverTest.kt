package com.sesamiwear.mobile.widget

import com.sesamiwear.core.haptics.HapticPattern
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetHapticResolverTest {
    @Test
    fun `a single success vibrates as success`() {
        assertEquals(HapticPattern.SUCCESS, WidgetHapticResolver.resolve(listOf(Outcome.SUCCESS)))
    }

    @Test
    fun `a single failure vibrates as failure`() {
        assertEquals(HapticPattern.FAILURE, WidgetHapticResolver.resolve(listOf(Outcome.FAILURE)))
    }

    @Test
    fun `a partial failure across devices vibrates as failure`() {
        assertEquals(
            HapticPattern.FAILURE,
            WidgetHapticResolver.resolve(listOf(Outcome.SUCCESS, Outcome.FAILURE)),
        )
    }

    @Test
    fun `a debounced tap does not vibrate`() {
        assertNull(WidgetHapticResolver.resolve(listOf(Outcome.DEBOUNCED)))
        assertNull(WidgetHapticResolver.resolve(listOf(Outcome.DEBOUNCED, Outcome.DEBOUNCED)))
    }

    @Test
    fun `a success mixed with a debounced device still vibrates as success`() {
        assertEquals(
            HapticPattern.SUCCESS,
            WidgetHapticResolver.resolve(listOf(Outcome.SUCCESS, Outcome.DEBOUNCED)),
        )
    }

    @Test
    fun `no target device does not vibrate`() {
        assertNull(WidgetHapticResolver.resolve(emptyList()))
    }
}

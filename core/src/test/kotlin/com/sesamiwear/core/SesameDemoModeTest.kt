package com.sesamiwear.core

import com.sesamiwear.core.api.SesameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameDemoModeTest {
    @Test
    fun `demo uuid does not collide with the all devices target`() {
        assertFalse(SesameDemoMode.isDemoDevice(SesameWearProtocol.ALL_DEVICES_TARGET_UUID))
        assertTrue(SesameDemoMode.isDemoDevice(SesameDemoMode.DEMO_DEVICE_UUID))
    }

    @Test
    fun `demo mode is available only when no device is registered`() {
        assertTrue(SesameDemoMode.isAvailable(emptyList()))
        assertFalse(SesameDemoMode.isAvailable(listOf(SesameDeviceSummary("uuid-1", "玄関"))))
    }

    @Test
    fun `selectable devices fall back to the demo device when nothing is registered`() {
        val devices = SesameDemoMode.selectableDevices(emptyList())

        assertEquals(1, devices.size)
        assertEquals(SesameDemoMode.DEMO_DEVICE_UUID, devices[0].uuid)
        assertEquals(SesameDemoMode.DEMO_DEVICE_DISPLAY_NAME, devices[0].displayName)
    }

    @Test
    fun `selectable devices never mix the demo device into registered devices`() {
        val registered = listOf(SesameDeviceSummary("uuid-1", "玄関"), SesameDeviceSummary("uuid-2", "勝手口"))

        val devices = SesameDemoMode.selectableDevices(registered)

        assertEquals(registered, devices)
        assertFalse(devices.any { SesameDemoMode.isDemoDevice(it.uuid) })
    }

    @Test
    fun `display state reflects the local lock state without depending on connectivity`() {
        assertEquals(TileDisplayState.LOCKED, SesameDemoMode.displayState(isLocked = true))
        assertEquals(TileDisplayState.UNLOCKED, SesameDemoMode.displayState(isLocked = false))
    }

    @Test
    fun `display state is always actionable so the tile can be tapped in demo mode`() {
        assertTrue(SesameDemoMode.displayState(isLocked = true).isActionable)
        assertTrue(SesameDemoMode.displayState(isLocked = false).isActionable)
    }

    @Test
    fun `commands toggle the demo lock state`() {
        assertTrue(SesameDemoMode.nextIsLocked(SesameCommand.LOCK))
        assertFalse(SesameDemoMode.nextIsLocked(SesameCommand.UNLOCK))
    }

    @Test
    fun `initial state is locked so the first demo operation is unlocking`() {
        assertEquals(TileDisplayState.LOCKED, SesameDemoMode.displayState(SesameDemoMode.INITIAL_IS_LOCKED))
    }
}

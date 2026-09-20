package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 経路が変わったときだけ知らせるための判定の検証（BL-190）。
 * 「初回は知らせない」「同じ経路が続く間は知らせない」「デバイスごとに独立」を固定する。
 */
class SesameRouteChangeTrackerTest {
    private val tracker = SesameRouteChangeTracker(InMemoryKeyValueStore())

    @Test
    fun `the first route is recorded without notifying`() {
        assertFalse(tracker.onRouteUsed(UUID, SesameStatusRoute.WEB_API))
    }

    @Test
    fun `the same route in a row does not notify`() {
        tracker.onRouteUsed(UUID, SesameStatusRoute.WEB_API)
        assertFalse(tracker.onRouteUsed(UUID, SesameStatusRoute.WEB_API))
    }

    @Test
    fun `switching routes notifies once in each direction`() {
        tracker.onRouteUsed(UUID, SesameStatusRoute.WEB_API)
        assertTrue(tracker.onRouteUsed(UUID, SesameStatusRoute.BLE))
        assertFalse(tracker.onRouteUsed(UUID, SesameStatusRoute.BLE))
        assertTrue(tracker.onRouteUsed(UUID, SesameStatusRoute.WEB_API))
    }

    @Test
    fun `each device is tracked on its own`() {
        tracker.onRouteUsed(UUID, SesameStatusRoute.WEB_API)
        assertFalse(tracker.onRouteUsed(OTHER_UUID, SesameStatusRoute.BLE))
        assertTrue(tracker.onRouteUsed(UUID, SesameStatusRoute.BLE))
    }

    @Test
    fun `a removed device starts over as a first time`() {
        tracker.onRouteUsed(UUID, SesameStatusRoute.WEB_API)
        tracker.remove(UUID)
        assertFalse(tracker.onRouteUsed(UUID, SesameStatusRoute.BLE))
    }

    private companion object {
        const val UUID = "11111111-2222-3333-4444-555555555555"
        const val OTHER_UUID = "99999999-8888-7777-6666-555555555555"
    }
}

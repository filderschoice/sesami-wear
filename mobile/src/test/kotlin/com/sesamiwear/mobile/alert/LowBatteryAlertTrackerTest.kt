package com.sesamiwear.mobile.alert

import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 電池残量が少なくなったときの通知の判定（BL-238）。下回ったときに1回だけ通知することを固定する。 */
class LowBatteryAlertTrackerTest {
    private val store = InMemoryKeyValueStore()
    private val tracker = LowBatteryAlertTracker(store)

    @Test
    fun `dropping below the threshold notifies once`() {
        assertFalse(tracker.shouldNotify(UUID, 20))
        assertTrue(tracker.shouldNotify(UUID, 19))
        assertFalse(tracker.shouldNotify(UUID, 15))
        assertFalse(tracker.shouldNotify(UUID, 5))
    }

    @Test
    fun `recovering above the threshold rearms the notification`() {
        assertTrue(tracker.shouldNotify(UUID, 10))
        assertFalse(tracker.shouldNotify(UUID, 100))
        assertTrue(tracker.shouldNotify(UUID, 18))
    }

    @Test
    fun `an unknown battery level changes nothing`() {
        assertTrue(tracker.shouldNotify(UUID, 10))
        assertFalse(tracker.shouldNotify(UUID, null))
        assertFalse(tracker.shouldNotify(UUID, 10))
    }

    @Test
    fun `each device is tracked separately`() {
        assertTrue(tracker.shouldNotify(UUID, 10))
        assertTrue(tracker.shouldNotify(OTHER_UUID, 10))
    }

    @Test
    fun `the notified state survives a new tracker over the same store`() {
        assertTrue(tracker.shouldNotify(UUID, 10))
        assertFalse(LowBatteryAlertTracker(store).shouldNotify(UUID, 10))
    }

    private companion object {
        const val UUID = "uuid-front"
        const val OTHER_UUID = "uuid-back"
    }
}

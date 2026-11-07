package com.sesamiwear.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameBleSightingTrackerTest {
    private fun tracker() = SesameBleSightingTracker(startedAtMillis = 0, lostAfterMillis = 6_000)

    @Test
    fun `starts as scanning`() {
        assertEquals(SesameBleProbeStage.SCANNING, tracker().initial().stage)
    }

    @Test
    fun `sighting is reported as found with the smoothed rssi`() {
        val tracker = tracker()
        assertEquals(SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -70), tracker.onSighting(-70, 1_000))
        // -70 + 0.3 * (-62 - -70) = -67.6 → -68
        assertEquals(-68, tracker.onSighting(-62, 2_000).rssiDbm)
    }

    @Test
    fun `not seen at all reports not found once after the limit`() {
        val tracker = tracker()
        assertNull(tracker.onTick(5_999))
        assertEquals(SesameBleProbeStage.NOT_FOUND, tracker.onTick(6_000)?.stage)
        assertNull(tracker.onTick(7_000))
    }

    @Test
    fun `losing sight reports not found once, and a new sighting recovers`() {
        val tracker = tracker()
        tracker.onSighting(-70, 1_000)
        assertNull(tracker.onTick(6_999))
        val lost = tracker.onTick(7_000)
        assertEquals(SesameBleProbeSnapshot(SesameBleProbeStage.NOT_FOUND), lost)
        assertNull(tracker.onTick(8_000))
        // 見失った間に蓄えた値は捨てており、戻ったときは生の値から始まる。
        assertEquals(SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -85), tracker.onSighting(-85, 9_000))
        assertNull(tracker.onTick(9_500))
    }
}

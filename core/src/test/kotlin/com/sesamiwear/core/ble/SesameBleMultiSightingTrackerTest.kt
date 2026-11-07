package com.sesamiwear.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameBleMultiSightingTrackerTest {
    private fun tracker() =
        SesameBleMultiSightingTracker(
            listOf("a", "b"),
            startedAtMillis = 0,
            lostAfterMillis = 6_000,
        )

    @Test
    fun `sighting is tracked per device`() {
        val tracker = tracker()

        assertEquals(
            SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -70),
            tracker.onSighting("a", -70, 1_000),
        )
        // bの平滑化はaの値に引きずられない。
        assertEquals(
            SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -90),
            tracker.onSighting("b", -90, 1_000),
        )
    }

    @Test
    fun `unknown device is ignored`() {
        assertNull(tracker().onSighting("zzz", -70, 1_000))
    }

    @Test
    fun `devices never seen are reported lost once after the limit`() {
        val tracker = tracker()

        assertEquals(emptyMap<String, SesameBleProbeSnapshot>(), tracker.onTick(5_999))
        assertEquals(setOf("a", "b"), tracker.onTick(6_000).keys)
        assertEquals(emptyMap<String, SesameBleProbeSnapshot>(), tracker.onTick(7_000))
    }

    @Test
    fun `only the device that went quiet is reported lost`() {
        val tracker = tracker()
        tracker.onSighting("a", -70, 5_000)

        val lost = tracker.onTick(6_500)

        assertEquals(setOf("b"), lost.keys)
        assertEquals(SesameBleProbeStage.NOT_FOUND, lost.getValue("b").stage)
    }
}

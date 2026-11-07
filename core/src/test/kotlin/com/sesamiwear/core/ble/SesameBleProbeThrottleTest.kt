package com.sesamiwear.core.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameBleProbeThrottleTest {
    private fun found(rssi: Int) = SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = rssi)

    @Test
    fun `first snapshot is always sent`() {
        assertTrue(SesameBleProbeThrottle().shouldSend(found(-70), 0))
    }

    @Test
    fun `identical snapshot is not sent again`() {
        val throttle = SesameBleProbeThrottle(minIntervalMillis = 800)
        throttle.shouldSend(found(-70), 0)
        assertFalse(throttle.shouldSend(found(-70), 5_000))
    }

    @Test
    fun `change within the same stage waits for the interval`() {
        val throttle = SesameBleProbeThrottle(minIntervalMillis = 800)
        throttle.shouldSend(found(-70), 0)
        assertFalse(throttle.shouldSend(found(-68), 799))
        assertTrue(throttle.shouldSend(found(-68), 800))
    }

    @Test
    fun `stage change is sent immediately`() {
        val throttle = SesameBleProbeThrottle(minIntervalMillis = 800)
        throttle.shouldSend(found(-70), 0)
        assertTrue(throttle.shouldSend(SesameBleProbeSnapshot(SesameBleProbeStage.NOT_FOUND), 10))
    }

    @Test
    fun `reset allows resending the same content`() {
        val throttle = SesameBleProbeThrottle()
        throttle.shouldSend(found(-70), 0)
        throttle.reset()
        assertTrue(throttle.shouldSend(found(-70), 1))
    }
}

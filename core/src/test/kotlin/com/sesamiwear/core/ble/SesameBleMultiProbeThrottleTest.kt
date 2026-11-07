package com.sesamiwear.core.ble

import com.sesamiwear.core.SesameDeviceSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameBleMultiProbeThrottleTest {
    private val base =
        SesameBleMultiProbeSnapshot.initial(
            listOf(SesameDeviceSummary("a", "玄関上"), SesameDeviceSummary("b", "玄関下")),
        )

    private fun found(
        uuid: String,
        rssi: Int,
    ) = base.withSnapshot(uuid, SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = rssi))

    @Test
    fun `first snapshot is always sent`() {
        assertTrue(SesameBleMultiProbeThrottle().shouldSend(base, 0))
    }

    @Test
    fun `identical snapshot is not sent again`() {
        val throttle = SesameBleMultiProbeThrottle(minIntervalMillis = 800)
        throttle.shouldSend(base, 0)
        assertFalse(throttle.shouldSend(base, 5_000))
    }

    @Test
    fun `stage change of any device is sent immediately`() {
        val throttle = SesameBleMultiProbeThrottle(minIntervalMillis = 800)
        throttle.shouldSend(base, 0)
        assertTrue(throttle.shouldSend(found("b", -70), 10))
    }

    @Test
    fun `rssi change within the same stages waits for the interval`() {
        val throttle = SesameBleMultiProbeThrottle(minIntervalMillis = 800)
        throttle.shouldSend(found("a", -70), 0)
        assertFalse(throttle.shouldSend(found("a", -68), 799))
        assertTrue(throttle.shouldSend(found("a", -68), 800))
    }

    @Test
    fun `reset allows resending the same content`() {
        val throttle = SesameBleMultiProbeThrottle()
        throttle.shouldSend(base, 0)
        throttle.reset()
        assertTrue(throttle.shouldSend(base, 1))
    }
}

package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetInProgressTrackerTest {
    private val front = SesameDeviceSummary(uuid = "uuid-front", displayName = "玄関")
    private val back = SesameDeviceSummary(uuid = "uuid-back", displayName = "勝手口")
    private val registered = listOf(front, back)
    private val tracker = WidgetInProgressTracker()

    @Test
    fun `nothing is in progress initially`() {
        assertFalse(tracker.isInProgress("uuid-front", registered))
        assertFalse(tracker.isInProgress(null, registered))
    }

    @Test
    fun `single device is in progress until finished`() {
        tracker.start(listOf("uuid-front"))

        assertTrue(tracker.isInProgress("uuid-front", registered))
        assertFalse(tracker.isInProgress("uuid-back", registered))

        tracker.finish(listOf("uuid-front"))
        assertFalse(tracker.isInProgress("uuid-front", registered))
    }

    @Test
    fun `all-devices widget is in progress while any registered device is`() {
        tracker.start(listOf("uuid-back"))

        assertTrue(tracker.isInProgress(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, registered))
    }

    @Test
    fun `overlapping runs on the same device stay in progress until both finish`() {
        tracker.start(listOf("uuid-front"))
        tracker.start(listOf("uuid-front"))

        tracker.finish(listOf("uuid-front"))
        assertTrue(tracker.isInProgress("uuid-front", registered))

        tracker.finish(listOf("uuid-front"))
        assertFalse(tracker.isInProgress("uuid-front", registered))
    }
}

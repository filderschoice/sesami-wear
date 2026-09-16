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
    private var now = 1_000L
    private val tracker = WidgetInProgressTracker { now }

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

    @Test
    fun `stays in progress until the timeout elapses`() {
        tracker.start(listOf("uuid-front"))

        now += WidgetInProgressTracker.IN_PROGRESS_TIMEOUT_MILLIS - 1
        assertTrue(tracker.isInProgress("uuid-front", registered))
    }

    @Test
    fun `is no longer in progress once the timeout elapses without finishing`() {
        // プロセスが落ちるなどで解除されなかった登録で、ウィジェットが「通信中...」のまま
        // 固まらないようにするための安全弁（BL-135）。
        tracker.start(listOf("uuid-front"))

        now += WidgetInProgressTracker.IN_PROGRESS_TIMEOUT_MILLIS
        assertFalse(tracker.isInProgress("uuid-front", registered))
        assertFalse(tracker.isInProgress(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, registered))
    }

    @Test
    fun `expiring an old run does not clear a newer run on the same device`() {
        tracker.start(listOf("uuid-front"))
        now += WidgetInProgressTracker.IN_PROGRESS_TIMEOUT_MILLIS - 1
        tracker.start(listOf("uuid-front"))

        now += 1
        assertTrue(tracker.isInProgress("uuid-front", registered))

        now += WidgetInProgressTracker.IN_PROGRESS_TIMEOUT_MILLIS
        assertFalse(tracker.isInProgress("uuid-front", registered))
    }
}

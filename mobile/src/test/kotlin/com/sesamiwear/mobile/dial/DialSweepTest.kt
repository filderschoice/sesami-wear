package com.sesamiwear.mobile.dial

import com.sesamiwear.core.api.SesameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ダイヤル操作（BL-243）の円形スワイプの判定。 */
class DialSweepTest {
    @Test
    fun `angles start at the top and go clockwise`() {
        assertEquals(0.0, DialSweep.angleOf(0f, -1f), DELTA)
        assertEquals(90.0, DialSweep.angleOf(1f, 0f), DELTA)
        assertEquals(180.0, DialSweep.angleOf(0f, 1f), DELTA)
        assertEquals(270.0, DialSweep.angleOf(-1f, 0f), DELTA)
    }

    @Test
    fun `the shortest delta crosses zero correctly`() {
        assertEquals(20.0, DialSweep.shortestDelta(350.0, 10.0), DELTA)
        assertEquals(-20.0, DialSweep.shortestDelta(10.0, 350.0), DELTA)
        assertEquals(180.0, DialSweep.shortestDelta(0.0, 180.0), DELTA)
    }

    @Test
    fun `a clockwise half turn triggers`() {
        val sweep = DialSweep()
        sweep.start(0f, -1f)
        sweep.move(1f, 0f)
        assertFalse(sweep.isTriggered)
        sweep.move(0f, 1f)
        assertTrue(sweep.isTriggered)
        assertEquals(180.0, sweep.sweptDegrees, DELTA)
        assertEquals(1f, sweep.progress, 0.0001f)
    }

    @Test
    fun `a counterclockwise half turn also triggers`() {
        val sweep = DialSweep()
        sweep.start(0f, -1f)
        sweep.move(-1f, 0f)
        sweep.move(0f, 1f)
        assertTrue(sweep.isTriggered)
        assertEquals(-180.0, sweep.sweptDegrees, DELTA)
    }

    @Test
    fun `going back and forth does not add up`() {
        val sweep = DialSweep()
        sweep.start(0f, -1f)
        sweep.move(1f, 0f)
        sweep.move(0f, -1f)
        sweep.move(1f, 0f)
        assertFalse(sweep.isTriggered)
        assertEquals(90.0, sweep.sweptDegrees, DELTA)
    }

    @Test
    fun `reset clears the progress`() {
        val sweep = DialSweep()
        sweep.start(0f, -1f)
        sweep.move(1f, 0f)
        sweep.reset()
        assertEquals(0.0, sweep.sweptDegrees, DELTA)
        assertEquals(0f, sweep.progress, 0.0001f)
    }

    @Test
    fun `positions are shown as degrees on the dial`() {
        assertEquals(180.0, DialSweep.pointerDegreesOf(-180), DELTA)
        assertEquals(90.0, DialSweep.pointerDegreesOf(-270), DELTA)
        assertEquals(42.0, DialSweep.pointerDegreesOf(402), DELTA)
    }

    @Test
    fun `the command is the opposite of the current state`() {
        assertEquals(SesameCommand.UNLOCK, DialSweep.commandFor(true))
        assertEquals(SesameCommand.LOCK, DialSweep.commandFor(false))
        assertNull(DialSweep.commandFor(null))
    }

    private companion object {
        const val DELTA = 0.0001
    }
}

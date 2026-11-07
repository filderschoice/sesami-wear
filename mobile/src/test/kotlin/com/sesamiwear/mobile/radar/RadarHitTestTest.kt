package com.sesamiwear.mobile.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadarHitTestTest {
    private val right = 0f // cos(0)=1: ブリップより右へラベルを置く向き
    private val left = Math.PI.toFloat() // cos(π)=-1: 左へ置く向き

    @Test
    fun `label goes to the right of the blip when facing right, and to the left when facing left`() {
        val (rx, ry) = RadarHitTest.labelOrigin(BlipShape(100f, 100f, 5f), right, LabelBox(40f, 20f, 10f))
        assertEquals(110f, rx, 0.001f)
        assertEquals(90f, ry, 0.001f)

        val (lx, _) = RadarHitTest.labelOrigin(BlipShape(100f, 100f, 5f), left, LabelBox(40f, 20f, 10f))
        assertEquals(50f, lx, 0.001f)
    }

    @Test
    fun `target covers both the blip and its label`() {
        val target = RadarHitTest.targetOf("a", BlipShape(100f, 100f, 5f), right, LabelBox(40f, 20f, 10f))

        assertEquals(95f, target.left, 0.001f)
        assertEquals(150f, target.right, 0.001f)
        assertEquals(90f, target.top, 0.001f)
        assertEquals(110f, target.bottom, 0.001f)
    }

    @Test
    fun `target without a label is only the blip`() {
        val target = RadarHitTest.targetOf("a", BlipShape(100f, 100f, 5f), right, null)

        assertEquals(95f, target.left, 0.001f)
        assertEquals(105f, target.right, 0.001f)
    }

    @Test
    fun `tap on the label or the blip hits, tap far away does not`() {
        val target = RadarHitTest.targetOf("a", BlipShape(100f, 100f, 5f), right, LabelBox(40f, 20f, 10f))

        assertEquals("a", RadarHitTest.find(100f, 100f, listOf(target), slop = 0f))
        assertEquals("a", RadarHitTest.find(140f, 100f, listOf(target), slop = 0f))
        assertNull(RadarHitTest.find(300f, 300f, listOf(target), slop = 0f))
    }

    @Test
    fun `slop widens the target`() {
        val target = RadarHitTest.targetOf("a", BlipShape(100f, 100f, 5f), right, null)

        assertNull(RadarHitTest.find(115f, 100f, listOf(target), slop = 0f))
        assertEquals("a", RadarHitTest.find(115f, 100f, listOf(target), slop = 12f))
    }

    @Test
    fun `when two targets overlap, the one whose center is nearer wins`() {
        val a = RadarTarget("a", 0f, 0f, 100f, 20f)
        val b = RadarTarget("b", 60f, 0f, 160f, 20f)

        assertEquals("a", RadarHitTest.find(70f, 10f, listOf(a, b), slop = 0f))
        assertEquals("b", RadarHitTest.find(90f, 10f, listOf(a, b), slop = 0f))
    }

    @Test
    fun `even bearings split the full turn and never coincide`() {
        val bearings = (0 until 3).map { RadarHitTest.evenBearing(it, 3) }

        assertEquals((Math.PI / 6).toFloat(), bearings[0], 0.0001f)
        assertEquals((2 * Math.PI / 3).toFloat(), bearings[1] - bearings[0], 0.0001f)
        assertEquals((2 * Math.PI / 3).toFloat(), bearings[2] - bearings[1], 0.0001f)
    }

    @Test
    fun `a single device is placed at the start bearing`() {
        assertEquals((Math.PI / 6).toFloat(), RadarHitTest.evenBearing(0, 1), 0.0001f)
        assertEquals((Math.PI / 6).toFloat(), RadarHitTest.evenBearing(0, 0), 0.0001f)
    }
}

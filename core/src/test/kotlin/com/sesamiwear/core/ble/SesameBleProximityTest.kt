package com.sesamiwear.core.ble

import com.sesamiwear.core.ble.SesameBleProximity.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameBleProximityTest {
    @Test
    fun `reference rssi means one meter`() {
        assertEquals(1.0, SesameBleProximity.distanceMeters(SesameBleProximity.REFERENCE_RSSI_AT_1M_DBM), 1e-9)
    }

    @Test
    fun `stronger than reference is clamped to one meter`() {
        assertEquals(1.0, SesameBleProximity.distanceMeters(-30), 1e-9)
    }

    @Test
    fun `weaker signal means longer distance`() {
        val near = SesameBleProximity.distanceMeters(-65)
        val far = SesameBleProximity.distanceMeters(-85)
        assertTrue(near < far)
    }

    @Test
    fun `levels follow the distance thresholds`() {
        assertEquals(Level.VERY_NEAR, SesameBleProximity.level(-55))
        assertEquals(Level.VERY_NEAR, SesameBleProximity.level(-63))
        assertEquals(Level.NEAR, SesameBleProximity.level(-64))
        assertEquals(Level.NEAR, SesameBleProximity.level(-73))
        assertEquals(Level.MEDIUM, SesameBleProximity.level(-74))
        assertEquals(Level.MEDIUM, SesameBleProximity.level(-81))
        assertEquals(Level.FAR, SesameBleProximity.level(-85))
    }

    @Test
    fun `null rssi is not seen`() {
        assertEquals(Level.NOT_SEEN, SesameBleProximity.level(null))
        assertEquals("距離不明", SesameBleProximity.distanceLabel(null))
        assertNull(SesameBleProximity.radarFraction(null))
    }

    @Test
    fun `distance label is rounded and capped at the outer ring`() {
        assertEquals("約1m", SesameBleProximity.distanceLabel(-60))
        assertEquals("約3m", SesameBleProximity.distanceLabel(-71))
        assertEquals("10m以上", SesameBleProximity.distanceLabel(-90))
    }

    @Test
    fun `radar fraction stays within zero and one and grows with distance`() {
        val samples = listOf(-50, -62, -68, -72, -77, -82, -88, -100).map { SesameBleProximity.radarFraction(it) }
        samples.forEach { fraction ->
            assertNotNull(fraction)
            assertTrue(fraction!! in 0f..1f)
        }
        samples.zipWithNext().forEach { (nearer, farther) -> assertTrue(nearer!! <= farther!!) }
        assertEquals(1f, SesameBleProximity.radarFraction(-100)!!, 0f)
    }

    @Test
    fun `radar fraction places rings at even spacing`() {
        // 3m（2番目の輪）は半周の位置に、10m（最外周）は1.0に来る。
        val threeMeters = SesameBleProximity.REFERENCE_RSSI_AT_1M_DBM - 22 * Math.log10(3.0)
        assertEquals(0.5f, SesameBleProximity.radarFraction(Math.round(threeMeters).toInt())!!, 0.05f)
    }
}

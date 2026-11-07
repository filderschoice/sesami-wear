package com.sesamiwear.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameBleRssiSmootherTest {
    @Test
    fun `first value is taken as is`() {
        val smoother = SesameBleRssiSmoother()
        assertNull(smoother.value)
        assertEquals(-70, smoother.add(-70))
        assertEquals(-70, smoother.value)
    }

    @Test
    fun `small jitter is damped`() {
        val smoother = SesameBleRssiSmoother()
        smoother.add(-70)
        // -70 + 0.3 * (-62 - -70) = -67.6 → -68（生の-62へ跳ばない）
        assertEquals(-68, smoother.add(-62))
    }

    @Test
    fun `large change follows faster`() {
        val smoother = SesameBleRssiSmoother()
        smoother.add(-90)
        // 差が12dB以上なので速い係数: -90 + 0.7 * (-60 - -90) = -69
        assertEquals(-69, smoother.add(-60))
    }

    @Test
    fun `reset forgets history`() {
        val smoother = SesameBleRssiSmoother()
        smoother.add(-60)
        smoother.reset()
        assertNull(smoother.value)
        assertEquals(-85, smoother.add(-85))
    }
}

package com.sesamiwear.core.haptics

import com.sesamiwear.core.SesameCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameHapticPatternResolverTest {
    @Test
    fun `success maps to the success pattern`() {
        assertEquals(HapticPattern.SUCCESS, SesameHapticPatternResolver.resolve(SesameCommandResult.SUCCESS))
    }

    @Test
    fun `failure maps to the failure pattern`() {
        assertEquals(HapticPattern.FAILURE, SesameHapticPatternResolver.resolve(SesameCommandResult.FAILURE))
    }

    @Test
    fun `patterns differ so they can be told apart without looking at the screen`() {
        assertNotEquals(HapticPattern.SUCCESS.timingsMillis, HapticPattern.FAILURE.timingsMillis)
    }

    @Test
    fun `every pattern is a valid waveform`() {
        HapticPattern.entries.forEach { pattern ->
            // VibrationEffect.createWaveformは空配列と負の値を受け付けない。
            assertTrue(pattern.name, pattern.timingsMillis.isNotEmpty())
            assertTrue(pattern.name, pattern.timingsMillis.all { it >= 0L })
        }
    }
}

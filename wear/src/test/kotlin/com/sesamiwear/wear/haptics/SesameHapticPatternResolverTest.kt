package com.sesamiwear.wear.haptics

import com.sesamiwear.core.SesameCommandResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SesameHapticPatternResolverTest {
    @Test
    fun `success result resolves to success pattern`() {
        assertEquals(HapticPattern.SUCCESS, SesameHapticPatternResolver.resolve(SesameCommandResult.SUCCESS))
    }

    @Test
    fun `failure result resolves to failure pattern`() {
        assertEquals(HapticPattern.FAILURE, SesameHapticPatternResolver.resolve(SesameCommandResult.FAILURE))
    }
}

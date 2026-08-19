package com.sesamiwear.wear.messaging

import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.wear.haptics.HapticPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameResultHandlerTest {
    @Test
    fun `success result payload resolves to success pattern`() {
        val pattern =
            SesameResultHandler.hapticPatternForMessage(
                path = SesameWearProtocol.PATH_COMMAND_RESULT,
                payload = SesameCommandResult.SUCCESS.toPayload(),
            )

        assertEquals(HapticPattern.SUCCESS, pattern)
    }

    @Test
    fun `failure result payload resolves to failure pattern`() {
        val pattern =
            SesameResultHandler.hapticPatternForMessage(
                path = SesameWearProtocol.PATH_COMMAND_RESULT,
                payload = SesameCommandResult.FAILURE.toPayload(),
            )

        assertEquals(HapticPattern.FAILURE, pattern)
    }

    @Test
    fun `unrelated message paths are ignored`() {
        val pattern =
            SesameResultHandler.hapticPatternForMessage(
                path = SesameWearProtocol.PATH_LOCK_REQUEST,
                payload = SesameCommandResult.SUCCESS.toPayload(),
            )

        assertNull(pattern)
    }
}

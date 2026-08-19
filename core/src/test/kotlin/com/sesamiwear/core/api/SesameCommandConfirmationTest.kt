package com.sesamiwear.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SesameCommandConfirmationTest {
    @Test
    fun `lock does not require confirmation`() {
        assertEquals(false, SesameCommandConfirmation.isRequired(SesameCommand.LOCK))
    }

    @Test
    fun `unlock requires confirmation`() {
        assertEquals(true, SesameCommandConfirmation.isRequired(SesameCommand.UNLOCK))
    }
}

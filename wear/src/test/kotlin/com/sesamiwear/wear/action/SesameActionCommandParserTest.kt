package com.sesamiwear.wear.action

import com.sesamiwear.core.api.SesameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameActionCommandParserTest {
    @Test
    fun `parses lock command name`() {
        assertEquals(SesameCommand.LOCK, SesameActionCommandParser.fromExtra("LOCK"))
    }

    @Test
    fun `parses unlock command name`() {
        assertEquals(SesameCommand.UNLOCK, SesameActionCommandParser.fromExtra("UNLOCK"))
    }

    @Test
    fun `returns null for unknown or missing extra`() {
        assertNull(SesameActionCommandParser.fromExtra(null))
        assertNull(SesameActionCommandParser.fromExtra("TOGGLE"))
    }
}

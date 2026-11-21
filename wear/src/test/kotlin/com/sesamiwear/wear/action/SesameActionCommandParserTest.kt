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
    fun `choose flag opens the chooser regardless of the command`() {
        assertEquals(SesameActionCommandParser.Request.Choose, SesameActionCommandParser.parse(null, choose = true))
        assertEquals(SesameActionCommandParser.Request.Choose, SesameActionCommandParser.parse("LOCK", choose = true))
    }

    @Test
    fun `a command without the choose flag is a direct request`() {
        assertEquals(
            SesameActionCommandParser.Request.Direct(SesameCommand.LOCK),
            SesameActionCommandParser.parse("LOCK", choose = false),
        )
        assertNull(SesameActionCommandParser.parse(null, choose = false))
        assertNull(SesameActionCommandParser.parse("TOGGLE", choose = false))
    }

    @Test
    fun `returns null for unknown or missing extra`() {
        assertNull(SesameActionCommandParser.fromExtra(null))
        assertNull(SesameActionCommandParser.fromExtra("TOGGLE"))
    }
}

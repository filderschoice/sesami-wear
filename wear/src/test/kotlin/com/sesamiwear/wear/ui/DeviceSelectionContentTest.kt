package com.sesamiwear.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSelectionContentTest {
    @Test
    fun `demo hint title fits in a single line`() {
        assertTrue(DeviceSelectionContent.DEMO_HINT_TITLE.length <= DeviceSelectionContent.MAX_LINE_CHARS)
    }

    @Test
    fun `every demo hint line fits in the circular safe area`() {
        DeviceSelectionContent.DEMO_HINT_LINES.forEach { line ->
            assertTrue(
                "円形画面の幅を超える行がある: $line",
                line.length <= DeviceSelectionContent.MAX_LINE_CHARS,
            )
        }
    }

    @Test
    fun `demo hint body joins lines with explicit line breaks`() {
        assertEquals(
            DeviceSelectionContent.DEMO_HINT_LINES.size - 1,
            DeviceSelectionContent.demoHintBody.count { it == '\n' },
        )
    }

    @Test
    fun `demo hint lines are not blank`() {
        assertTrue(DeviceSelectionContent.DEMO_HINT_LINES.isNotEmpty())
        DeviceSelectionContent.DEMO_HINT_LINES.forEach { assertTrue(it.isNotBlank()) }
    }
}

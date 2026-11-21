package com.sesamiwear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameNodeSelectorTest {
    @Test
    fun `skips the cloud relay node even when it comes first`() {
        assertEquals("phone-1", SesameNodeSelector.selectPhoneNodeId(listOf("cloud", "phone-1")))
    }

    @Test
    fun `returns the first phone node`() {
        assertEquals("phone-1", SesameNodeSelector.selectPhoneNodeId(listOf("phone-1", "cloud")))
    }

    @Test
    fun `returns null when only the cloud node or nothing is connected`() {
        assertNull(SesameNodeSelector.selectPhoneNodeId(listOf("cloud")))
        assertNull(SesameNodeSelector.selectPhoneNodeId(emptyList()))
    }
}

package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.display.SesameDeviceTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPinPlacementTest {
    private val front = SesameDeviceTargets.Choice(uuid = "uuid-front", label = "玄関")
    private val back = SesameDeviceTargets.Choice(uuid = "uuid-back", label = "勝手口")
    private val all = SesameDeviceTargets.Choice(uuid = SesameWearProtocol.ALL_DEVICES_TARGET_UUID, label = "全デバイス")

    @Test
    fun `single choice is assigned automatically`() {
        assertEquals("uuid-front", WidgetPinPlacement.autoAssignUuid(listOf(front), currentAssignment = null))
    }

    @Test
    fun `multiple choices are left to the user`() {
        assertNull(WidgetPinPlacement.autoAssignUuid(listOf(all, front, back), currentAssignment = null))
    }

    @Test
    fun `existing assignment is never overwritten`() {
        assertNull(WidgetPinPlacement.autoAssignUuid(listOf(front), currentAssignment = "uuid-back"))
    }

    @Test
    fun `no choices assigns nothing`() {
        assertNull(WidgetPinPlacement.autoAssignUuid(emptyList(), currentAssignment = null))
    }
}

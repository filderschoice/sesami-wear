package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.display.SesameDeviceTargets
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetChoiceLabelTest {
    private val front = SesameDeviceTargets.Choice(uuid = "uuid-front", label = "玄関")
    private val all = SesameDeviceTargets.Choice(uuid = SesameWearProtocol.ALL_DEVICES_TARGET_UUID, label = "全デバイス")

    @Test
    fun `assigned choice is marked as selected`() {
        assertEquals("玄関（選択中）", WidgetChoiceLabel.of(front, "uuid-front"))
        assertEquals(
            "全デバイス（選択中）",
            WidgetChoiceLabel.of(all, SesameWearProtocol.ALL_DEVICES_TARGET_UUID),
        )
    }

    @Test
    fun `other choices keep their label`() {
        assertEquals("全デバイス", WidgetChoiceLabel.of(all, "uuid-front"))
    }

    @Test
    fun `unassigned widget marks nothing`() {
        assertEquals("玄関", WidgetChoiceLabel.of(front, null))
    }
}

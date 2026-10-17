package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAccessibilityLabelsTest {
    private fun model(
        state: TileDisplayState,
        detailLabel: String? = null,
        batteryPercentage: Int? = null,
        uuid: String = "uuid-front",
    ) = SesameWidgetModel.Configured(
        deviceUuid = uuid,
        displayName = "玄関",
        state = state,
        isAllDevices = uuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
        detailLabel = detailLabel,
        batteryPercentage = batteryPercentage,
    )

    @Test
    fun `locked status reads name, state, freshness, battery and the unlock action`() {
        assertEquals(
            "玄関、施錠中、3分前、電池残量85%、タップで解錠",
            WidgetAccessibilityLabels.status(model(TileDisplayState.LOCKED, "3分前", 85)),
        )
    }

    @Test
    fun `all-devices status reads the aggregate wording`() {
        assertEquals(
            "玄関、全解錠中、タップで全施錠",
            WidgetAccessibilityLabels.status(
                model(TileDisplayState.UNLOCKED, uuid = SesameWearProtocol.ALL_DEVICES_TARGET_UUID),
            ),
        )
    }

    @Test
    fun `non-operable states omit the action and the trailing dots`() {
        assertEquals("玄関、通信中", WidgetAccessibilityLabels.status(model(TileDisplayState.IN_PROGRESS)))
        assertEquals("玄関、状態不明、未取得", WidgetAccessibilityLabels.status(model(TileDisplayState.UNKNOWN, "未取得")))
    }

    @Test
    fun `status never reads emoji`() {
        val label = WidgetAccessibilityLabels.status(model(TileDisplayState.LOCKED, "3分前", 15))
        listOf("🔒", "🔋", "⚠").forEach { assertFalse(label, label.contains(it)) }
    }

    @Test
    fun `low battery is announced before the percentage`() {
        assertEquals("電池残量が少なくなっています 15%", WidgetAccessibilityLabels.battery(15))
        assertEquals("電池残量20%", WidgetAccessibilityLabels.battery(20))
        assertNull(WidgetAccessibilityLabels.battery(null))
    }

    @Test
    fun `refresh names the target device`() {
        assertEquals("玄関の状態を更新", WidgetAccessibilityLabels.refresh("玄関"))
    }
}

package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTapActionTest {
    private fun model(
        state: TileDisplayState,
        uuid: String = "uuid-front",
    ) = SesameWidgetModel.Configured(
        deviceUuid = uuid,
        displayName = "玄関",
        state = state,
        isAllDevices = uuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
    )

    @Test
    fun `locked widget opens the chooser instead of unlocking directly`() {
        assertEquals(
            WidgetTapAction.Choose,
            WidgetTapAction.forModel(model(TileDisplayState.LOCKED)),
        )
    }

    @Test
    fun `unlocked widget opens the chooser instead of locking at once`() {
        // BL-304: 1台のウィジェットでも、解錠中のタップは即施錠せず選ぶ画面を開く。
        assertEquals(WidgetTapAction.Choose, WidgetTapAction.forModel(model(TileDisplayState.UNLOCKED)))
    }

    @Test
    fun `mixed all-devices widget opens the chooser`() {
        assertEquals(
            WidgetTapAction.Choose,
            WidgetTapAction.forModel(model(TileDisplayState.MIXED, SesameWearProtocol.ALL_DEVICES_TARGET_UUID)),
        )
    }

    @Test
    fun `in-progress and unknown widgets do nothing`() {
        assertEquals(WidgetTapAction.None, WidgetTapAction.forModel(model(TileDisplayState.IN_PROGRESS)))
        assertEquals(WidgetTapAction.None, WidgetTapAction.forModel(model(TileDisplayState.UNKNOWN)))
    }

    @Test
    fun `unconfigured widget opens the selection screen`() {
        assertEquals(WidgetTapAction.OpenConfiguration, WidgetTapAction.forModel(SesameWidgetModel.Unconfigured))
    }

    @Test
    fun `no widget state runs a command on a plain tap`() {
        TileDisplayState.entries.forEach { state ->
            val action = WidgetTapAction.forModel(model(state))
            assertTrue(state.name, action == WidgetTapAction.Choose || action == WidgetTapAction.None)
        }
    }

    @Test
    fun `an all-devices widget follows the same rule as a single device`() {
        val all = SesameWearProtocol.ALL_DEVICES_TARGET_UUID
        TileDisplayState.entries.forEach { state ->
            assertEquals(
                state.name,
                WidgetTapAction.forModel(model(state)),
                WidgetTapAction.forModel(model(state, all)),
            )
        }
    }
}

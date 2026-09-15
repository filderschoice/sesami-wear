package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `locked widget asks for confirmation before unlocking`() {
        assertEquals(
            WidgetTapAction.Confirm(SesameCommand.UNLOCK),
            WidgetTapAction.forModel(model(TileDisplayState.LOCKED)),
        )
    }

    @Test
    fun `unlocked widget locks immediately`() {
        assertEquals(
            WidgetTapAction.Run(SesameCommand.LOCK),
            WidgetTapAction.forModel(model(TileDisplayState.UNLOCKED)),
        )
    }

    @Test
    fun `mixed all-devices widget locks all immediately`() {
        assertEquals(
            WidgetTapAction.Run(SesameCommand.LOCK),
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
    fun `cancelling the confirmation sends nothing`() {
        assertNull(WidgetTapAction.commandAfterConfirmation(SesameCommand.UNLOCK, confirmed = false))
        assertEquals(
            SesameCommand.UNLOCK,
            WidgetTapAction.commandAfterConfirmation(SesameCommand.UNLOCK, confirmed = true),
        )
    }
}

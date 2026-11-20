package com.sesamiwear.core.display

import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameTileActionsTest {
    @Test
    fun `locked state offers unlock`() {
        assertEquals(SesameCommand.UNLOCK, SesameTileActions.commandForState(TileDisplayState.LOCKED))
    }

    @Test
    fun `unlocked state offers lock`() {
        assertEquals(SesameCommand.LOCK, SesameTileActions.commandForState(TileDisplayState.UNLOCKED))
    }

    @Test
    fun `mixed state offers lock as the safe default`() {
        assertEquals(SesameCommand.LOCK, SesameTileActions.commandForState(TileDisplayState.MIXED))
    }

    @Test
    fun `non-actionable states offer no command`() {
        assertNull(SesameTileActions.commandForState(TileDisplayState.IN_PROGRESS))
        assertNull(SesameTileActions.commandForState(TileDisplayState.DISCONNECTED))
        assertNull(SesameTileActions.commandForState(TileDisplayState.UNKNOWN))
    }

    // BL-295: タップの新しい規則（Tile・ウィジェット・クイック設定とウォッチの一覧）。
    // 判定は単体・全デバイスで同じ（全デバイスかどうかは選ぶ画面のボタンの文言だけを変える）。

    @Test
    fun `tile tap decision per state`() {
        val expected =
            mapOf(
                TileDisplayState.LOCKED to SesameTileActions.TapDecision.OpenChooser,
                TileDisplayState.MIXED to SesameTileActions.TapDecision.OpenChooser,
                TileDisplayState.UNLOCKED to SesameTileActions.TapDecision.Run(SesameCommand.LOCK),
                TileDisplayState.IN_PROGRESS to SesameTileActions.TapDecision.None,
                TileDisplayState.DISCONNECTED to SesameTileActions.TapDecision.None,
                TileDisplayState.UNKNOWN to SesameTileActions.TapDecision.None,
            )
        TileDisplayState.entries.forEach { state ->
            assertEquals(state.name, expected.getValue(state), SesameTileActions.tapDecisionForState(state))
        }
    }

    @Test
    fun `no state ever unlocks on a plain tap`() {
        TileDisplayState.entries.forEach { state ->
            assertNotEquals(
                state.name,
                SesameTileActions.TapDecision.Run(SesameCommand.UNLOCK),
                SesameTileActions.tapDecisionForState(state),
            )
        }
        assertNotEquals(
            SesameTileActions.TapDecision.Run(SesameCommand.UNLOCK),
            SesameTileActions.listTapDecision(),
        )
    }

    @Test
    fun `watch list rows always open the chooser regardless of state`() {
        assertEquals(SesameTileActions.TapDecision.OpenChooser, SesameTileActions.listTapDecision())
    }

    @Test
    fun `command for state is unchanged by the chooser rule`() {
        assertEquals(SesameCommand.UNLOCK, SesameTileActions.commandForState(TileDisplayState.LOCKED))
        assertEquals(SesameCommand.LOCK, SesameTileActions.commandForState(TileDisplayState.UNLOCKED))
        assertEquals(SesameCommand.LOCK, SesameTileActions.commandForState(TileDisplayState.MIXED))
    }

    @Test
    fun `command labels are prefixed with zen for all devices`() {
        assertEquals("施錠", SesameTileActions.commandLabel(SesameCommand.LOCK, isAllDevices = false))
        assertEquals("解錠", SesameTileActions.commandLabel(SesameCommand.UNLOCK, isAllDevices = false))
        assertEquals("全施錠", SesameTileActions.commandLabel(SesameCommand.LOCK, isAllDevices = true))
        assertEquals("全解錠", SesameTileActions.commandLabel(SesameCommand.UNLOCK, isAllDevices = true))
    }
}

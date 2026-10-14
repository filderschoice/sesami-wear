package com.sesamiwear.mobile.quicksettings

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.quicksettings.QuickSettingsTileContent.IconKind
import com.sesamiwear.mobile.quicksettings.QuickSettingsTileContent.Tap
import com.sesamiwear.mobile.quicksettings.QuickSettingsTileContent.TileState
import com.sesamiwear.mobile.widget.SesameWidgetModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** クイック設定パネルのタイル（BL-240）。ウィジェットと同じ操作ルールと、パネル固有の差分を固定する。 */
class QuickSettingsTileContentTest {
    private val front = SesameDeviceSummary(uuid = "uuid-front", displayName = "玄関")
    private val back = SesameDeviceSummary(uuid = "uuid-back", displayName = "勝手口")

    @Test
    fun `target is the single device or all devices`() {
        assertNull(QuickSettingsTileContent.targetUuid(emptyList()))
        assertEquals("uuid-front", QuickSettingsTileContent.targetUuid(listOf(front)))
        assertEquals(
            SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
            QuickSettingsTileContent.targetUuid(listOf(front, back)),
        )
    }

    @Test
    fun `nothing registered opens the app`() {
        val content = QuickSettingsTileContent.from(SesameWidgetModel.Unconfigured)

        assertEquals(Tap.OpenApp, content.tap)
        assertEquals("タップして登録", content.subtitle)
        assertEquals(TileState.INACTIVE, content.tileState)
    }

    @Test
    fun `locked asks for confirmation before unlocking`() {
        val content = QuickSettingsTileContent.from(configured(TileDisplayState.LOCKED))

        assertEquals(Tap.ConfirmUnlock("uuid-front", "玄関"), content.tap)
        assertEquals(TileState.ACTIVE, content.tileState)
        assertEquals(IconKind.LOCKED, content.iconKind)
        assertEquals("玄関", content.label)
        assertEquals("施錠中", content.subtitle)
    }

    @Test
    fun `unlocked and mixed lock without confirmation`() {
        assertEquals(
            Tap.Run("uuid-front", SesameCommand.LOCK),
            QuickSettingsTileContent.from(configured(TileDisplayState.UNLOCKED)).tap,
        )
        val all = SesameWearProtocol.ALL_DEVICES_TARGET_UUID
        val mixed = QuickSettingsTileContent.from(configured(TileDisplayState.MIXED, uuid = all, isAll = true))
        assertEquals(Tap.Run(all, SesameCommand.LOCK), mixed.tap)
        assertEquals(IconKind.UNLOCKED, mixed.iconKind)
        assertEquals(TileState.INACTIVE, mixed.tileState)
    }

    @Test
    fun `unknown refreshes and in progress does nothing`() {
        assertEquals(Tap.Refresh("uuid-front"), QuickSettingsTileContent.from(configured(TileDisplayState.UNKNOWN)).tap)
        val inProgress = QuickSettingsTileContent.from(configured(TileDisplayState.IN_PROGRESS))
        assertEquals(Tap.None, inProgress.tap)
        assertEquals(TileState.UNAVAILABLE, inProgress.tileState)
    }

    @Test
    fun `a device without a name never shows its uuid`() {
        val content =
            QuickSettingsTileContent.from(configured(TileDisplayState.LOCKED, displayName = "uuid-front"))

        assertEquals("Sesame", content.label)
    }

    private fun configured(
        state: TileDisplayState,
        uuid: String = "uuid-front",
        displayName: String = "玄関",
        isAll: Boolean = false,
    ) = SesameWidgetModel.Configured(
        deviceUuid = uuid,
        displayName = displayName,
        state = state,
        isAllDevices = isAll,
    )
}

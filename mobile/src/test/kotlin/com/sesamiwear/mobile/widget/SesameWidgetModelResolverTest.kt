package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.display.SesameTileContent
import org.junit.Assert.assertEquals
import org.junit.Test

class SesameWidgetModelResolverTest {
    private val front = SesameDeviceSummary(uuid = "uuid-front", displayName = "玄関")
    private val back = SesameDeviceSummary(uuid = "uuid-back", displayName = "勝手口")
    private val lockStates = mutableMapOf<String, Boolean>()

    private fun resolve(
        assignedUuid: String?,
        registered: List<SesameDeviceSummary>,
        isCommandInProgress: Boolean = false,
    ) = SesameWidgetModelResolver.resolve(assignedUuid, registered, { lockStates[it] }, isCommandInProgress)

    private fun configured(model: SesameWidgetModel) = model as SesameWidgetModel.Configured

    @Test
    fun `unassigned widget asks to be configured`() {
        assertEquals(SesameWidgetModel.Unconfigured, resolve(null, listOf(front)))
    }

    @Test
    fun `assigned device that was deleted asks to be configured`() {
        assertEquals(SesameWidgetModel.Unconfigured, resolve("uuid-removed", listOf(front)))
    }

    @Test
    fun `single device shows its name and saved state with Tile wording and colors`() {
        lockStates["uuid-front"] = true

        val model = configured(resolve("uuid-front", listOf(front, back)))

        assertEquals("玄関", model.displayName)
        assertEquals(TileDisplayState.LOCKED, model.state)
        assertEquals("施錠中", model.statusLabel)
        assertEquals("タップで解錠", model.actionLabel)
        assertEquals(SesameTileContent.statusIcon(TileDisplayState.LOCKED), model.statusIcon)
        assertEquals(SesameTileContent.backgroundColorArgb(TileDisplayState.LOCKED), model.backgroundColorArgb)
        assertEquals(SesameTileContent.statusTextColorArgb(TileDisplayState.LOCKED), model.textColorArgb)
    }

    @Test
    fun `single device without saved state is unknown`() {
        val model = configured(resolve("uuid-front", listOf(front)))

        assertEquals(TileDisplayState.UNKNOWN, model.state)
        assertEquals("状態不明", model.statusLabel)
        assertEquals(null, model.actionLabel)
    }

    @Test
    fun `phone widget never shows the watch-only disconnected state`() {
        val states =
            listOf(null, true, false).map { isLocked ->
                if (isLocked == null) lockStates.remove("uuid-front") else lockStates["uuid-front"] = isLocked
                configured(resolve("uuid-front", listOf(front))).state
            }

        assertEquals(listOf(TileDisplayState.UNKNOWN, TileDisplayState.LOCKED, TileDisplayState.UNLOCKED), states)
    }

    @Test
    fun `command in progress is shown as in progress`() {
        lockStates["uuid-front"] = false

        val model = configured(resolve("uuid-front", listOf(front), isCommandInProgress = true))

        assertEquals(TileDisplayState.IN_PROGRESS, model.state)
    }

    @Test
    fun `all devices aggregates saved states with all-devices wording`() {
        lockStates["uuid-front"] = true
        lockStates["uuid-back"] = true

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals("全デバイス", model.displayName)
        assertEquals(TileDisplayState.LOCKED, model.state)
        assertEquals("全施錠中", model.statusLabel)
        assertEquals("タップで全解錠", model.actionLabel)
    }

    @Test
    fun `all devices with mixed states offers lock-all`() {
        lockStates["uuid-front"] = true
        lockStates["uuid-back"] = false

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals(TileDisplayState.MIXED, model.state)
        assertEquals("一部解錠", model.statusLabel)
        assertEquals("タップで全施錠", model.actionLabel)
    }

    @Test
    fun `all devices with one unknown device is unknown`() {
        lockStates["uuid-front"] = true

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals(TileDisplayState.UNKNOWN, model.state)
    }

    @Test
    fun `all devices with no registered device asks to be configured`() {
        assertEquals(SesameWidgetModel.Unconfigured, resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, emptyList()))
    }

    @Test
    fun `demo device starts locked when nothing is registered`() {
        val model = configured(resolve(SesameDemoMode.DEMO_DEVICE_UUID, emptyList()))

        assertEquals("デモ", model.displayName)
        assertEquals(TileDisplayState.LOCKED, model.state)
    }

    @Test
    fun `demo device uses its saved state`() {
        lockStates[SesameDemoMode.DEMO_DEVICE_UUID] = false

        val model = configured(resolve(SesameDemoMode.DEMO_DEVICE_UUID, emptyList()))

        assertEquals(TileDisplayState.UNLOCKED, model.state)
    }

    @Test
    fun `demo device asks to be configured once a real device is registered`() {
        assertEquals(SesameWidgetModel.Unconfigured, resolve(SesameDemoMode.DEMO_DEVICE_UUID, listOf(front)))
    }
}

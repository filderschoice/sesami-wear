package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.display.SesameTileContent
import org.junit.Assert.assertEquals
import org.junit.Test

class SesameWidgetModelResolverTest {
    private val front = SesameDeviceSummary(uuid = "uuid-front", displayName = "玄関")
    private val back = SesameDeviceSummary(uuid = "uuid-back", displayName = "勝手口")
    private val lockStates = mutableMapOf<String, Boolean>()
    private val updatedAtMillis = mutableMapOf<String, Long>()
    private val failures = mutableMapOf<String, SesameStatusFailure>()
    private val batteries = mutableMapOf<String, Int>()
    private val routes = mutableMapOf<String, SesameStatusRoute>()

    private fun resolve(
        assignedUuid: String?,
        registered: List<SesameDeviceSummary>,
        isCommandInProgress: Boolean = false,
    ) = SesameWidgetModelResolver.resolve(
        assignedUuid = assignedUuid,
        registeredDevices = registered,
        snapshotOf = { uuid -> snapshotOf(uuid) },
        isCommandInProgress = isCommandInProgress,
        nowEpochMillis = NOW,
    )

    private fun snapshotOf(uuid: String): SesameStatusSnapshot? {
        val isLocked = lockStates[uuid]
        val failure = failures[uuid]
        if (isLocked == null && failure == null) return null
        return SesameStatusSnapshot(
            isLocked = isLocked,
            updatedAtEpochMillis = if (isLocked == null) null else updatedAtMillis[uuid] ?: NOW,
            lastFailure = failure,
            batteryPercentage = batteries[uuid],
            lastRoute = routes[uuid],
        )
    }

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

    @Test
    fun `single device shows how old the saved state is`() {
        lockStates["uuid-front"] = true
        updatedAtMillis["uuid-front"] = NOW - 5 * 60_000L

        assertEquals("5分前", configured(resolve("uuid-front", listOf(front))).detailLabel)
    }

    @Test
    fun `single device without saved state shows never fetched`() {
        assertEquals("未取得", configured(resolve("uuid-front", listOf(front))).detailLabel)
    }

    @Test
    fun `all devices shows the oldest fetch among them`() {
        lockStates["uuid-front"] = true
        lockStates["uuid-back"] = true
        updatedAtMillis["uuid-front"] = NOW - 2 * 60_000L
        updatedAtMillis["uuid-back"] = NOW - 90 * 60_000L

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals("1時間前", model.detailLabel)
    }

    @Test
    fun `all devices shows never fetched when one of them was never fetched`() {
        lockStates["uuid-front"] = true
        updatedAtMillis["uuid-front"] = NOW - 2 * 60_000L

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals("未取得", model.detailLabel)
    }

    @Test
    fun `demo device has no freshness because it never calls the API`() {
        assertEquals(null, configured(resolve(SesameDemoMode.DEMO_DEVICE_UUID, emptyList())).detailLabel)
    }

    @Test
    fun `a failed refresh keeps the last known state and shows the reason instead of the freshness`() {
        lockStates["uuid-front"] = true
        updatedAtMillis["uuid-front"] = NOW - 5 * 60_000L
        failures["uuid-front"] = SesameStatusFailure.AUTH_OR_QUOTA

        val model = configured(resolve("uuid-front", listOf(front)))

        assertEquals(TileDisplayState.LOCKED, model.state)
        assertEquals("施錠中", model.statusLabel)
        assertEquals("認証エラー（設定を確認）", model.detailLabel)
    }

    @Test
    fun `a communication failure without any saved state stays unknown`() {
        failures["uuid-front"] = SesameStatusFailure.COMMUNICATION

        val model = configured(resolve("uuid-front", listOf(front)))

        assertEquals(TileDisplayState.UNKNOWN, model.state)
        assertEquals("通信エラー（電波状況を確認）", model.detailLabel)
    }

    @Test
    fun `all devices prefers the failure the user can act on`() {
        lockStates["uuid-front"] = true
        lockStates["uuid-back"] = true
        failures["uuid-front"] = SesameStatusFailure.COMMUNICATION
        failures["uuid-back"] = SesameStatusFailure.AUTH_OR_QUOTA

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals("認証エラー（設定を確認）", model.detailLabel)
    }

    // --- 電池残量（BL-171） ---

    @Test
    fun `the battery is shown next to the freshness without adding a row`() {
        lockStates[front.uuid] = true
        batteries[front.uuid] = 85

        val model = configured(resolve(front.uuid, listOf(front)))

        assertEquals(85, model.batteryPercentage)
        assertEquals("たった今 🔋85%", model.detailWithBatteryLabel)
    }

    @Test
    fun `no battery reading leaves the line as it was`() {
        lockStates[front.uuid] = true

        val model = configured(resolve(front.uuid, listOf(front)))

        assertEquals(null, model.batteryPercentage)
        assertEquals("たった今", model.detailWithBatteryLabel)
    }

    @Test
    fun `the lowest battery represents all devices`() {
        lockStates[front.uuid] = true
        lockStates[back.uuid] = true
        batteries[front.uuid] = 85
        batteries[back.uuid] = 40

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals(40, model.batteryPercentage)
    }

    @Test
    fun `a device without a reading does not hide the readings of the others`() {
        lockStates[front.uuid] = true
        lockStates[back.uuid] = true
        batteries[back.uuid] = 40

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals(40, model.batteryPercentage)
    }

    @Test
    fun `the route is exposed separately and is not prefixed to the detail label`() {
        // ウィジェットは経路をベクターアイコンで描くため、文言へ絵文字を前置しない（BL-176）。
        lockStates[front.uuid] = true
        updatedAtMillis[front.uuid] = NOW - 3 * 60_000L
        routes[front.uuid] = SesameStatusRoute.BLE

        val model = configured(resolve(front.uuid, listOf(front)))

        assertEquals(SesameStatusRoute.BLE, model.route)
        assertEquals("3分前", model.detailLabel)
    }

    @Test
    fun `a mixed route is not shown for all devices`() {
        lockStates[front.uuid] = true
        lockStates[back.uuid] = true
        routes[front.uuid] = SesameStatusRoute.BLE
        routes[back.uuid] = SesameStatusRoute.WEB_API

        val model = configured(resolve(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)))

        assertEquals(null, model.route)
    }

    @Test
    fun `a demo device never shows a battery`() {
        val model = configured(resolve(SesameDemoMode.DEMO_DEVICE_UUID, emptyList()))

        assertEquals(null, model.batteryPercentage)
        assertEquals(null, model.detailWithBatteryLabel)
    }

    private companion object {
        // 2026-09-18 12:00 JST 相当の固定値。相対表記の判定だけに使うため、日付の意味は持たせない。
        const val NOW = 1_789_700_000_000L
    }
}

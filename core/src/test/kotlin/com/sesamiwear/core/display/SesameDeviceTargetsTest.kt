package com.sesamiwear.core.display

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameDeviceTargetsTest {
    private val front = SesameDeviceSummary(uuid = "uuid-front", displayName = "玄関")
    private val back = SesameDeviceSummary(uuid = "uuid-back", displayName = "")

    @Test
    fun `choices are only the demo device when nothing is registered`() {
        val choices = SesameDeviceTargets.choices(emptyList())

        assertEquals(
            listOf(
                SesameDeviceTargets.Choice(SesameDemoMode.DEMO_DEVICE_UUID, SesameDemoMode.DEMO_DEVICE_DISPLAY_NAME),
            ),
            choices,
        )
    }

    @Test
    fun `choices do not include all devices when only one device is registered`() {
        val choices = SesameDeviceTargets.choices(listOf(front))

        assertEquals(listOf(SesameDeviceTargets.Choice("uuid-front", "玄関")), choices)
    }

    @Test
    fun `choices put all devices first when two or more devices are registered`() {
        val choices = SesameDeviceTargets.choices(listOf(front, back))

        assertEquals(
            listOf(
                SesameDeviceTargets.Choice(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, "全デバイス"),
                SesameDeviceTargets.Choice("uuid-front", "玄関"),
                // 表示名が空欄のデバイスはuuidをラベルにする（従来のチップ表示と同じ）。
                SesameDeviceTargets.Choice("uuid-back", "uuid-back"),
            ),
            choices,
        )
    }

    @Test
    fun `isAllDevices matches only the all devices target`() {
        assertTrue(SesameDeviceTargets.isAllDevices(SesameWearProtocol.ALL_DEVICES_TARGET_UUID))
        assertFalse(SesameDeviceTargets.isAllDevices("uuid-front"))
        assertFalse(SesameDeviceTargets.isAllDevices(SesameDemoMode.DEMO_DEVICE_UUID))
    }

    @Test
    fun `display name uses fixed labels for demo and all devices`() {
        assertEquals("デモ", SesameDeviceTargets.displayName(SesameDemoMode.DEMO_DEVICE_UUID, listOf(front)))
        assertEquals(
            "全デバイス",
            SesameDeviceTargets.displayName(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front)),
        )
    }

    @Test
    fun `display name falls back to uuid when blank or not registered`() {
        val registered = listOf(front, back)

        assertEquals("玄関", SesameDeviceTargets.displayName("uuid-front", registered))
        assertEquals("uuid-back", SesameDeviceTargets.displayName("uuid-back", registered))
        assertEquals("uuid-removed", SesameDeviceTargets.displayName("uuid-removed", registered))
    }

    @Test
    fun `target uuids expand all devices in registration order`() {
        assertEquals(
            listOf("uuid-front", "uuid-back"),
            SesameDeviceTargets.targetUuids(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, listOf(front, back)),
        )
        assertEquals(
            emptyList<String>(),
            SesameDeviceTargets.targetUuids(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, emptyList()),
        )
    }

    @Test
    fun `target uuids for a single device is just that device`() {
        assertEquals(listOf("uuid-back"), SesameDeviceTargets.targetUuids("uuid-back", listOf(front, back)))
        assertEquals(
            listOf(SesameDemoMode.DEMO_DEVICE_UUID),
            SesameDeviceTargets.targetUuids(SesameDemoMode.DEMO_DEVICE_UUID, emptyList()),
        )
    }
}

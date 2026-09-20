package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDeviceCycleTest {
    private val twoDevices =
        listOf(
            SesameDeviceSummary(uuid = "uuid-a", displayName = "玄関"),
            SesameDeviceSummary(uuid = "uuid-b", displayName = "勝手口"),
        )

    @Test
    fun `forward follows the order of the selection screen`() {
        // 2台以上のときの並びは「全デバイス」→ 登録順（SesameDeviceTargets.choices と同じ）。
        assertEquals(
            "uuid-a",
            WidgetDeviceCycle.next(
                SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
                twoDevices,
                WidgetDeviceCycle.FORWARD,
            ),
        )
        assertEquals("uuid-b", WidgetDeviceCycle.next("uuid-a", twoDevices, WidgetDeviceCycle.FORWARD))
    }

    @Test
    fun `forward wraps around at the end`() {
        assertEquals(
            SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
            WidgetDeviceCycle.next("uuid-b", twoDevices, WidgetDeviceCycle.FORWARD),
        )
    }

    @Test
    fun `backward wraps around at the beginning`() {
        assertEquals(
            "uuid-b",
            WidgetDeviceCycle.next(
                SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
                twoDevices,
                WidgetDeviceCycle.BACKWARD,
            ),
        )
        assertEquals("uuid-a", WidgetDeviceCycle.next("uuid-b", twoDevices, WidgetDeviceCycle.BACKWARD))
    }

    @Test
    fun `a single device has no other choice`() {
        // 1台のみなら「全デバイス」は出ない（冗長なため）ので、順送りしても同じuuidへ戻る。
        val single = listOf(SesameDeviceSummary(uuid = "uuid-a", displayName = "玄関"))
        assertEquals("uuid-a", WidgetDeviceCycle.next("uuid-a", single, WidgetDeviceCycle.FORWARD))
        assertEquals("uuid-a", WidgetDeviceCycle.next("uuid-a", single, WidgetDeviceCycle.BACKWARD))
    }

    @Test
    fun `with no registered device only the demo device is offered`() {
        assertEquals(
            SesameDemoMode.DEMO_DEVICE_UUID,
            WidgetDeviceCycle.next(SesameDemoMode.DEMO_DEVICE_UUID, emptyList(), WidgetDeviceCycle.FORWARD),
        )
    }

    @Test
    fun `an assignment that is no longer offered falls back to the first choice`() {
        // 割り当て済みのデバイスが削除された場合。順送りで先頭（2台以上なら「全デバイス」）へ戻す。
        assertEquals(
            SesameWearProtocol.ALL_DEVICES_TARGET_UUID,
            WidgetDeviceCycle.next("removed-uuid", twoDevices, WidgetDeviceCycle.FORWARD),
        )
    }
}

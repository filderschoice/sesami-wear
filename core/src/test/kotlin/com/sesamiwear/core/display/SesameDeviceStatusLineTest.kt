package com.sesamiwear.core.display

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneId

/**
 * スマートフォンとウォッチの状態一覧に出す1行の検証（BL-169 / BL-170）。
 * 分かっていない項目が出ないこと、経路がアイコンではなく語で出ることを固定する。
 */
class SesameDeviceStatusLineTest {
    @Test
    fun `a fully known snapshot shows every part`() {
        val line =
            SesameDeviceStatusLine.label(
                snapshot =
                    SesameStatusSnapshot(
                        isLocked = true,
                        updatedAtEpochMillis = NOW - 3 * 60_000L,
                        batteryPercentage = 85,
                        position = 42,
                        lastRoute = SesameStatusRoute.BLE,
                    ),
                nowEpochMillis = NOW,
                zoneId = ZONE,
            )

        assertEquals("施錠中 ・ 電池85% ・ 3分前 ・ Bluetooth", line)
    }

    @Test
    fun `the two line form splits the state from when it was taken`() {
        // スマートフォンのデバイスカード（BL-177）。経路はどちらの行にも入れない（カード側で描くため）。
        val lines =
            SesameDeviceStatusLine.lines(
                snapshot =
                    SesameStatusSnapshot(
                        isLocked = true,
                        updatedAtEpochMillis = NOW - 3 * 60_000L,
                        batteryPercentage = 85,
                        position = 42,
                        lastRoute = SesameStatusRoute.BLE,
                    ),
                nowEpochMillis = NOW,
                zoneId = ZONE,
                includePosition = true,
            )

        assertEquals("施錠中 ・ 電池85% ・ 角度42", lines.statusLine)
        assertEquals("3分前", lines.freshnessLine)
    }

    @Test
    fun `the two line form leaves the state line empty when nothing is known`() {
        val lines = SesameDeviceStatusLine.lines(snapshot = null, nowEpochMillis = NOW, zoneId = ZONE)

        assertEquals("", lines.statusLine)
        assertEquals(SesameDeviceStatusLine.NEVER_FETCHED_LABEL, lines.freshnessLine)
    }

    @Test
    fun `the angle is only shown when it is asked for`() {
        val snapshot =
            SesameStatusSnapshot(
                isLocked = false,
                updatedAtEpochMillis = NOW,
                position = 42,
                lastRoute = SesameStatusRoute.WEB_API,
            )

        assertEquals(
            "解錠中 ・ 角度42 ・ たった今 ・ インターネット",
            SesameDeviceStatusLine.label(snapshot, NOW, ZONE, includePosition = true),
        )
        assertFalse(SesameDeviceStatusLine.label(snapshot, NOW, ZONE).contains("角度"))
    }

    @Test
    fun `a device that was never fetched shows only that`() {
        assertEquals(
            SesameDeviceStatusLine.NEVER_FETCHED_LABEL,
            SesameDeviceStatusLine.label(snapshot = null, nowEpochMillis = NOW, zoneId = ZONE),
        )
    }

    @Test
    fun `unknown parts are left out`() {
        val line =
            SesameDeviceStatusLine.label(
                snapshot = SesameStatusSnapshot(isLocked = true, updatedAtEpochMillis = NOW),
                nowEpochMillis = NOW,
                zoneId = ZONE,
            )

        assertEquals("施錠中 ・ たった今", line)
    }

    @Test
    fun `a failure replaces the freshness and keeps the other parts`() {
        val line =
            SesameDeviceStatusLine.label(
                snapshot =
                    SesameStatusSnapshot(
                        isLocked = true,
                        updatedAtEpochMillis = NOW - 60 * 60_000L,
                        lastFailure = SesameStatusFailure.COMMUNICATION,
                        batteryPercentage = 40,
                        lastRoute = SesameStatusRoute.WEB_API,
                    ),
                nowEpochMillis = NOW,
                zoneId = ZONE,
            )

        assertEquals("施錠中 ・ 電池40% ・ 通信エラー（電波状況を確認） ・ インターネット", line)
    }

    @Test
    fun `the route is written as a word instead of an icon`() {
        val line =
            SesameDeviceStatusLine.label(
                snapshot =
                    SesameStatusSnapshot(
                        isLocked = true,
                        updatedAtEpochMillis = NOW,
                        lastRoute = SesameStatusRoute.BLE,
                    ),
                nowEpochMillis = NOW,
                zoneId = ZONE,
            )

        assertFalse(line.contains(SesameRouteLabel.ICON_BLE))
        assertEquals(true, line.endsWith("Bluetooth"))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
    }
}

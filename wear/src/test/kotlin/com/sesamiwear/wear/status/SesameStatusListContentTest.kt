package com.sesamiwear.wear.status

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * ウォッチの状態一覧の行の検証（BL-170）。
 *
 * 円形画面では行が長いと行頭・行末が見切れるため（BL-114）、各行が
 * [SesameStatusListContent.MAX_LINE_CHARS]の目安に収まることもあわせて確認する。
 */
class SesameStatusListContentTest {
    @Test
    fun `a known device shows its state, battery and route`() {
        val rows =
            rowsOf(
                SesameStatusSnapshot(
                    true,
                    NOW - 3 * 60_000L,
                    batteryPercentage = 85,
                    lastRoute = SesameStatusRoute.BLE,
                ),
            )

        assertEquals("玄関", rows.single().displayName)
        assertEquals("🔒施錠中 🔋85%", rows.single().statusLine)
        assertEquals("🔗3分前", rows.single().detailLine)
    }

    @Test
    fun `a device without a battery reading shows only the state`() {
        val rows = rowsOf(SesameStatusSnapshot(false, NOW, lastRoute = SesameStatusRoute.WEB_API))

        assertEquals("🔓解錠中", rows.single().statusLine)
        assertEquals("🌐たった今", rows.single().detailLine)
    }

    @Test
    fun `a device that was never fetched shows unknown and never fetched`() {
        val rows = rowsOf(null)

        assertEquals("❔状態不明", rows.single().statusLine)
        assertEquals("未取得", rows.single().detailLine)
    }

    @Test
    fun `a failure is shown instead of the freshness`() {
        val rows =
            rowsOf(
                SesameStatusSnapshot(
                    isLocked = true,
                    updatedAtEpochMillis = NOW - 60 * 60_000L,
                    lastFailure = SesameStatusFailure.AUTH_OR_QUOTA,
                    lastRoute = SesameStatusRoute.WEB_API,
                ),
            )

        assertEquals("🌐認証エラー", rows.single().detailLine)
    }

    @Test
    fun `the uuid is used when the display name is blank`() {
        val rows =
            SesameStatusListContent.rows(
                devices = listOf(SesameDeviceSummary(uuid = "uuid-1", displayName = "")),
                snapshotOf = { null },
                nowEpochMillis = NOW,
                zoneId = ZONE,
            )

        assertEquals("uuid-1", rows.single().displayName)
    }

    @Test
    fun `every device gets a row in the given order`() {
        val rows =
            SesameStatusListContent.rows(
                devices =
                    listOf(
                        SesameDeviceSummary(uuid = "a", displayName = "玄関"),
                        SesameDeviceSummary(uuid = "b", displayName = "勝手口"),
                    ),
                snapshotOf = { null },
                nowEpochMillis = NOW,
                zoneId = ZONE,
            )

        assertEquals(listOf("玄関", "勝手口"), rows.map { it.displayName })
    }

    @Test
    fun `lines stay within the width that a round screen can show`() {
        // 全角1・半角0.5で数えた長さが目安の上限に収まること（BL-114）。
        val rows =
            rowsOf(
                SesameStatusSnapshot(
                    isLocked = true,
                    updatedAtEpochMillis = NOW - 23 * 60 * 60_000L,
                    batteryPercentage = 100,
                    lastRoute = SesameStatusRoute.WEB_API,
                ),
            )

        listOf(rows.single().statusLine, rows.single().detailLine).forEach { line ->
            assertTrue(line, widthOf(line) <= SesameStatusListContent.MAX_LINE_CHARS)
        }
    }

    @Test
    fun `the empty message is split into lines that fit`() {
        SesameStatusListContent.EMPTY_LINES.forEach { line ->
            assertTrue(line, widthOf(line) <= SesameStatusListContent.MAX_LINE_CHARS)
        }
    }

    private fun rowsOf(snapshot: SesameStatusSnapshot?) =
        SesameStatusListContent.rows(
            devices = listOf(SesameDeviceSummary(uuid = "uuid-1", displayName = "玄関")),
            snapshotOf = { snapshot },
            nowEpochMillis = NOW,
            zoneId = ZONE,
        )

    /** 全角を1、半角を0.5として数えた表示幅の目安。 */
    private fun widthOf(line: String): Double = line.sumOf { if (it.code < ASCII_LIMIT) HALF_WIDTH else FULL_WIDTH }

    private companion object {
        const val NOW = 1_700_000_000_000L
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        const val ASCII_LIMIT = 0x80
        const val HALF_WIDTH = 0.5
        const val FULL_WIDTH = 1.0
    }
}

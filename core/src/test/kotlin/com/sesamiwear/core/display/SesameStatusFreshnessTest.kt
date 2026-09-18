package com.sesamiwear.core.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SesameStatusFreshnessTest {
    @Test
    fun `never fetched shows the dedicated label`() {
        assertEquals("未取得", SesameStatusFreshness.label(null, NOW, ZONE))
    }

    @Test
    fun `within a minute shows just now`() {
        assertEquals("たった今", SesameStatusFreshness.label(NOW, NOW, ZONE))
        assertEquals("たった今", SesameStatusFreshness.label(NOW - 59_000L, NOW, ZONE))
    }

    @Test
    fun `a future timestamp from a skewed clock is treated as just now`() {
        assertEquals("たった今", SesameStatusFreshness.label(NOW + 10 * 60_000L, NOW, ZONE))
    }

    @Test
    fun `under an hour shows elapsed minutes`() {
        assertEquals("1分前", SesameStatusFreshness.label(NOW - 60_000L, NOW, ZONE))
        assertEquals("59分前", SesameStatusFreshness.label(NOW - 59 * 60_000L, NOW, ZONE))
    }

    @Test
    fun `under a day shows elapsed hours`() {
        assertEquals("1時間前", SesameStatusFreshness.label(NOW - 60 * 60_000L, NOW, ZONE))
        assertEquals("23時間前", SesameStatusFreshness.label(NOW - 23 * 60 * 60_000L, NOW, ZONE))
    }

    @Test
    fun `a day or older shows the date only`() {
        // 2026-09-18 12:00 JSTのちょうど24時間前は前日の同時刻。
        assertEquals("9/17", SesameStatusFreshness.label(NOW - 24 * 60 * 60_000L, NOW, ZONE))
        assertEquals("9/1", SesameStatusFreshness.label(instantOf(2026, 9, 1, 23), NOW, ZONE))
        assertEquals("12/31", SesameStatusFreshness.label(instantOf(2025, 12, 31, 8), NOW, ZONE))
    }

    @Test
    fun `date uses the given zone rather than the system default`() {
        // UTCの2026-09-16 20:00はJSTでは2026-09-17 05:00。同じ時刻でも日付表記が変わることを示す。
        val utcEvening = instantOf(2026, 9, 16, 20, ZoneId.of("UTC"))

        assertEquals("9/16", SesameStatusFreshness.label(utcEvening, NOW, ZoneId.of("UTC")))
        assertEquals("9/17", SesameStatusFreshness.label(utcEvening, NOW, ZONE))
    }

    @Test
    fun `aggregate uses the oldest timestamp`() {
        assertEquals(100L, SesameStatusFreshness.oldestOf(listOf(300L, 100L, 200L)))
    }

    @Test
    fun `aggregate is never fetched when any device is missing or the list is empty`() {
        assertNull(SesameStatusFreshness.oldestOf(listOf(300L, null)))
        assertNull(SesameStatusFreshness.oldestOf(emptyList()))
    }

    private fun instantOf(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        zoneId: ZoneId = ZONE,
    ): Long = ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zoneId).toInstant().toEpochMilli()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        val NOW: Long = ZonedDateTime.of(2026, 9, 18, 12, 0, 0, 0, ZONE).toInstant().toEpochMilli()
    }
}

package com.sesamiwear.mobile.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ApiUsageCounterTest {
    private val store = InMemoryKeyValueStore()
    private val counter = ApiUsageCounter(store, ZONE)

    @Test
    fun `starts at zero`() {
        assertEquals(0, counter.countOf(SEPTEMBER_18))
    }

    @Test
    fun `counts calls within the same month`() {
        assertEquals(1, counter.record(SEPTEMBER_18))
        assertEquals(2, counter.record(SEPTEMBER_30_LATE))
        assertEquals(2, counter.countOf(SEPTEMBER_18))
    }

    @Test
    fun `starts over when the month changes`() {
        counter.record(SEPTEMBER_18)
        counter.record(SEPTEMBER_30_LATE)

        assertEquals(1, counter.record(OCTOBER_1_EARLY))
        assertEquals(1, counter.countOf(OCTOBER_1_EARLY))
    }

    @Test
    fun `a past month reads as zero without erasing the stored month`() {
        counter.record(OCTOBER_1_EARLY)

        assertEquals(0, counter.countOf(SEPTEMBER_18))
        assertEquals(1, counter.countOf(OCTOBER_1_EARLY))
    }

    @Test
    fun `the month boundary follows the given zone`() {
        // 2026-10-01 00:30 JST は UTC では 2026-09-30 15:30。
        val utcCounter = ApiUsageCounter(store, ZoneId.of("UTC"))
        utcCounter.record(SEPTEMBER_30_LATE)

        // JSTでは月をまたいでいるので数え直し、UTCではまだ同じ月なので加算される。
        assertEquals(2, utcCounter.record(OCTOBER_1_EARLY))
    }

    @Test
    fun `a broken stored value is treated as zero`() {
        store.putString("api_usage_year_month", "2026-09")
        store.putString("api_usage_count", "壊れた値")

        assertEquals(0, counter.countOf(SEPTEMBER_18))
        assertEquals(1, counter.record(SEPTEMBER_18))
    }

    @Test
    fun `the label says the number is only an estimate from this app`() {
        val label = ApiUsageCounter.label(42)

        assertTrue(label, label.contains("42"))
        assertTrue(label, label.contains("目安"))
        // 他経路の消費を含まないことが分かる文言にする（上限までの残りと誤解されないため）。
        assertTrue(label, label.contains("このアプリ"))
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        val SEPTEMBER_18: Long = epochMillisOf(2026, 9, 18, 12, 0)
        val SEPTEMBER_30_LATE: Long = epochMillisOf(2026, 9, 30, 23, 59)
        val OCTOBER_1_EARLY: Long = epochMillisOf(2026, 10, 1, 0, 30)

        fun epochMillisOf(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
        ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()
    }
}

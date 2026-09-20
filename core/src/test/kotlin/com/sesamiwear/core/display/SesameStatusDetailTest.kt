package com.sesamiwear.core.display

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SesameStatusDetailTest {
    @Test
    fun `without a failure it shows how old the state is`() {
        assertEquals("5分前", SesameStatusDetail.compactLabel(null, NOW - 5 * 60_000L, NOW, ZONE))
        assertEquals("5分前", SesameStatusDetail.detailedLabel(null, NOW - 5 * 60_000L, NOW, ZONE))
    }

    @Test
    fun `without a failure and without a fetch it shows never fetched`() {
        assertEquals("未取得", SesameStatusDetail.compactLabel(null, null, NOW, ZONE))
        assertEquals("未取得", SesameStatusDetail.detailedLabel(null, null, NOW, ZONE))
    }

    @Test
    fun `a failure wins over the freshness because it is the newer information`() {
        val justFetched = NOW - 1_000L

        assertEquals(
            "認証エラー",
            SesameStatusDetail.compactLabel(SesameStatusFailure.AUTH_OR_QUOTA, justFetched, NOW, ZONE),
        )
        assertEquals(
            "通信エラー",
            SesameStatusDetail.compactLabel(SesameStatusFailure.COMMUNICATION, justFetched, NOW, ZONE),
        )
    }

    @Test
    fun `the widget wording adds what to do next`() {
        assertEquals(
            "認証エラー（設定を確認）",
            SesameStatusDetail.detailedLabel(SesameStatusFailure.AUTH_OR_QUOTA, NOW, NOW, ZONE),
        )
        assertEquals(
            "通信エラー（電波状況を確認）",
            SesameStatusDetail.detailedLabel(SesameStatusFailure.COMMUNICATION, NOW, NOW, ZONE),
        )
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        val NOW: Long = ZonedDateTime.of(2026, 9, 18, 12, 0, 0, 0, ZONE).toInstant().toEpochMilli()
    }

    @Test
    fun `the route icon is prefixed to the compact label`() {
        assertEquals(
            "🔗3分前",
            SesameStatusDetail.compactLabel(
                failure = null,
                updatedAtEpochMillis = NOW - 3 * 60 * 1000,
                nowEpochMillis = NOW,
                route = SesameStatusRoute.BLE,
            ),
        )
    }

    @Test
    fun `the route icon is prefixed to a failure label too`() {
        assertEquals(
            "🌐認証エラー",
            SesameStatusDetail.compactLabel(
                failure = SesameStatusFailure.AUTH_OR_QUOTA,
                updatedAtEpochMillis = NOW,
                nowEpochMillis = NOW,
                route = SesameStatusRoute.WEB_API,
            ),
        )
    }

    @Test
    fun `nothing is prefixed when the route is unknown`() {
        assertEquals(
            "3分前",
            SesameStatusDetail.compactLabel(
                failure = null,
                updatedAtEpochMillis = NOW - 3 * 60 * 1000,
                nowEpochMillis = NOW,
                route = null,
            ),
        )
    }

    @Test
    fun `the detailed label also carries the route icon`() {
        assertEquals(
            "🔗たった今",
            SesameStatusDetail.detailedLabel(
                failure = null,
                updatedAtEpochMillis = NOW,
                nowEpochMillis = NOW,
                route = SesameStatusRoute.BLE,
            ),
        )
    }
}

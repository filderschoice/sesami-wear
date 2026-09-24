package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 撮影モードの操作画面の選択肢（BL-213）。 */
class ShowcaseStateOptionsTest {
    @Test
    fun `経過時間はそれ以下で最も大きい選択肢に当たる`() {
        assertEquals("たった今", ShowcaseStateOptions.ageOptionOf(0).label)
        assertEquals("たった今", ShowcaseStateOptions.ageOptionOf(4 * 60_000L).label)
        assertEquals("5分前", ShowcaseStateOptions.ageOptionOf(8 * 60_000L).label)
        assertEquals("1時間前", ShowcaseStateOptions.ageOptionOf(2 * 3_600_000L).label)
        assertEquals("1日前", ShowcaseStateOptions.ageOptionOf(3 * 86_400_000L).label)
        assertEquals("たった今", ShowcaseStateOptions.ageOptionOf(-1).label)
    }

    @Test
    fun `各項目の選択肢は値が重複しない`() {
        listOf(
            ShowcaseStateOptions.lock,
            ShowcaseStateOptions.battery,
            ShowcaseStateOptions.route,
            ShowcaseStateOptions.failure,
            ShowcaseStateOptions.age,
        ).forEach { options -> assertEquals(options.size, options.map { it.value }.distinct().size) }
        assertEquals(SesameStatusFailure.entries.size + 1, ShowcaseStateOptions.failure.size)
    }

    @Test
    fun `保存値から状態へ戻すと経過時間は今との差になる`() {
        val snapshot =
            SesameStatusSnapshot(
                isLocked = true,
                updatedAtEpochMillis = 40_000,
                lastFailure = SesameStatusFailure.COMMUNICATION,
                batteryPercentage = 50,
                position = 3,
                lastRoute = SesameStatusRoute.WEB_API,
            )

        val state = ShowcaseDeviceState.of(snapshot, nowMillis = 100_000)

        assertEquals(
            ShowcaseDeviceState(true, 50, 3, SesameStatusRoute.WEB_API, SesameStatusFailure.COMMUNICATION, 60_000),
            state,
        )
    }

    @Test
    fun `保存値が無ければ未取得になる`() {
        val state = ShowcaseDeviceState.of(null, nowMillis = 100_000)

        assertNull(state.isLocked)
        assertEquals(0L, state.ageMillis)
    }
}

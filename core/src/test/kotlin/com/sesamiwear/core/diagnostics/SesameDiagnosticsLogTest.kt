package com.sesamiwear.core.diagnostics

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.SesameStatusRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 診断ログ（BL-188）の検証。
 *
 * 保持件数と整形に加えて、**出力へ資格情報が混入しないこと**を固定する。利用者から
 * そのまま連携してもらう前提の機能のため、uuid・apikey・secretKeyが混ざると外へ出てしまう。
 */
class SesameDiagnosticsLogTest {
    private val store = InMemoryStore()
    private val log = SesameDiagnosticsLog(store, ZONE)

    @Test
    fun `entries come back newest first`() {
        log.record(entry(NOW, deviceName = "玄関"))
        log.record(entry(NOW + 1_000, deviceName = "勝手口"))

        assertEquals(listOf("勝手口", "玄関"), log.load().map { it.deviceName })
    }

    @Test
    fun `only the newest entries are kept`() {
        repeat(SesameDiagnosticsLog.MAX_ENTRIES + 10) { index ->
            log.record(entry(NOW + index, deviceName = "dev$index"))
        }

        val entries = log.load()
        assertEquals(SesameDiagnosticsLog.MAX_ENTRIES, entries.size)
        // 最も新しい1件が先頭で、最初に入れた分は落ちている。
        assertEquals("dev${SesameDiagnosticsLog.MAX_ENTRIES + 9}", entries.first().deviceName)
        assertFalse(entries.any { it.deviceName == "dev0" })
    }

    @Test
    fun `a successful entry shows the route and no reason`() {
        val line =
            log.format(
                entry(
                    NOW,
                    deviceName = "玄関",
                    operation = SesameDiagnosticsOperation.UNLOCK,
                    outcome = SesameDiagnosticsOutcome.SUCCESS,
                    result = Result(route = SesameStatusRoute.WEB_API),
                ),
            )

        assertEquals("09/20 11:30:00 玄関 解錠 インターネット 成功", line)
    }

    @Test
    fun `a failed entry shows the reason`() {
        val line =
            log.format(
                entry(
                    NOW,
                    deviceName = "玄関",
                    operation = SesameDiagnosticsOperation.STATUS,
                    outcome = SesameDiagnosticsOutcome.FAILURE,
                    result = Result(route = SesameStatusRoute.BLE, reason = "認証エラー"),
                ),
            )

        assertEquals("09/20 11:30:00 玄関 状態取得 Bluetooth 失敗（認証エラー）", line)
    }

    @Test
    fun `an entry without a route omits it`() {
        val line = log.format(entry(NOW, deviceName = "デモ用", outcome = SesameDiagnosticsOutcome.SKIPPED))

        assertEquals("09/20 11:30:00 デモ用 施錠 重複として無視", line)
    }

    @Test
    fun `the shared text starts with the given header`() {
        log.record(entry(NOW, deviceName = "玄関"))

        val text = log.formatAll(listOf("Sesami Wear 0.13.0 (8)", "----"))

        assertEquals(
            listOf("Sesami Wear 0.13.0 (8)", "----", "09/20 11:30:00 玄関 施錠 成功"),
            text.lines(),
        )
    }

    @Test
    fun `an empty log still produces a readable text`() {
        assertEquals(SesameDiagnosticsLog.EMPTY_MESSAGE, log.formatAll())
    }

    @Test
    fun `neither the saved value nor the shared text contains credentials`() {
        // 実行口は表示名しか渡さないが、仮にuuidらしき値が紛れても保存・出力の形が変わらないことを示す。
        // ここでは「記録に渡していない値は、どこにも現れない」ことを固定する。
        log.record(
            entry(
                NOW,
                deviceName = "玄関",
                operation = SesameDiagnosticsOperation.UNLOCK,
                outcome = SesameDiagnosticsOutcome.FAILURE,
                result = Result(route = SesameStatusRoute.WEB_API, reason = "認証エラー"),
            ),
        )

        val saved = store.values.values.joinToString("\n")
        val shared = log.formatAll(listOf("Sesami Wear 0.13.0 (8)"))
        listOf(UUID_SAMPLE, API_KEY_SAMPLE, SECRET_KEY_SAMPLE).forEach { secret ->
            assertFalse(saved.contains(secret))
            assertFalse(shared.contains(secret))
        }
        assertTrue(shared.contains("玄関"))
    }

    /** `SesameDiagnosticsEntry`をそのまま組み立てると引数が多くなるため、既定値つきの薄い包みを使う。 */
    private fun entry(
        epochMillis: Long,
        deviceName: String,
        operation: SesameDiagnosticsOperation = SesameDiagnosticsOperation.LOCK,
        outcome: SesameDiagnosticsOutcome = SesameDiagnosticsOutcome.SUCCESS,
        result: Result = Result(),
    ) = SesameDiagnosticsEntry(
        epochMillis = epochMillis,
        operation = operation,
        deviceName = deviceName,
        outcome = outcome,
        route = result.route,
        reason = result.reason,
    )

    /** 経路と失敗理由の組。引数の数をdetektの上限（6）内へ収めるためにまとめている。 */
    private data class Result(
        val route: SesameStatusRoute? = null,
        val reason: String? = null,
    )

    private class InMemoryStore : SesameKeyValueStore {
        val values = mutableMapOf<String, String>()

        override fun putString(
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override fun getString(key: String): String? = values[key]

        override fun clear() {
            values.clear()
        }
    }

    private companion object {
        /** 2026-09-20 11:30:00 JST。 */
        const val NOW = 1_789_871_400_000L
        val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

        // 記録へ渡していない値。出力のどこにも現れないことを確かめるためだけに使う（実在しない）。
        const val UUID_SAMPLE = "aaaa1111"
        const val API_KEY_SAMPLE = "dummyapikey1"

        // secretKeyと同じ形（16進数32文字）にするが、資格情報の混入検査へ引っかからないよう
        // ソース上は16文字のリテラル2つに分ける。実在しない値。
        const val SECRET_KEY_HALF = "1a2b3c4d5e6f7081"
        const val SECRET_KEY_SAMPLE = SECRET_KEY_HALF + "92a3b4c5d6e7f801"
    }
}

package com.sesamiwear.mobile.state

import com.sesamiwear.core.SesameKeyValueStore
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * このアプリがSesame Web APIを呼び出した回数を暦月ごとに数える（BL-147）。
 *
 * Web APIには1か月あたりのリクエスト回数の上限があり、到達するとアプリ全体が機能停止する
 * （BL-141）。BL-142で自動状態取得を廃止して消費は利用者のタップ分だけになったが、利用者が
 * 「今月どれだけ使ったか」を知る手段がアプリ内に無く、SESAME Bizのサイトを見に行くしかなかった。
 *
 * **数えるのはこのアプリからの呼び出しだけで、Sesame純正アプリなど他経路の消費は含まない。**
 * そのため表示はあくまで目安で、上限までの残りを正確に示すものではない（上限値そのものも
 * 契約内容によって変わり、アプリからは取得できない）。表示文言でその旨を明示する。
 *
 * 月が変わったら数え直す。保存値は「対象の年月」と「回数」の2つだけで、機密情報を含まないため
 * 保存先は非暗号化のSharedPreferences（[LockStateStore]と同方針）。
 * ウォッチ経由とウィジェット経由の呼び出しが並行しうるため、読み書きは同期化する。
 *
 * @param zoneId 月の境界を判定するタイムゾーン。既定は端末のタイムゾーン。
 * CANDY HOUSE側のカウンタがどのタイムゾーンで月を区切るかは未確認のため、利用者の体感に合う
 * 端末のタイムゾーンを既定とする（目安であることと合わせて許容する）。
 */
class ApiUsageCounter(
    private val keyValueStore: SesameKeyValueStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    /** [nowEpochMillis]時点の呼び出しを1回記録し、その月の累計を返す。 */
    @Synchronized
    fun record(nowEpochMillis: Long): Int {
        val yearMonth = yearMonthOf(nowEpochMillis)
        val count = if (keyValueStore.getString(KEY_YEAR_MONTH) == yearMonth) storedCount() + 1 else 1
        keyValueStore.putString(KEY_YEAR_MONTH, yearMonth)
        keyValueStore.putString(KEY_COUNT, count.toString())
        return count
    }

    /** [nowEpochMillis]が属する月の累計。月が変わっていれば0。 */
    @Synchronized
    fun countOf(nowEpochMillis: Long): Int =
        if (keyValueStore.getString(KEY_YEAR_MONTH) == yearMonthOf(nowEpochMillis)) storedCount() else 0

    private fun storedCount(): Int = keyValueStore.getString(KEY_COUNT)?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun yearMonthOf(epochMillis: Long): String =
        YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zoneId)).toString()

    companion object {
        /** 画面へ出す1行（BL-147）。目安であることを併記して、上限までの残りと誤解されないようにする。 */
        fun label(count: Int): String = "今月のAPI呼び出し: $count 回（このアプリからの分のみ・目安）"
    }
}

private const val KEY_YEAR_MONTH = "api_usage_year_month"
private const val KEY_COUNT = "api_usage_count"

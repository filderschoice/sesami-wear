package com.sesamiwear.core.display

import java.time.Instant
import java.time.ZoneId

/**
 * 「最後に状態を取得した時刻」の表示文言を決める（BL-142）。
 *
 * 自動状態取得を廃止した（Sesame Web APIの月間リクエスト上限を使い切るため）ことで、Tile・
 * Complication・ホーム画面ウィジェットの表示は「最後に分かった状態」を出し続ける。利用者が
 * その表示をいつまで信用してよいかを判断できるよう、状態と並べてこの文言を表示する。
 *
 * 24時間以内は相対表記（「3分前」）、それ以降は日付のみ（「9/17」）とする。相対表記は
 * 「どれだけ古いか」が一目で分かり、24時間を超えた状態は既に参考値のため日付まで分かれば足りる。
 * 一度も取得していない場合は[NEVER_FETCHED_LABEL]。
 *
 * wearのTileとmobileのウィジェットで文言を食い違わせないよう、両者が参照できるcoreに置く
 * （[SesameTileContent]と同方針、BL-119）。Android非依存のためユニットテスト対象。
 * `java.time`はminSdk 26で利用できる（desugaring不要）。
 */
object SesameStatusFreshness {
    /** 一度も状態を取得していない（保存済みスナップショットが無い）場合の文言。 */
    const val NEVER_FETCHED_LABEL = "未取得"

    /** 取得直後、および端末時計のずれで未来の時刻が保存されていた場合の文言。 */
    const val JUST_NOW_LABEL = "たった今"

    /**
     * [updatedAtEpochMillis]（nullなら未取得）の古さを表す文言を返す。
     *
     * @param nowEpochMillis 現在時刻。テストから固定できるよう引数で受ける。
     * @param zoneId 日付表記へ使うタイムゾーン。既定は端末のタイムゾーン。
     */
    fun label(
        updatedAtEpochMillis: Long?,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (updatedAtEpochMillis == null) return NEVER_FETCHED_LABEL
        val elapsedMillis = nowEpochMillis - updatedAtEpochMillis
        return when {
            elapsedMillis < MINUTE_MILLIS -> JUST_NOW_LABEL
            elapsedMillis < HOUR_MILLIS -> "${elapsedMillis / MINUTE_MILLIS}分前"
            elapsedMillis < DAY_MILLIS -> "${elapsedMillis / HOUR_MILLIS}時間前"
            else -> dateLabel(updatedAtEpochMillis, zoneId)
        }
    }

    /**
     * 複数デバイスを1つの表示へ集約する（「全デバイス」対象、BL-071）ときの代表値。
     * 最も古い取得時刻を返し、1台でも未取得があれば全体を未取得（null）として扱う。
     * 集約状態の判定（[com.sesamiwear.core.TileDisplayStateResolver.resolveAggregate]）が
     * 1台でも未取得なら「状態不明」にするのと同じ、最悪値を採る考え方で揃える。
     */
    fun oldestOf(updatedAtEpochMillisList: List<Long?>): Long? =
        if (updatedAtEpochMillisList.isEmpty() || updatedAtEpochMillisList.any { it == null }) {
            null
        } else {
            updatedAtEpochMillisList.filterNotNull().min()
        }

    private fun dateLabel(
        epochMillis: Long,
        zoneId: ZoneId,
    ): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return "${date.monthValue}/${date.dayOfMonth}"
    }

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
}

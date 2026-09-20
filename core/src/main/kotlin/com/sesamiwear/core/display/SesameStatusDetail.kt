package com.sesamiwear.core.display

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import java.time.ZoneId

/**
 * 状態表示に添える1行（状態文言の下）の内容を決める（BL-140）。
 *
 * 直近の取得・操作が失敗していればその理由、成功していれば最後に取得した時刻の古さ
 * （[SesameStatusFreshness]）を出す。失敗を優先するのは、失敗のほうが新しい情報であり、
 * かつ利用者が次に取るべき行動（設定の確認・電波状況の確認）に直結するため。
 * 失敗しているときは表示している状態が古いことも同時に意味するため、鮮度を落としても情報は失われない。
 *
 * 表示している施錠状態そのものは失敗しても「状態不明」へ戻さず、最後に分かった状態を残す
 * （BL-142で自動取得を廃止し「最後に分かった状態を出し続ける」設計にしたことと揃える）。
 *
 * 先頭には最後に使った経路のアイコン（🔗 / 🌐、BL-173）を前置する（BL-168）。Tile・Complication・
 * ウィジェットはいずれも表示余白が無く、過去に文言が収まらず省略された事例がある
 * （BL-102 / BL-104 / BL-158）ため、**行を増やさずこの1行へ相乗りさせる**。
 * 経路が分からない場合は何も前置しない。
 */
object SesameStatusDetail {
    /** Tile・Complication向け（表示領域が狭い面）。 */
    fun compactLabel(
        failure: SesameStatusFailure?,
        updatedAtEpochMillis: Long?,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        route: SesameStatusRoute? = null,
    ): String =
        SesameRouteLabel.withIcon(
            route,
            failure?.shortLabel ?: SesameStatusFreshness.label(updatedAtEpochMillis, nowEpochMillis, zoneId),
        )

    /** ホーム画面ウィジェット向け（表示領域に余裕がある面）。 */
    fun detailedLabel(
        failure: SesameStatusFailure?,
        updatedAtEpochMillis: Long?,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        route: SesameStatusRoute? = null,
    ): String =
        SesameRouteLabel.withIcon(
            route,
            failure?.detailedLabel ?: SesameStatusFreshness.label(updatedAtEpochMillis, nowEpochMillis, zoneId),
        )
}

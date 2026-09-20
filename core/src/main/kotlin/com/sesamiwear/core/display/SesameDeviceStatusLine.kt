package com.sesamiwear.core.display

import com.sesamiwear.core.SesameStatusSnapshot
import java.time.ZoneId

/**
 * 1台のセサミの状態を1行へまとめる（BL-169 / BL-170）。
 *
 * スマートフォンの資格情報設定画面のデバイス一覧と、ウォッチのアプリ本体の状態一覧で共用する。
 * どちらも表示領域に余裕があるため、Tileで使うアイコン（🔗 / 🌐）ではなく
 * **語で書く**（「Bluetooth」「インターネット」）。
 *
 * 分かっていない項目は出さない。何も分かっていなければ[NEVER_FETCHED_LABEL]だけを返す。
 *
 * Android非依存のためユニットテスト対象。
 */
object SesameDeviceStatusLine {
    /** 一度も状態が分かっていないデバイスの表示。 */
    const val NEVER_FETCHED_LABEL = "未取得"

    /** 項目の区切り。 */
    private const val SEPARATOR = " ・ "

    /**
     * [snapshot]を1行へまとめる。
     *
     * @param includePosition サムターンの角度を含めるか。角度はセサミが返す生の値で、利用者にとって
     * 意味を読み取りにくいため既定では出さない。設定画面のように情報量を許せる面でのみ有効にする。
     */
    fun label(
        snapshot: SesameStatusSnapshot?,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        includePosition: Boolean = false,
    ): String {
        val parts =
            buildList {
                lockLabel(snapshot)?.let { add(it) }
                snapshot?.batteryPercentage?.let { add("電池$it%") }
                if (includePosition) snapshot?.position?.let { add("角度$it") }
                add(freshnessLabel(snapshot, nowEpochMillis, zoneId))
                snapshot?.lastRoute?.let { add(SesameRouteLabel.name(it)) }
            }
        return parts.joinToString(SEPARATOR)
    }

    /** 施錠状態。一度も分かっていなければnull（鮮度の「未取得」だけで足りるため）。 */
    private fun lockLabel(snapshot: SesameStatusSnapshot?): String? =
        when (snapshot?.isLocked) {
            true -> "施錠中"
            false -> "解錠中"
            null -> null
        }

    /**
     * 直近の失敗があればその理由、無ければ最後に取得した時刻の古さ（[SesameStatusDetail]と同じ考え方）。
     * 経路はこの行の末尾へ別項目として並べるため、ここではアイコンを前置しない。
     */
    private fun freshnessLabel(
        snapshot: SesameStatusSnapshot?,
        nowEpochMillis: Long,
        zoneId: ZoneId,
    ): String =
        snapshot?.lastFailure?.detailedLabel
            ?: SesameStatusFreshness.label(snapshot?.updatedAtEpochMillis, nowEpochMillis, zoneId)
}

package com.sesamiwear.core.ble

/**
 * 1回のスキャンで複数台を同時に追うときの、台ごとの進捗の判定（BL-280）。
 *
 * 台ごとに[SesameBleSightingTracker]を持ち、電波を捉えたuuidは[onSighting]、見失ったかどうかは
 * 定期的に呼ぶ[onTick]で判定する（見失った台を1回だけ返す。呼ぶたびに同じ進捗を返さない規則は単体の場合と同じ）。
 * 対象外のuuidの電波は無視する。状態を持つため、確認1回につき1つ作る。時刻は引数で受け、テストから固定できる。
 */
class SesameBleMultiSightingTracker(
    uuids: List<String>,
    startedAtMillis: Long,
    lostAfterMillis: Long = SesameBleSightingTracker.DEFAULT_LOST_AFTER_MILLIS,
) {
    private val trackers = uuids.associateWith { SesameBleSightingTracker(startedAtMillis, lostAfterMillis) }

    /** [uuid]の電波を捉えた。平滑化したRSSIを載せた「発見」を返す。対象外のuuidならnull。 */
    fun onSighting(
        uuid: String,
        rssiDbm: Int,
        nowMillis: Long,
    ): SesameBleProbeSnapshot? = trackers[uuid]?.onSighting(rssiDbm, nowMillis)

    /** 見失った（または一度も捉えないまま制限時間が過ぎた）台を、台ごとに1回だけ「圏外」で返す。 */
    fun onTick(nowMillis: Long): Map<String, SesameBleProbeSnapshot> =
        trackers.mapNotNull { (uuid, tracker) -> tracker.onTick(nowMillis)?.let { uuid to it } }.toMap()
}

package com.sesamiwear.core.ble

/**
 * スキャンで捉えたSesameの電波から、レーダーに出す進捗（探索中 / 発見 / 圏外）を決める（BL-272）。
 *
 * 電波を捉えるたびに[onSighting]へ渡すと、RSSIを平滑化した「発見」を返す。一定時間
 * （[lostAfterMillis]）捉えられなければ、定期的に呼ぶ[onTick]が「圏外」を**1回だけ**返す
 * （呼ぶたびに同じ進捗を返すと、画面の更新とウォッチへの送信が無駄に続くため）。
 * 圏外になったあとも、電波を捉え直せば[onSighting]が「発見」へ戻す。
 * 時刻は引数で受け、テストから固定できる。状態を持つため、確認1回につき1つ作る。
 */
class SesameBleSightingTracker(
    private val startedAtMillis: Long,
    private val lostAfterMillis: Long = DEFAULT_LOST_AFTER_MILLIS,
    private val smoother: SesameBleRssiSmoother = SesameBleRssiSmoother(),
) {
    private var lastSeenAtMillis: Long? = null
    private var lostReported = false

    /** 確認の開始時点の進捗（探索中）。 */
    fun initial(): SesameBleProbeSnapshot = SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING)

    /** 電波を捉えた。平滑化したRSSIを載せた「発見」を返す。 */
    fun onSighting(
        rssiDbm: Int,
        nowMillis: Long,
    ): SesameBleProbeSnapshot {
        lastSeenAtMillis = nowMillis
        lostReported = false
        return SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = smoother.add(rssiDbm))
    }

    /** 見失った（または一度も捉えないまま制限時間が過ぎた）ときだけ「圏外」を返す。それ以外はnull。 */
    fun onTick(nowMillis: Long): SesameBleProbeSnapshot? {
        val reference = lastSeenAtMillis ?: startedAtMillis
        if (lostReported || nowMillis - reference < lostAfterMillis) return null
        lostReported = true
        smoother.reset()
        return SesameBleProbeSnapshot(SesameBleProbeStage.NOT_FOUND)
    }

    companion object {
        /**
         * 見失ったとみなすまでの時間。Sesameの広告間隔と、Androidのスキャンの間引きを考えると
         * 数秒は空きうるため、短すぎると近くにいても圏外へ点滅する。
         */
        const val DEFAULT_LOST_AFTER_MILLIS = 6_000L
    }
}

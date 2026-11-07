package com.sesamiwear.core.ble

/**
 * ウォッチへ進捗を送る頻度の間引き（BL-273）。
 *
 * 発見のRSSIは広告のたびに変わるため、そのまま送るとData Layerの通信が増え、小さな画面もちらつく。
 * **段階が変わったときは間隔によらず送り**、同じ段階の中の変化（RSSIの揺れ）は[minIntervalMillis]
 * 以上空いたときだけ送る。同じ内容は送らない。状態を持つため、確認1回につき1つ作る。
 * 時刻は引数で受け、テストから固定できる。
 */
class SesameBleProbeThrottle(
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) {
    private var lastSent: SesameBleProbeSnapshot? = null
    private var lastSentAtMillis = 0L

    /** [snapshot]を送ってよいか。trueを返した場合は送った扱いになる。 */
    fun shouldSend(
        snapshot: SesameBleProbeSnapshot,
        nowMillis: Long,
    ): Boolean {
        val previous = lastSent
        val send =
            when {
                snapshot == previous -> false
                previous == null || snapshot.stage != previous.stage -> true
                else -> nowMillis - lastSentAtMillis >= minIntervalMillis
            }
        if (send) {
            lastSent = snapshot
            lastSentAtMillis = nowMillis
        }
        return send
    }

    /** 送った記録を捨てる（画面側が内容を失った可能性があるとき、同じ内容でも送り直せるようにする）。 */
    fun reset() {
        lastSent = null
    }

    companion object {
        /** 同じ段階の中の変化を送る最短の間隔。 */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 800L
    }
}

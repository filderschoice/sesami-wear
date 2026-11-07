package com.sesamiwear.core.ble

/**
 * 複数台の進捗をウォッチへ送る頻度の間引き（BL-280）。[SesameBleProbeThrottle]と同じ考え方で、
 * **どれかの台の段階が変わったときは間隔によらず送り**、段階が同じままの変化（RSSIの揺れ）は
 * [minIntervalMillis]以上空いたときだけ送る。同じ内容は送らない。状態を持つため、確認1回につき1つ作る。
 * 時刻は引数で受け、テストから固定できる。
 */
class SesameBleMultiProbeThrottle(
    private val minIntervalMillis: Long = SesameBleProbeThrottle.DEFAULT_MIN_INTERVAL_MILLIS,
) {
    private var lastSent: SesameBleMultiProbeSnapshot? = null
    private var lastSentAtMillis = 0L

    /** [snapshot]を送ってよいか。trueを返した場合は送った扱いになる。 */
    fun shouldSend(
        snapshot: SesameBleMultiProbeSnapshot,
        nowMillis: Long,
    ): Boolean {
        val previous = lastSent
        val send =
            when {
                snapshot == previous -> false
                previous == null || stagesOf(snapshot) != stagesOf(previous) -> true
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

    private fun stagesOf(snapshot: SesameBleMultiProbeSnapshot): List<SesameBleProbeStage> =
        snapshot.entries.map { it.snapshot.stage }
}

package com.sesamiwear.core.api

/**
 * 施錠/解錠をWeb APIで送った後に取り直した状態と、送った命令の照合（BL-293）。
 *
 * Web APIの2xxはクラウドが受理しただけで実機の動作を保証しないため、利用者が設定で有効にしたときだけ、
 * 送信の数秒後に状態を1回取得して食い違いを見つける。Android非依存の純Kotlinでユニットテスト対象。
 */
object SesameCommandVerification {
    /**
     * 照合の結果。
     *
     * 古い値・timestampの無い値は、送信より前の状態かもしれず、食い違いを「未反映」と断定すると
     * 動作したのに失敗と誤報するため[UNVERIFIABLE]（安全側。成否は送信時点の扱いのまま）にする。
     */
    enum class Verdict {
        /** 取得した状態が送った命令と一致した。 */
        CONFIRMED,

        /** 送信より新しい状態が、送った命令と食い違っていた。 */
        NOT_REFLECTED,

        /** 応答が送信より古い（またはtimestampが無い）ため、判断できない。 */
        UNVERIFIABLE,
    }

    /**
     * [status]を[sentAtMillis]（命令を送った時刻、エポックミリ秒）と[command]に照らして判定する。
     *
     * timestampの単位は公式文書から確定できないため、秒とミリ秒のどちらでも比較できるようにしている
     * （1e11未満は秒、以上はミリ秒とみなす。秒なら1973年〜5138年、ミリ秒なら1970年〜1973年が秒側へ入る）。
     * 比較は秒単位で行う。端末の時計がサーバより遅れていると、送信前の古い状態を「新しい」と誤判定し、
     * 動作したのに「未反映」と誤報しうる。そのため、timestampが送信時刻より[marginSeconds]を超えて
     * 新しい場合だけ「新しい値」とみなし、それ以外は[Verdict.UNVERIFIABLE]へ倒す（誤報しない側）。
     * 代償として、端末の時計が正確で反映が余裕以内に済んだ場合も照合できない扱いになりうる。
     * [marginSeconds]の既定値は実機で調整する（[DEFAULT_CLOCK_SKEW_MARGIN_SECONDS]）。
     */
    fun judge(
        command: SesameCommand,
        status: SesameStatus,
        sentAtMillis: Long,
        marginSeconds: Long = DEFAULT_CLOCK_SKEW_MARGIN_SECONDS,
    ): Verdict {
        val timestampSeconds = status.timestamp?.let(::toSeconds)
        return when {
            timestampSeconds == null ||
                timestampSeconds <= sentAtMillis / MILLIS_PER_SECOND + marginSeconds -> Verdict.UNVERIFIABLE
            status.isInLockRange == (command == SesameCommand.LOCK) -> Verdict.CONFIRMED
            else -> Verdict.NOT_REFLECTED
        }
    }

    /**
     * 送信前に取った状態の[baselineSeconds]（[timestampSecondsOf]）と比べて判定する（BL-315）。
     *
     * サーバの値どうしの比較なので端末の時計に依存しない。送信後の`timestamp`が進んでいれば、状態が更新された
     * （命令が実機へ届いた）ので、命令と一致なら[Verdict.CONFIRMED]、食い違いなら[Verdict.NOT_REFLECTED]。
     * 進んでいない場合、状態が命令と食い違っていれば、実機へ届いていない（Hub 3のオフライン等）として
     * [Verdict.NOT_REFLECTED]、すでに命令どおりの状態なら更新が起きなくて当然なので[Verdict.UNVERIFIABLE]。
     * 反映が待ち時間より遅いと、動いたのに[Verdict.NOT_REFLECTED]になりうる（実機では約1秒以内だった）。
     */
    fun judgeAgainstBaseline(
        command: SesameCommand,
        status: SesameStatus,
        baselineSeconds: Long,
    ): Verdict {
        val timestampSeconds = timestampSecondsOf(status) ?: return Verdict.UNVERIFIABLE
        val matches = status.isInLockRange == (command == SesameCommand.LOCK)
        return when {
            timestampSeconds > baselineSeconds -> if (matches) Verdict.CONFIRMED else Verdict.NOT_REFLECTED
            matches -> Verdict.UNVERIFIABLE
            else -> Verdict.NOT_REFLECTED
        }
    }

    /** [status]の`timestamp`を秒へそろえた値。無ければnull。 */
    fun timestampSecondsOf(status: SesameStatus): Long? = status.timestamp?.let(::toSeconds)

    private fun toSeconds(timestamp: Long): Long =
        if (timestamp >= MILLIS_THRESHOLD) timestamp / MILLIS_PER_SECOND else timestamp

    /**
     * 端末時計のずれに備えた余裕（秒）の既定値。BL-306の実機確認（2026-11-22、Hub 3オンライン）で、
     * timestampは秒単位の最終状態更新時刻とみられ、反映は送信の0.4〜1.2秒後だった（状態取得の4〜7秒前）。
     * 余裕2秒だと正しい反映が毎回UNVERIFIABLEになるため0秒（送信時刻より後の秒だけを新しい値とする）にした。
     */
    const val DEFAULT_CLOCK_SKEW_MARGIN_SECONDS = 0L

    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_THRESHOLD = 100_000_000_000L
}

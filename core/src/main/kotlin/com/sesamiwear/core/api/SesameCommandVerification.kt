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
     * 比較は秒単位で行い、送信と同じ秒に付いた値は新しいものとして扱う。
     */
    fun judge(
        command: SesameCommand,
        status: SesameStatus,
        sentAtMillis: Long,
    ): Verdict {
        val timestampSeconds = status.timestamp?.let(::toSeconds)
        return when {
            timestampSeconds == null || timestampSeconds < sentAtMillis / MILLIS_PER_SECOND -> Verdict.UNVERIFIABLE
            status.isInLockRange == (command == SesameCommand.LOCK) -> Verdict.CONFIRMED
            else -> Verdict.NOT_REFLECTED
        }
    }

    private fun toSeconds(timestamp: Long): Long =
        if (timestamp >= MILLIS_THRESHOLD) timestamp / MILLIS_PER_SECOND else timestamp

    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_THRESHOLD = 100_000_000_000L
}

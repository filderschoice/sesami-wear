package com.sesamiwear.mobile.alert

/**
 * 予約した時刻に確かめた結果から、解錠したままの通知（BL-241）を出すかと、その本文を決める。
 *
 * - 取得できて解錠中なら通知する。
 * - 取得できなかった場合は、最後に分かっている状態が解錠中なら「確かめられなかった」旨を添えて通知する。
 *   閉め忘れを見逃すほうが、確かめられなかったことを知らせるより害が大きいため。
 * - 施錠中と分かった（取得結果・最後の状態のいずれか）なら通知しない。
 *
 * Android非依存のためユニットテスト対象。
 */
object UnlockReminderDecision {
    /** 通知の本文。通知しないならnull。 */
    fun messageOrNull(
        fetchedIsLocked: Boolean?,
        lastKnownIsLocked: Boolean?,
        minutes: Int,
    ): String? =
        when {
            fetchedIsLocked == false -> "解錠してから${minutes}分たちましたが、解錠したままです。"
            fetchedIsLocked == null && lastKnownIsLocked == false ->
                "解錠してから${minutes}分たちました。最新の状態を確かめられませんでしたが、解錠したままの可能性があります。"
            else -> null
        }

    /** 通知の見出し。 */
    fun title(deviceName: String): String = "$deviceName：解錠したままです"
}

package com.sesamiwear.mobile.alert

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.display.SesameTileContent

/**
 * 電池残量が少なくなったことを通知すべきかを判定する（BL-238）。
 *
 * 残量が[THRESHOLD_PERCENTAGE]未満へ**下がったときに1回だけ**通知する。下回ったままの間は、取得・操作の
 * たびに通知が積み上がらないよう再通知しない。電池を交換するなどして閾値以上へ戻ったら、次に下回ったとき
 * もう一度通知する。
 *
 * 通知済みかどうかはデバイスごと（uuid）に保存し、アプリの再起動をまたいでも重複しないようにする。
 * uuidと真偽だけで機密情報を含まないため非暗号化のストアでよい（[SesameAlertSettingsStore]と同じファイル）。
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class LowBatteryAlertTracker(private val store: SesameKeyValueStore) {
    /**
     * [uuid]のデバイスの電池残量[percentage]を受け取り、いま通知すべきならtrueを返す。
     * 残量が分からない（null）場合は記録を変えずにfalseを返す。
     */
    @Synchronized
    fun shouldNotify(
        uuid: String,
        percentage: Int?,
    ): Boolean {
        if (percentage == null) return false
        val key = KEY_PREFIX + uuid
        val alreadyNotified = store.getString(key) == NOTIFIED
        return if (percentage < THRESHOLD_PERCENTAGE) {
            if (!alreadyNotified) store.putString(key, NOTIFIED)
            !alreadyNotified
        } else {
            if (alreadyNotified) store.putString(key, CLEARED)
            false
        }
    }

    companion object {
        /**
         * これ未満で「電池残量が少ない」とみなす（%）。ウィジェット・ウォッチの電池表示の警告（BL-257）と
         * 基準を揃えるため、coreの定数を参照する。
         */
        const val THRESHOLD_PERCENTAGE = SesameTileContent.LOW_BATTERY_THRESHOLD_PERCENTAGE

        private const val KEY_PREFIX = "low_battery_notified:"
        private const val NOTIFIED = "true"
        private const val CLEARED = "false"
    }
}

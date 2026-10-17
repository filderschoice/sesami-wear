package com.sesamiwear.mobile.alert

import com.sesamiwear.core.SesameKeyValueStore

/**
 * 「お知らせ」の設定（BL-238 / BL-241）。上部バーの設定メニューの「お知らせ」から変える。
 *
 * - 電池残量が少なくなったときの通知（BL-238）: 既定はオン。状態取得・操作の結果に含まれる電池残量だけで
 *   判定し、追加の通信をしないため、既定で有効にしても利用者の負担（APIの消費）が増えない。
 * - 解錠したままのときの通知（BL-241）: 既定はオフ。確かめるたびに状態を1回取得し、Bluetoothで届かなければ
 *   Sesame Web APIの月間リクエストを1回消費するため、利用者がオンにした場合だけ動かす
 *   （2026-10-15のユーザー判断。待ち時間は[UNLOCK_REMINDER_CHOICES]から選ぶ）。
 *
 * 全デバイス共通の値だけを持ち、機密情報を含まないため非暗号化のストアへ保存する。
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class SesameAlertSettingsStore(private val store: SesameKeyValueStore) {
    /** 電池残量が少なくなったときに通知するか。未設定なら既定（オン）。 */
    fun isLowBatteryEnabled(): Boolean = store.getString(KEY_LOW_BATTERY) != DISABLED

    fun setLowBatteryEnabled(enabled: Boolean) {
        store.putString(KEY_LOW_BATTERY, if (enabled) ENABLED else DISABLED)
    }

    /**
     * 解錠してから確かめるまでの分数。0ならオフ。未設定・選択肢に無い値（古い版の値や破損）はオフとして扱う
     * （APIを消費する機能のため、分からないときは動かさない側へ倒す）。
     */
    fun unlockReminderMinutes(): Int =
        store.getString(KEY_UNLOCK_REMINDER_MINUTES)?.toIntOrNull()?.takeIf { it in UNLOCK_REMINDER_CHOICES } ?: OFF

    /** [minutes]が選択肢に無い場合は保存しない。 */
    fun setUnlockReminderMinutes(minutes: Int) {
        if (minutes in UNLOCK_REMINDER_CHOICES) store.putString(KEY_UNLOCK_REMINDER_MINUTES, minutes.toString())
    }

    companion object {
        /** 解錠したままの通知の選択肢（分）。先頭の0はオフ。 */
        val UNLOCK_REMINDER_CHOICES = listOf(0, 5, 10, 30)

        /** 解錠したままの通知がオフであることを表す分数。 */
        const val OFF = 0

        private const val KEY_LOW_BATTERY = "low_battery_alert"
        private const val KEY_UNLOCK_REMINDER_MINUTES = "unlock_reminder_minutes"
        private const val ENABLED = "true"
        private const val DISABLED = "false"
    }
}

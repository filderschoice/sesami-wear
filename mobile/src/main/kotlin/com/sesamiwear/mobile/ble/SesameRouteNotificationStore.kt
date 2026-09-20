package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameKeyValueStore

/**
 * 経路が切り替わったときに通知を出すかどうかの設定（BL-190）。
 *
 * 既定はオン。切り替わりに気づけないと「Bluetoothにしたのに回数が減らない」という状況の
 * 原因を切り分けられないためで、BL-168でトーストを入れた理由と同じである。
 * 一方で通知は煩わしくもあるため、上部バーの設定メニューからオフにできる
 * （2026-09-20のユーザー判断）。
 *
 * 全デバイス共通の値を1つ持つだけで機密情報を含まないため、BLEの到達実績
 * （[SesameBleReachability]）と同じ非暗号化のファイルを別のキーで共有する。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class SesameRouteNotificationStore(private val store: SesameKeyValueStore) {
    /** 通知を出す設定か。未設定なら既定（オン）。 */
    fun isEnabled(): Boolean = store.getString(KEY_ENABLED) != DISABLED

    /** 設定を保存する。 */
    fun setEnabled(enabled: Boolean) {
        store.putString(KEY_ENABLED, if (enabled) ENABLED else DISABLED)
    }

    private companion object {
        const val KEY_ENABLED = "route_change_notification"
        const val ENABLED = "true"
        const val DISABLED = "false"
    }
}

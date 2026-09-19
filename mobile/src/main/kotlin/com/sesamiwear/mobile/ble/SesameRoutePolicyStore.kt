package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.SesameRoutePolicy

/**
 * 経路の方針（[SesameRoutePolicy]）の保存先（BL-167）。
 *
 * 全デバイス共通の値を1つ持つだけ。機密情報を含まないため非暗号化のSharedPreferencesでよく、
 * BLEの到達実績（[SesameBleReachability]）と同じファイルを別のキーで共有する。
 * 未設定・未知の値は[SesameRoutePolicy.DEFAULT]（自動）として扱う。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class SesameRoutePolicyStore(private val store: SesameKeyValueStore) {
    /** 現在の方針。未設定なら[SesameRoutePolicy.DEFAULT]。 */
    fun load(): SesameRoutePolicy = SesameRoutePolicy.ofNameOrDefault(store.getString(KEY_ROUTE_POLICY))

    /** 方針を保存する。 */
    fun save(policy: SesameRoutePolicy) {
        store.putString(KEY_ROUTE_POLICY, policy.name)
    }

    private companion object {
        const val KEY_ROUTE_POLICY = "route_policy"
    }
}

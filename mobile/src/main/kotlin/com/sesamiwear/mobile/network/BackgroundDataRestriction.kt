package com.sesamiwear.mobile.network

import android.content.Context
import android.net.ConnectivityManager

/**
 * 端末の「バックグラウンドデータの制限」が本アプリに掛かっているかを調べる（BL-192）。
 *
 * 2026-09-21の実機検証（Pixel 8 Pro）で、`cmd netpolicy list restrict-background-blacklist`へ
 * 本アプリのUIDが入っており、モバイル回線が既定の経路のときだけウィジェットからの操作が
 * 約0.4秒で名前解決の失敗になることを確認した。アプリを前面にしている間とWi-Fi（非従量制）では
 * 成功するため、利用者からは「モバイル回線のときだけ、たまに通信エラーになる」としか見えない。
 *
 * [ConnectivityManager.getRestrictBackgroundStatus]は、データセーバーと、アプリごとの
 * バックグラウンドデータの制限（`POLICY_REJECT_METERED_BACKGROUND`）の**どちらも**
 * `RESTRICT_BACKGROUND_STATUS_ENABLED`として返す。非従量制の回線では`DISABLED`になるため、
 * 「従量制の回線で、かつバックグラウンド通信が止められている」ことをこの1つの値で判定できる。
 * 前面にいるかどうかはこの値に含まれないが、前面ならそもそも通信が成功して失敗の分類自体を
 * 通らないため、追加の判定は要らない。
 *
 * ウィジェットのタップのたびに呼ばれるため、[ConnectivityManager]の取得は都度行い状態を持たない。
 * Android依存のためユニットテスト対象外（分類そのものは`core.SesameStatusFailure.of`でテストする）。
 */
object BackgroundDataRestriction {
    /** 本アプリのバックグラウンド通信が、従量制の回線でOSに止められていればtrue。 */
    fun isRestricted(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }
}

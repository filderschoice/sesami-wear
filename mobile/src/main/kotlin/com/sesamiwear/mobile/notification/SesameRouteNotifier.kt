package com.sesamiwear.mobile.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.mobile.ble.SesameRouteNotificationStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import com.sesamiwear.mobile.ui.SesameRouteIcon

/**
 * 経路が切り替わったことを通知で知らせる（BL-190）。
 *
 * もとはトースト（BL-168）だったが、2026-09-20の実機検証で
 * **背景から出したトーストは、通知が無効な端末ではシステムに抑止される**ことが判明した
 * （`NotificationService: Suppressing toast from package ... by user request.`）。
 * ウィジェットのタップもウォッチからの操作も背景のコンポーネントで動くため、
 * 設計意図の場面では一度も出ていなかった。前景（アプリを開いている間）では出る。
 *
 * そこで通知へ置き換える。通知には`POST_NOTIFICATIONS`（Android 13以上）が要るため、
 * BL-154のデータセーフティ申告・権限の用途説明もあわせて更新する必要がある。
 * 通知そのものを煩わしく感じる利用者のために、上部バーの設定メニューからオフにできる
 * （[SesameRouteNotificationStore]）。
 *
 * トーストは前景でのみ機能する補助として残す（呼び出し側で併用する）。
 * Android依存のためユニットテスト対象外（文言は`SesameRouteLabelTest`で検証済み）。
 */
class SesameRouteNotifier(private val context: Context) {
    private val settings =
        SesameRouteNotificationStore(SharedPreferencesKeyValueStore.forBleReachability(context))

    /**
     * [deviceName]のデバイスの経路が[route]へ変わったことを知らせる。
     *
     * 設定がオフ・権限が無い・通知が無効のいずれかなら何もしない（失敗として扱わない）。
     * デバイスごとに通知を分けると、複数台の一括操作で通知が積み上がるため、
     * **1つの通知を上書きする**（同じID）。
     *
     * 権限は[canNotify]で確かめてから通知する。Lintはその経由を追えないため`MissingPermission`を
     * 明示的に抑止する（`SesameBleScanner`が権限確認済みのBLE呼び出しへ付けているのと同じ扱い）。
     */
    @SuppressLint("MissingPermission")
    fun notifyRouteChange(
        deviceName: String,
        route: SesameStatusRoute,
    ) {
        if (!settings.isEnabled() || !canNotify()) return
        ensureChannel()
        val summary = "$deviceName：${SesameRouteLabel.routeChangeMessage(route)}"
        // 折りたたみ時は1行しか読めないため、対処（BL-192）は展開時の本文にだけ足す。
        val detail =
            if (route == SesameStatusRoute.WEB_API) {
                summary + "\n" + SesameRouteLabel.OFFICIAL_APP_HINT
            } else {
                summary
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(SesameRouteIcon.drawableResOrNull(route) ?: return)
                .setContentTitle(SesameRouteLabel.ROUTE_CHANGE_TITLE)
                .setContentText(summary)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(detail),
                )
                // 操作のたびに音が鳴ると煩わしい。表示だけで足りる知らせのため既定より低くする。
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** 通知を出せる状態か。権限（Android 13以上）と、利用者による通知の有効・無効の両方を見る。 */
    private fun canNotify(): Boolean {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 通知チャネル。作成済みなら何も起きない。 */
    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESCRIPTION
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "route_change"
        const val CHANNEL_NAME = "操作の経路の変化"
        const val CHANNEL_DESCRIPTION = "Bluetoothでの直接操作とインターネット経由が切り替わったときに知らせます"

        /** 1つの通知を上書きするための固定ID。 */
        const val NOTIFICATION_ID = 1001
    }
}

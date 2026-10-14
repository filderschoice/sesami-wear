package com.sesamiwear.mobile.alert

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sesamiwear.mobile.MainActivity
import com.sesamiwear.mobile.R

/**
 * 「お知らせ」の通知を出す（BL-238 / BL-241）。
 *
 * 経路の変化の通知（`SesameRouteNotifier`、BL-190）とはチャネルを分ける。あちらは操作のたびに出うる
 * 補助的な知らせで重要度を下げているが、こちらは電池切れ・閉め忘れという**利用者が行動すべき知らせ**の
 * ため、既定の重要度（音・バナーあり）にする。チャネルを分けておけば、利用者は端末の設定でそれぞれを
 * 個別に止められる。
 *
 * 通知はデバイスごとに分け（tagにuuidを使う）、同じデバイスの同じ種類は上書きする。
 * タップするとアプリの主画面を開く。設定がオフ・権限が無い・通知が無効の場合は何もしない。
 * Android依存のためユニットテスト対象外（判定は[LowBatteryAlertTracker]でテスト済み）。
 */
class SesameAlertNotifier(private val context: Context) {
    /** [deviceName]の電池残量が[percentage]%まで下がったことを知らせる。 */
    fun notifyLowBattery(
        uuid: String,
        deviceName: String,
        percentage: Int,
    ) {
        notify(
            tag = uuid,
            id = LOW_BATTERY_NOTIFICATION_ID,
            title = "$deviceName：電池残量が少なくなっています",
            text = "残り$percentage%です。早めに電池を交換してください。",
        )
    }

    /** [deviceName]を解錠したまま時間がたったことを知らせる（BL-241）。本文は`UnlockReminderDecision`が決める。 */
    fun notifyStillUnlocked(
        uuid: String,
        deviceName: String,
        message: String,
    ) {
        notify(
            tag = uuid,
            id = UNLOCK_REMINDER_NOTIFICATION_ID,
            title = UnlockReminderDecision.title(deviceName),
            text = message,
        )
    }

    @SuppressLint("MissingPermission") // 権限は[canNotify]で確かめている（SesameRouteNotifierと同じ扱い）。
    private fun notify(
        tag: String,
        id: Int,
        title: String,
        text: String,
    ) {
        if (!canNotify()) return
        ensureChannel()
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** 通知を出せる状態か。権限（Android 13以上）と、利用者による通知の有効・無効の両方を見る。 */
    private fun canNotify(): Boolean {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESCRIPTION
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "sesame_alerts"
        const val CHANNEL_NAME = "お知らせ（電池残量・閉め忘れ）"
        const val CHANNEL_DESCRIPTION = "電池残量が少なくなったときや、解錠したままのときに知らせます"
        const val LOW_BATTERY_NOTIFICATION_ID = 2001
        const val UNLOCK_REMINDER_NOTIFICATION_ID = 2002
    }
}

package com.sesamiwear.mobile.alert

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sesamiwear.mobile.EntryPointGuard
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorFactory
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import java.util.concurrent.TimeUnit

/**
 * 解錠したままの通知（BL-241）の確認を1回だけ行うWorker。
 *
 * 予約から実行までの間に設定をオフにしていたら何もしない。確かめるための状態取得は通常の「更新」と同じ
 * 実行口で行い（BLE圏内ならBLE、それ以外はWeb APIを1回消費する）、結果をウィジェット・ウォッチへも反映する。
 * 通知の要否は[UnlockReminderDecision]が決める。
 *
 * WorkManagerは端末の省電力（Doze）で実行を遅らせることがあるため、確認は「おおよそN分後」になる
 * （利用者向けの説明にも書く）。正確な時刻のアラームは追加の権限（`SCHEDULE_EXACT_ALARM`）が要るため使わない。
 * Android依存の薄いアダプタのためユニットテスト対象外（予約と判定はそれぞれテスト済み）。
 */
class UnlockReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uuid = inputData.getString(KEY_UUID)
        val settings = SesameAlertSettingsStore(SharedPreferencesKeyValueStore.forAlerts(applicationContext))
        if (uuid != null && settings.unlockReminderMinutes() != SesameAlertSettingsStore.OFF) {
            check(uuid, inputData.getInt(KEY_MINUTES, 0))
        }
        // 失敗しても再試行しない（再試行のたびにAPIを消費するため）。
        return Result.success()
    }

    private suspend fun check(
        uuid: String,
        minutes: Int,
    ) {
        EntryPointGuard.run(onFailure = { Log.w(TAG, "unlock reminder failed: $it") }) {
            val fetched = SesameDeviceCommandExecutorFactory.create(applicationContext).refreshStatus(uuid)
            val lastKnown = SesameDeviceStores.lockState(applicationContext).load(uuid)?.isLocked
            UnlockReminderDecision.messageOrNull(fetched, lastKnown, minutes)?.let { message ->
                SesameAlertNotifier(applicationContext).notifyStillUnlocked(
                    uuid = uuid,
                    deviceName = SesameAlerts.displayNameOf(applicationContext, uuid),
                    message = message,
                )
            }
        }
    }

    /** WorkManagerへの予約（[UnlockReminderScheduler]の実装）。デバイスごとに1件で、新しい予約が古い予約を置き換える。 */
    class Scheduler(context: Context) : UnlockReminderScheduler {
        private val workManager = WorkManager.getInstance(context.applicationContext)

        override fun schedule(
            uuid: String,
            delayMinutes: Int,
        ) {
            val request =
                OneTimeWorkRequestBuilder<UnlockReminderWorker>()
                    .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                    .setInputData(Data.Builder().putString(KEY_UUID, uuid).putInt(KEY_MINUTES, delayMinutes).build())
                    .build()
            workManager.enqueueUniqueWork(workName(uuid), ExistingWorkPolicy.REPLACE, request)
        }

        override fun cancel(uuid: String) {
            workManager.cancelUniqueWork(workName(uuid))
        }

        private fun workName(uuid: String) = "unlock_reminder:$uuid"
    }

    private companion object {
        const val TAG = "SesameUnlockReminder"
        const val KEY_UUID = "uuid"
        const val KEY_MINUTES = "minutes"
    }
}

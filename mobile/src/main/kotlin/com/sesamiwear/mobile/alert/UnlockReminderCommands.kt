package com.sesamiwear.mobile.alert

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor
import com.sesamiwear.mobile.command.SesameDeviceCommands

/**
 * 解錠したままの通知（BL-241）の予約を、施錠/解錠の実行口へ差し込む。
 *
 * [delegate]へ処理を委ね、結果が成功のときだけ次を行う。
 * - 解錠に成功した: 設定の分数（[reminderMinutes]）後に1回だけ確かめるよう予約する。オフ（0）なら予約しない。
 * - 施錠に成功した: そのデバイスの予約を取り消す（確かめるための状態取得＝APIの消費をしない）。
 *
 * **状態取得（[refreshStatus]）では予約しない。** 確かめた結果が解錠中だったときに再び予約すると、
 * 解錠のままの間ずっとAPIを消費し続けるため（通知は解錠1回につき1回まで）。
 * 失敗・重複として無視した操作でも予約は変えない。デモ用デバイスは実物が無いため対象外。
 *
 * ウォッチ経由・ウィジェット経由・パネル・ショートカットはすべて`SesameDeviceCommandExecutorFactory`の
 * 実行口を通るため、ここで包めば経路を問わず同じに動く。Android非依存のためユニットテスト対象。
 */
class UnlockReminderCommands(
    private val delegate: SesameDeviceCommands,
    private val scheduler: UnlockReminderScheduler,
    private val reminderMinutes: () -> Int,
) : SesameDeviceCommands {
    override suspend fun execute(
        uuid: String,
        command: SesameCommand,
    ): SesameDeviceCommandExecutor.Outcome {
        val outcome = delegate.execute(uuid, command)
        if (outcome == SesameDeviceCommandExecutor.Outcome.SUCCESS && !SesameDemoMode.isDemoDevice(uuid)) {
            when (command) {
                SesameCommand.UNLOCK -> {
                    val minutes = reminderMinutes()
                    if (minutes > SesameAlertSettingsStore.OFF) {
                        scheduler.schedule(
                            uuid,
                            minutes,
                        )
                    } else {
                        scheduler.cancel(uuid)
                    }
                }
                SesameCommand.LOCK -> scheduler.cancel(uuid)
            }
        }
        return outcome
    }

    override suspend fun refreshStatus(uuid: String): Boolean? = delegate.refreshStatus(uuid)
}

/** 解錠したままの確認の予約先（実装はWorkManager、`WorkManagerUnlockReminderScheduler`）。 */
interface UnlockReminderScheduler {
    /** [uuid]のデバイスを[delayMinutes]分後に1回確かめる。同じデバイスの既存の予約は置き換える。 */
    fun schedule(
        uuid: String,
        delayMinutes: Int,
    )

    /** [uuid]のデバイスの予約を取り消す。予約が無ければ何もしない。 */
    fun cancel(uuid: String)
}

package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.LockStateNotifier
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome
import com.sesamiwear.mobile.command.SesameDeviceCommands
import com.sesamiwear.mobile.state.LockStateStore

/**
 * 撮影モード（BL-212）の実行口。**Sesame Web APIもBLEも呼ばず**、撮影用の状態を書き換えるだけにする。
 *
 * ウォッチのTile・ホーム画面ウィジェットから施錠/解錠すると、撮影用の状態が実物と同じように切り替わり、
 * ウォッチ（DataItem）とウィジェットへ通知される。撮影用のデバイス以外（実物のuuid）への操作は
 * 何もせず失敗を返す（撮影モード中の実物への操作の抑止）。
 *
 * 撮影用の状態に直近の失敗が設定されている場合は、失敗の表示を撮れるよう、操作も失敗させて状態を変えない。
 * 状態取得（「更新」）は、保存済みの施錠状態の取得時刻を今へ進めるだけにする（「たった今」を撮るため）。
 */
class ShowcaseDeviceCommands(
    private val loadDevices: () -> List<SesameCredentials>,
    private val lockStateStore: LockStateStore,
    private val notifier: LockStateNotifier,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SesameDeviceCommands {
    override suspend fun execute(
        uuid: String,
        command: SesameCommand,
    ): Outcome {
        if (!isShowcaseDevice(uuid) || lockStateStore.load(uuid)?.lastFailure != null) return Outcome.FAILURE
        lockStateStore.save(uuid, isLocked = command == SesameCommand.LOCK, updatedAtEpochMillis = nowMillis())
        notify(uuid)
        return Outcome.SUCCESS
    }

    override suspend fun refreshStatus(uuid: String): Boolean? {
        val snapshot = if (isShowcaseDevice(uuid)) lockStateStore.load(uuid) else null
        val isLocked = snapshot?.isLocked?.takeIf { snapshot.lastFailure == null } ?: return null
        lockStateStore.save(uuid, isLocked = isLocked, updatedAtEpochMillis = nowMillis())
        notify(uuid)
        return isLocked
    }

    private fun isShowcaseDevice(uuid: String): Boolean = loadDevices().any { it.uuid == uuid }

    private suspend fun notify(uuid: String) {
        val snapshot = lockStateStore.load(uuid) ?: SesameStatusSnapshot(isLocked = null, updatedAtEpochMillis = null)
        notifier.watch.onStatusChanged(uuid, snapshot)
        notifier.local.onStatusChanged(uuid, snapshot)
    }
}

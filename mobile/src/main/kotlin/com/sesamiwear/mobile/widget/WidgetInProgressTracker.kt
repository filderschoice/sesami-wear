package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.display.SesameDeviceTargets

/**
 * ウィジェットから実行中のコマンドの対象uuidを、プロセス内のメモリだけで保持する（BL-122）。
 * 通信中（IN_PROGRESS）の表示に使う。永続化しないのは、プロセスが終了した時点で実行も終わっており、
 * 「通信中」のまま表示が固まるのを避けるため。Android非依存のためユニットテスト対象。
 */
class WidgetInProgressTracker {
    private val inProgressUuids = mutableMapOf<String, Int>()

    /** [uuids]を実行中として登録する。同じuuidが重ねて登録された場合は、同じ回数の[finish]で解除される。 */
    @Synchronized
    fun start(uuids: Collection<String>) {
        uuids.forEach { inProgressUuids[it] = (inProgressUuids[it] ?: 0) + 1 }
    }

    @Synchronized
    fun finish(uuids: Collection<String>) {
        uuids.forEach { uuid ->
            val count = (inProgressUuids[uuid] ?: 0) - 1
            if (count > 0) inProgressUuids[uuid] = count else inProgressUuids.remove(uuid)
        }
    }

    /**
     * [assignedUuid]を割り当てたウィジェットを通信中として表示するか。
     * 全デバイスの場合は、登録済みのいずれか1台でも実行中なら通信中とする（一部だけ古い状態で集約しないため）。
     */
    @Synchronized
    fun isInProgress(
        assignedUuid: String?,
        registeredDevices: List<SesameDeviceSummary>,
    ): Boolean =
        when {
            assignedUuid == null -> false
            assignedUuid in inProgressUuids -> true
            SesameDeviceTargets.isAllDevices(assignedUuid) -> registeredDevices.any { it.uuid in inProgressUuids }
            else -> false
        }

    companion object {
        /** プロセス内で共有するインスタンス（受信側と描画側で同じものを参照する）。 */
        val shared = WidgetInProgressTracker()
    }
}

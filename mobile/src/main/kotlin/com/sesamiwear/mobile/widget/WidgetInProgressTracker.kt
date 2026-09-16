package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.display.SesameDeviceTargets

/**
 * ウィジェットから実行中のコマンドの対象uuidを、プロセス内のメモリだけで保持する（BL-122）。
 * 通信中（IN_PROGRESS）の表示に使う。永続化しないのは、プロセスが終了した時点で実行も終わっており、
 * 「通信中」のまま表示が固まるのを避けるため。Android非依存のためユニットテスト対象。
 *
 * 登録は開始時刻つきで保持し、[IN_PROGRESS_TIMEOUT_MILLIS]を過ぎたものは[finish]が呼ばれていなくても
 * 実行中とみなさない（BL-135）。実行が途中で打ち切られて解除が行われないと、ウィジェットは自発的な
 * 再描画契機を持たない（`updatePeriodMillis=0`）ため、右側のタップも効かない「通信中...」の表示が
 * 残り続けてしまう。その状態から必ず抜けられるようにするための安全弁で、上限はSesame APIの
 * 呼び出し全体のタイムアウト（20秒、BL-133）より長くとり、正常に終わる操作を誤って打ち切らない値にしている。
 */
class WidgetInProgressTracker(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** uuidごとの実行開始時刻。同じuuidへ重ねて実行された場合は、開始した回数だけ並ぶ。 */
    private val startedAtMillis = mutableMapOf<String, MutableList<Long>>()

    /** [uuids]を実行中として登録する。同じuuidが重ねて登録された場合は、同じ回数の[finish]で解除される。 */
    @Synchronized
    fun start(uuids: Collection<String>) {
        val startedAt = nowMillis()
        uuids.forEach { startedAtMillis.getOrPut(it) { mutableListOf() }.add(startedAt) }
    }

    @Synchronized
    fun finish(uuids: Collection<String>) {
        uuids.forEach { uuid ->
            val startedAt = startedAtMillis[uuid] ?: return@forEach
            startedAt.removeAt(0)
            if (startedAt.isEmpty()) startedAtMillis.remove(uuid)
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
    ): Boolean {
        removeExpired()
        return when {
            assignedUuid == null -> false
            assignedUuid in startedAtMillis -> true
            SesameDeviceTargets.isAllDevices(assignedUuid) -> registeredDevices.any { it.uuid in startedAtMillis }
            else -> false
        }
    }

    /** 上限を過ぎた登録を捨てる（BL-135）。解除されないまま残った登録で表示が固まるのを防ぐ。 */
    private fun removeExpired() {
        val expiredAtOrBefore = nowMillis() - IN_PROGRESS_TIMEOUT_MILLIS
        val entries = startedAtMillis.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            entry.value.removeAll { it <= expiredAtOrBefore }
            if (entry.value.isEmpty()) entries.remove()
        }
    }

    companion object {
        /** これを過ぎた登録は、解除されていなくても実行中とみなさない（BL-135）。 */
        const val IN_PROGRESS_TIMEOUT_MILLIS = 30_000L

        /** プロセス内で共有するインスタンス（受信側と描画側で同じものを参照する）。 */
        val shared = WidgetInProgressTracker()
    }
}

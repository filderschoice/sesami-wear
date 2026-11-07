package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.mobile.ble.BleMultiProbeOperations
import com.sesamiwear.mobile.ble.BleProbeOperations
import com.sesamiwear.mobile.ble.checkSequentially
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 撮影モード中の「まとめて接続確認」（BL-280）の動き。**デバッグ版にだけ含まれる。** **Bluetoothを使わず**、
 * 台ごとに単体用の[ShowcaseBleProbe]（撮影用の状態を段階・距離へ対応させる）を動かして並べる。
 * 接続確認は実物と同じく1台ずつ順番に行う（[checkSequentially]）。
 *
 * @param members 台ごとの（uuid、表示名、単体の撮影用の動き）。並びが画面の並びになる。
 */
class ShowcaseBleMultiProbe(
    private val members: List<Member>,
) : BleMultiProbeOperations {
    /** 1台ぶんの撮影用の動き。 */
    data class Member(
        val uuid: String,
        val name: String,
        val probe: BleProbeOperations,
    )

    override val usesBluetooth: Boolean = false

    override fun observe(): Flow<SesameBleMultiProbeSnapshot> =
        channelFlow {
            val state = MutableStateFlow(initial())
            members.forEach { member ->
                launch {
                    member.probe.observe().collect { snapshot ->
                        state.update { it.withSnapshot(member.uuid, snapshot) }
                    }
                }
            }
            state.collect { send(it) }
        }

    override suspend fun checkConnections(
        current: SesameBleMultiProbeSnapshot,
        onProgress: (SesameBleMultiProbeSnapshot) -> Unit,
    ): SesameBleMultiProbeSnapshot {
        val byUuid = members.associateBy { it.uuid }
        return checkSequentially(current, { byUuid.getValue(it).probe }, onProgress)
    }

    private fun initial() =
        SesameBleMultiProbeSnapshot(
            members.map {
                SesameBleMultiProbeSnapshot.Entry(
                    it.uuid,
                    it.name,
                    SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING),
                )
            },
        )
}

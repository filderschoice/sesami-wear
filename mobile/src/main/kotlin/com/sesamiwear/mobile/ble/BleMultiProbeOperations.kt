package com.sesamiwear.mobile.ble

import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * 「まとめて接続確認」（BL-280）が呼ぶ操作。登録した全デバイスを対象にする。実物は[SesameBleMultiProber]
 * （1回のスキャンで複数台を追う）。デバッグ版の撮影モード中は、Bluetoothを使わず撮影用の状態で動く実装に
 * 差し替える（`ShowcaseMode.multiProbeOperationsOrNull`）。スマホの画面とウォッチからの要求の両方が使う。
 */
interface BleMultiProbeOperations {
    /** Bluetoothの権限を要る実装か。撮影用の実装はfalse（権限が無くても動かす）。 */
    val usesBluetooth: Boolean

    /** 全台の電波を追い、台ごとの探索中 / 発見 / 圏外などの進捗を流す。収集をやめると止まる。 */
    fun observe(): Flow<SesameBleMultiProbeSnapshot>

    /**
     * 全台の接続・ログインを**1台ずつ順番に**試し、結果を返す（Sesameは同時に1接続しか持てないため）。
     * 途中の段階は[onProgress]へ流す。呼ぶ前に[observe]の収集を止めておくこと。
     *
     * @param current 直前の進捗。各台の最後のRSSIを結果の表示へ引き継ぐために使う。
     */
    suspend fun checkConnections(
        current: SesameBleMultiProbeSnapshot,
        onProgress: (SesameBleMultiProbeSnapshot) -> Unit,
    ): SesameBleMultiProbeSnapshot
}

/**
 * [current]の全台を登録順に1台ずつ[BleProbeOperations.checkConnection]で確かめる。各台の途中の段階と結果を
 * 進捗へ反映して[onProgress]へ流し、最後の進捗を返す。1台の失敗で止めず、残りの台も確かめる。
 * 実物と撮影用の両方がこの順序を共有する。
 */
internal suspend fun checkSequentially(
    current: SesameBleMultiProbeSnapshot,
    probeOf: (uuid: String) -> BleProbeOperations,
    onProgress: (SesameBleMultiProbeSnapshot) -> Unit,
): SesameBleMultiProbeSnapshot {
    var state = current
    for (entry in current.entries) {
        val result =
            probeOf(entry.uuid).checkConnection(entry.snapshot.rssiDbm) { progress ->
                state = state.withSnapshot(entry.uuid, progress)
                onProgress(state)
            }
        state = state.withSnapshot(entry.uuid, result)
        onProgress(state)
    }
    return state
}

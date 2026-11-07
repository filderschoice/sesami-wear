package com.sesamiwear.mobile.ble

import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * 「Bluetooth接続を確認」（BL-272 / BL-273）が呼ぶ操作。実物は[SesameBleProber]（BLEのスキャンと接続）。
 *
 * デバッグ版の撮影モード中は、Bluetoothを使わず撮影用の状態で動く実装に差し替える
 * （`ShowcaseMode.probeOperationsOrNull`）。Sesame実機が無くても、画面の表示と掲載画像の撮影ができるようにするため。
 * スマホの画面とウォッチからの要求（[com.sesamiwear.mobile.messaging.SesameBleProbeRelay]）の両方が使う。
 */
interface BleProbeOperations {
    /** Bluetoothの権限を要る実装か。撮影用の実装はfalse（権限が無くても動かす）。 */
    val usesBluetooth: Boolean

    /** 対象の電波を追い、探索中 / 発見 / 圏外などの進捗を流す。収集をやめると止まる。 */
    fun observe(): Flow<SesameBleProbeSnapshot>

    /**
     * 接続・ログインまで試し、終了段階を返す。途中の段階は[onProgress]へ流す。
     * 呼ぶ前に[observe]の収集を止めておくこと。
     */
    suspend fun checkConnection(
        lastRssiDbm: Int?,
        onProgress: (SesameBleProbeSnapshot) -> Unit,
    ): SesameBleProbeSnapshot
}

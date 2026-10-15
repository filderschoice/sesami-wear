package com.sesamiwear.mobile.dial

import com.sesamiwear.core.api.SesameCommand

/**
 * ダイヤル操作画面（BL-243）が呼ぶ操作。実物は[DialController]（Bluetoothだけで操作する）。
 *
 * デバッグ版の撮影モード中は、Bluetoothを使わず撮影用の状態で動く実装に差し替える
 * （BL-246、`ShowcaseMode.dialOperationsOrNull`）。Sesame実機が無くても画面の表示と操作の流れを確認できるようにするため。
 */
interface DialOperations {
    /** 接続して現在の角度を読む。[lastResult]は直前の操作の結果の1行（画面へそのまま出す）。 */
    suspend fun load(lastResult: String? = null): DialUiState

    /** [command]を送り、送ったあとの状態を返す。 */
    suspend fun run(command: SesameCommand): DialUiState
}

package com.sesamiwear.mobile.dial

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleClient

/**
 * ダイヤル操作画面（BL-243）の状態と、画面へ出す文言。Android非依存のためユニットテスト対象。
 *
 * この画面は**Bluetoothで届くときだけ**操作でき、届かないときはWeb APIへ倒さずにその旨を出す
 * （2026-10-15のユーザー指示）。施錠・解錠が効かないときなどに、手元で確実に回すための手段のため。
 */
sealed interface DialUiState {
    /** 接続して状態を読んでいる。 */
    data object Connecting : DialUiState

    /** 操作できる。[position]は角度、[isLocked]は施錠範囲にあるか（施錠・解錠範囲のどちらでもなければnull）。 */
    data class Ready(
        val position: Int,
        val isLocked: Boolean?,
        val batteryPercentage: Int?,
        /** 直前の操作の結果。初回はnull。 */
        val lastResult: String? = null,
    ) : DialUiState {
        val command: SesameCommand? get() = DialSweep.commandFor(isLocked)

        /** ダイヤルの中央に出す案内。 */
        val instruction: String
            get() =
                when (command) {
                    SesameCommand.UNLOCK -> "ぐるっと半周なぞると解錠"
                    SesameCommand.LOCK -> "ぐるっと半周なぞると施錠"
                    null -> "施錠・解錠のどちらの位置でもありません"
                }

        val stateLabel: String
            get() =
                when (isLocked) {
                    true -> "施錠中"
                    false -> "解錠中"
                    null -> "途中の位置"
                }
    }

    /** 実行中。 */
    data class Running(val command: SesameCommand) : DialUiState {
        val label: String get() = if (command == SesameCommand.LOCK) "施錠しています..." else "解錠しています..."
    }

    /** Bluetoothで届かない・使えない。[message]に理由と対処を出す。 */
    data class Unavailable(val message: String) : DialUiState

    companion object {
        /** Bluetoothでの接続・状態取得に失敗したときの文言。 */
        fun unavailableMessage(result: SesameBleClient.Result): String =
            when (result) {
                SesameBleClient.Result.PERMISSION_DENIED ->
                    "Bluetoothの許可がありません。右上「⋮」→「Bluetoothで直接操作」から許可してください。"
                SesameBleClient.Result.LOGIN_FAILED ->
                    "Sesameに接続できましたが、ログインできませんでした。secretKeyの入力内容を確認してください。"
                else ->
                    "Bluetoothで届きません。Sesameの近くで、Bluetoothがオンになっているか確認してから" +
                        "「もう一度」をタップしてください。この画面はインターネット経由では操作しません。"
            }

        /** 操作の結果の1行。 */
        fun resultMessage(
            command: SesameCommand,
            succeeded: Boolean,
        ): String {
            val name = if (command == SesameCommand.LOCK) "施錠" else "解錠"
            return if (succeeded) "${name}しました（Bluetooth）" else "${name}できませんでした。近づいてもう一度お試しください"
        }
    }
}

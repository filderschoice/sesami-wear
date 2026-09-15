package com.sesamiwear.mobile.widget

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.api.SesameCommandConfirmation
import com.sesamiwear.core.display.SesameTileActions

/**
 * ウィジェット右側（状態表示）をタップしたときの動作（BL-122）。操作ルールはwearのTileと同じで、
 * 提示コマンドは`SesameTileActions`（施錠中→解錠、解錠中→施錠、一部解錠→全施錠、通信中・状態不明→なし）、
 * 確認画面の要否は`SesameCommandConfirmation`（解錠のみ確認）で決める。
 */
sealed interface WidgetTapAction {
    /** 対象デバイスが未設定のため、選択画面を開く。 */
    data object OpenConfiguration : WidgetTapAction

    /** 確認なしですぐに[command]を実行する（施錠）。 */
    data class Run(val command: SesameCommand) : WidgetTapAction

    /** 確認画面を挟んでから[command]を実行する（解錠）。 */
    data class Confirm(val command: SesameCommand) : WidgetTapAction

    /** 操作できない状態（通信中・状態不明）。タップしても何もしない。 */
    data object None : WidgetTapAction

    companion object {
        fun forModel(model: SesameWidgetModel): WidgetTapAction =
            when (model) {
                SesameWidgetModel.Unconfigured -> OpenConfiguration
                is SesameWidgetModel.Configured -> {
                    val command = SesameTileActions.commandForState(model.state)
                    when {
                        command == null -> None
                        SesameCommandConfirmation.isRequired(command) -> Confirm(command)
                        else -> Run(command)
                    }
                }
            }

        /** 確認画面の結果から実行するコマンドを返す。キャンセルした場合は何も実行しない（null）。 */
        fun commandAfterConfirmation(
            command: SesameCommand,
            confirmed: Boolean,
        ): SesameCommand? = command.takeIf { confirmed }
    }
}

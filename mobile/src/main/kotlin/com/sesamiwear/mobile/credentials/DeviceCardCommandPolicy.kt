package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor

/**
 * 主画面のデバイスカードの「施錠」「解錠」ボタンの規則（BL-295後半）。Android非依存のためユニットテスト対象。
 *
 * ボタンは表示している状態によらず押せる（すでに施錠中でも施錠を、解錠中でも解錠を送れる）。
 * 無効にするのは、送信中（二重送信を避ける）だけ。解錠は確認ダイアログを経てから送り、
 * 即解錠の経路は作らない。施錠は確認なしで送る（ウィジェット・タイルの施錠と同じ方針）。
 */
internal object DeviceCardCommandPolicy {
    /** ボタンを押したときの次の動き。 */
    sealed interface PressAction {
        data class Send(
            val command: SesameCommand,
        ) : PressAction

        data class AskConfirm(
            val command: SesameCommand,
        ) : PressAction

        data object Ignore : PressAction
    }

    /** 表示状態によらず、送信中でなければ押せる。 */
    fun isEnabled(isSending: Boolean): Boolean = !isSending

    /** 解錠だけ確認を挟む。 */
    fun requiresConfirmation(command: SesameCommand): Boolean = command == SesameCommand.UNLOCK

    /** ボタンの押下。送信中は何もしない。解錠は確認、施錠は送信。 */
    fun onPressed(
        command: SesameCommand,
        isSending: Boolean,
    ): PressAction =
        when {
            !isEnabled(isSending) -> PressAction.Ignore
            requiresConfirmation(command) -> PressAction.AskConfirm(command)
            else -> PressAction.Send(command)
        }

    fun buttonLabel(command: SesameCommand): String = if (command == SesameCommand.LOCK) LOCK_LABEL else UNLOCK_LABEL

    const val CONFIRM_TITLE = "解錠しますか？"

    fun confirmMessage(displayName: String): String = "${displayName}を解錠します。"

    /** 結果のメッセージ（スナックバー用）。失敗には「未反映」（送信後の状態確認で実際の状態が違った場合）を含む。 */
    fun resultMessage(
        displayName: String,
        command: SesameCommand,
        outcome: SesameDeviceCommandExecutor.Outcome,
    ): String {
        val verb = if (command == SesameCommand.LOCK) "施錠" else "解錠"
        return when (outcome) {
            SesameDeviceCommandExecutor.Outcome.SUCCESS -> "${displayName}を${verb}しました"
            SesameDeviceCommandExecutor.Outcome.FAILURE -> "${displayName}を${verb}できませんでした（理由はカードの最終取得の行）"
            SesameDeviceCommandExecutor.Outcome.DEBOUNCED -> "直前の操作から間がないため送りませんでした。少し待ってください"
        }
    }

    const val LOCK_LABEL = "施錠"
    const val UNLOCK_LABEL = "解錠"
}

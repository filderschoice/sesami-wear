package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor

/**
 * 主画面のデバイスカードの「鍵を操作」の規則（BL-295後半で施錠・解錠の2ボタンとして追加し、BL-303で
 * 1つのボタン＋選ぶ画面へまとめた）。Android非依存のためユニットテスト対象。
 *
 * ボタンは表示している状態によらず押せ、押すと［キャンセル｜施錠｜解錠］を選ぶ画面を開く（ウィジェットの
 * 選ぶ画面と同じ部品）。選ぶ画面で「施錠」「解錠」を押したときだけ送る（すでに施錠中でも施錠を、解錠中でも
 * 解錠を送れる）。解錠は選ぶ画面で「解錠」を押す明示操作を経るため、確認は重ねない（ウィジェットと同じ方針）。
 * 無効にするのは、送信中（二重送信を避ける）だけ。
 */
internal object DeviceCardCommandPolicy {
    /** 「鍵を操作」を押したときの次の動き。 */
    sealed interface PressAction {
        data object OpenChooser : PressAction

        data object Ignore : PressAction
    }

    /** 選ぶ画面で選んだときの次の動き。 */
    sealed interface ChooseAction {
        data class Send(
            val command: SesameCommand,
        ) : ChooseAction

        data object Ignore : ChooseAction
    }

    /** 表示状態によらず、送信中でなければ押せる。 */
    fun isEnabled(isSending: Boolean): Boolean = !isSending

    /** 「鍵を操作」の押下。送信中は何もしない。 */
    fun onPressed(isSending: Boolean): PressAction =
        if (isEnabled(isSending)) PressAction.OpenChooser else PressAction.Ignore

    /**
     * 選ぶ画面での選択。[command]がnull（キャンセル）なら送らない。選ぶ画面を開いている間に別の経路の
     * 送信が始まっていた場合も送らない（二重送信を避ける）。
     */
    fun onChosen(
        command: SesameCommand?,
        isSending: Boolean,
    ): ChooseAction = if (command == null || !isEnabled(isSending)) ChooseAction.Ignore else ChooseAction.Send(command)

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

    /** 施錠・解錠を選ぶ画面を開くボタンの文言（BL-303、2026-11-21のユーザー判断）。 */
    const val MENU_LABEL = "鍵を操作"
}

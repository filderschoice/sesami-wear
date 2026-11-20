package com.sesamiwear.wear.action

import com.sesamiwear.core.api.SesameCommand

/**
 * IntentのExtraに載せたコマンド名（[SesameCommand.name]）から[SesameCommand]を復元する。
 * Intent自体を受け取らず文字列のみを扱うため、Android非依存でユニットテストできる。
 *
 * BL-295: 施錠・解錠を選ぶ画面を開く依頼（[EXTRA_CHOOSE]）は[Request.Choose]、従来の
 * コマンド指定は[Request.Direct]として区別する。
 */
object SesameActionCommandParser {
    const val EXTRA_COMMAND = "com.sesamiwear.wear.action.EXTRA_COMMAND"
    const val EXTRA_DEVICE_UUID = "com.sesamiwear.wear.action.EXTRA_DEVICE_UUID"
    const val EXTRA_CHOOSE = "com.sesamiwear.wear.action.EXTRA_CHOOSE"

    /** 操作画面の起動依頼。 */
    sealed interface Request {
        /** 施錠・解錠を選ぶ画面を開く。 */
        data object Choose : Request

        /** [command]を指定した起動（施錠は即実行、解錠は選ぶ画面を経る）。 */
        data class Direct(
            val command: SesameCommand,
        ) : Request
    }

    /** [choose]が真なら選ぶ画面、そうでなければ[commandName]から復元する。どちらも無効ならnull。 */
    fun parse(
        commandName: String?,
        choose: Boolean,
    ): Request? =
        if (choose) {
            Request.Choose
        } else {
            fromExtra(commandName)?.let { Request.Direct(it) }
        }

    fun fromExtra(name: String?): SesameCommand? = name?.let { n -> SesameCommand.entries.find { it.name == n } }
}

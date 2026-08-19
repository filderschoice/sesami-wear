package com.sesamiwear.wear.action

import com.sesamiwear.core.api.SesameCommand

/**
 * IntentのExtraに載せたコマンド名（[SesameCommand.name]）から[SesameCommand]を復元する。
 * Intent自体を受け取らず文字列のみを扱うため、Android非依存でユニットテストできる。
 */
object SesameActionCommandParser {
    const val EXTRA_COMMAND = "com.sesamiwear.wear.action.EXTRA_COMMAND"

    fun fromExtra(name: String?): SesameCommand? = name?.let { n -> SesameCommand.entries.find { it.name == n } }
}

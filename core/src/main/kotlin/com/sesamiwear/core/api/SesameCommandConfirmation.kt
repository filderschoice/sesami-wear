package com.sesamiwear.core.api

/**
 * コマンド実行前に確認を要求するかどうかを判定する。
 * PLAN.mdのUX要件（施錠はワンタップ即実行、解錠は誤操作防止のためホールド確認 or 確認ダイアログ）に対応する。
 */
object SesameCommandConfirmation {
    fun isRequired(command: SesameCommand): Boolean = command == SesameCommand.UNLOCK
}

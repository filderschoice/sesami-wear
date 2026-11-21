package com.sesamiwear.wear.action

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.api.SesameCommandConfirmation

/**
 * [SesameActionActivity]の起動時にIntent extraから「何をするか」を決める、Android非依存の判定
 * （BL-295前半のレビュー指摘で、Activityから切り出してユニットテスト対象にした）。
 *
 * - 対象デバイスのuuidが無い・コマンドも選ぶ依頼も無効 → [Plan.Finish]（何もせず閉じる）
 * - 選ぶ依頼（[SesameActionCommandParser.EXTRA_CHOOSE]） → [Plan.Chooser]
 * - 確認が要るコマンド（解錠）の直接指定 → [Plan.Chooser]（Intentで直接指定されても、
 *   解錠は選ぶ画面で「解錠」を押す明示操作を経る）
 * - 確認が要らないコマンド（施錠）の直接指定 → [Plan.Execute]（ワンタップ即実行）
 */
object SesameActionLaunchPlanner {
    /** 起動時の動作。 */
    sealed interface Plan {
        /** 何もせず閉じる。 */
        data object Finish : Plan

        /** 施錠・解錠を選ぶ画面を出す。 */
        data object Chooser : Plan

        /** [command]をそのまま実行する。 */
        data class Execute(
            val command: SesameCommand,
        ) : Plan
    }

    fun plan(
        commandName: String?,
        choose: Boolean,
        deviceUuid: String?,
    ): Plan {
        val request = SesameActionCommandParser.parse(commandName, choose)
        return when {
            request == null || deviceUuid.isNullOrEmpty() -> Plan.Finish
            request is SesameActionCommandParser.Request.Direct &&
                !SesameCommandConfirmation.isRequired(request.command) -> Plan.Execute(request.command)
            else -> Plan.Chooser
        }
    }
}

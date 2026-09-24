package com.sesamiwear.mobile.command

import com.sesamiwear.core.api.SesameCommand

/**
 * 施錠/解錠・状態取得の実行口（BL-212）。ウォッチ（`SesameMessageListenerService`）と
 * ホーム画面ウィジェット（`WidgetCommandRunner`）はこの型越しに呼ぶ。
 *
 * 通常は[SesameDeviceCommandExecutor]（Sesame Web API・BLEで実物を操作する）が実装する。
 * デバッグ版の撮影モード中だけは、実物へ一切通信せずに撮影用の状態を書き換える実装へ
 * [SesameDeviceCommandExecutorFactory]が差し替える（`com.sesamiwear.mobile.showcase.ShowcaseMode`）。
 */
interface SesameDeviceCommands {
    /** [uuid]のデバイスへ[command]を送る。 */
    suspend fun execute(
        uuid: String,
        command: SesameCommand,
    ): SesameDeviceCommandExecutor.Outcome

    /** [uuid]のデバイスの状態を取り直す。取得できた場合はその施錠状態、できなければnull。 */
    suspend fun refreshStatus(uuid: String): Boolean?
}

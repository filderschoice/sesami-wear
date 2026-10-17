package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleClient
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome
import com.sesamiwear.mobile.command.SesameDeviceCommands
import com.sesamiwear.mobile.dial.DialOperations
import com.sesamiwear.mobile.dial.DialUiState
import com.sesamiwear.mobile.state.LockStateStore
import kotlinx.coroutines.delay

/**
 * 撮影モード中のダイヤル操作画面（BL-243）の操作（BL-246）。**デバッグ版にだけ含まれる。**
 * **Bluetoothを使わず**、撮影用の状態を読み、操作は撮影用の実行口（[ShowcaseDeviceCommands]）へ渡す。
 *
 * 撮影モードの操作画面で選んだ状態を、ダイヤル画面の各表示へ次のとおり対応させる。
 *
 * - 施錠中・解錠中: 操作できる表示。角度は実測（DESIGN.md、施錠-180・解錠-270）の値にする。
 * - 未取得: 「途中の位置」の表示（なぞっても何も送らない）。角度は施錠と解錠の中間にする。
 * - 失敗（認証/端末設定/通信）: Bluetoothで届かないときの3種類の文言（ログイン失敗/許可なし/届かない）。
 *
 * 接続・送信の待ちを見せるため、読むたび・送るたびに[delayMillis]だけ待つ。
 */
class ShowcaseDialOperations(
    private val uuid: String,
    private val lockStateStore: LockStateStore,
    private val commands: SesameDeviceCommands,
    private val delayMillis: Long = DEFAULT_DELAY_MILLIS,
) : DialOperations {
    override suspend fun load(lastResult: String?): DialUiState {
        delay(delayMillis)
        val snapshot = lockStateStore.load(uuid)
        val failure = snapshot?.lastFailure
        if (failure != null) return DialUiState.Unavailable(DialUiState.unavailableMessage(bleResultOf(failure)))
        val isLocked = snapshot?.isLocked
        return DialUiState.Ready(
            position = positionOf(isLocked),
            isLocked = isLocked,
            batteryPercentage = snapshot?.batteryPercentage,
            lastResult = lastResult,
        )
    }

    override suspend fun run(command: SesameCommand): DialUiState {
        delay(delayMillis)
        val succeeded = commands.execute(uuid, command) == Outcome.SUCCESS
        return load(lastResult = DialUiState.resultMessage(command, succeeded))
    }

    companion object {
        const val LOCKED_POSITION = -180
        const val UNLOCKED_POSITION = -270
        const val MIDDLE_POSITION = -225
        private const val DEFAULT_DELAY_MILLIS = 800L

        fun positionOf(isLocked: Boolean?): Int =
            when (isLocked) {
                true -> LOCKED_POSITION
                false -> UNLOCKED_POSITION
                null -> MIDDLE_POSITION
            }

        /** 撮影用に設定した失敗を、ダイヤル画面の「Bluetoothで届かない」文言の種類へ対応させる。 */
        fun bleResultOf(failure: SesameStatusFailure): SesameBleClient.Result =
            when (failure) {
                SesameStatusFailure.AUTH_OR_QUOTA -> SesameBleClient.Result.LOGIN_FAILED
                SesameStatusFailure.BACKGROUND_RESTRICTED -> SesameBleClient.Result.PERMISSION_DENIED
                SesameStatusFailure.COMMUNICATION -> SesameBleClient.Result.CONNECTION_FAILED
            }
    }
}

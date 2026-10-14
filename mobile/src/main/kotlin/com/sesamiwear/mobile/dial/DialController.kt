package com.sesamiwear.mobile.dial

import android.content.Context
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleAddressCache
import com.sesamiwear.mobile.ble.SesameBleClient
import com.sesamiwear.mobile.ble.SesameBleMechStatus
import com.sesamiwear.mobile.messaging.SesameStatusSyncer
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import com.sesamiwear.mobile.widget.SesameWidgetUpdater

/**
 * ダイヤル操作画面（BL-243）のBluetooth操作。**Web APIへは一切倒さない**（2026-10-15のユーザー指示）。
 *
 * 施錠/解錠の通常の実行口（`SesameDeviceCommandExecutor`）はBluetoothで届かなければWeb APIへ倒すため使わず、
 * [SesameBleClient]を直接呼ぶ。上限時間は既定（合計12秒）を使う。画面を開いて待っている利用者に向けた
 * 操作のため、ウィジェットのように受信の制限時間（約10秒）に縛られない。
 *
 * 操作に成功したら角度を読み直し、結果をウィジェット・ウォッチの表示にも反映する（通常の操作と同じ保存先）。
 * Android依存の薄いアダプタのためユニットテスト対象外（判定と文言は[DialSweep] / [DialUiState]でテスト済み）。
 */
class DialController(
    context: Context,
    private val credentials: SesameCredentials,
) {
    private val appContext = context.applicationContext
    private val client =
        SesameBleClient(
            appContext,
            addressCache = SesameBleAddressCache(SharedPreferencesKeyValueStore.forBleReachability(appContext)),
        )

    /** 接続して現在の角度を読む。 */
    suspend fun load(lastResult: String? = null): DialUiState {
        val outcome = client.fetchStatus(credentials)
        val status = outcome.status
        return if (status != null) {
            save(status, isLocked = isLockedOf(status))
            readyOf(status, lastResult)
        } else {
            DialUiState.Unavailable(DialUiState.unavailableMessage(outcome.result))
        }
    }

    /** [command]をBluetoothで送り、送ったあとの角度を読み直す。 */
    suspend fun run(command: SesameCommand): DialUiState {
        val succeeded = client.execute(credentials, command).result == SesameBleClient.Result.SUCCESS
        val message = DialUiState.resultMessage(command, succeeded)
        if (succeeded) {
            // 読み直しに失敗しても、送ったコマンドが意図した状態を保存する（通常の操作と同じ扱い、BL-015）。
            val status = client.fetchStatus(credentials).status
            if (status != null) {
                save(status, isLocked = isLockedOf(status) ?: (command == SesameCommand.LOCK))
                return readyOf(status, message)
            }
            saveCommandResult(command)
        }
        return load(lastResult = message)
    }

    private fun readyOf(
        status: SesameBleMechStatus,
        lastResult: String?,
    ) = DialUiState.Ready(
        position = status.position,
        isLocked = isLockedOf(status),
        batteryPercentage = status.batteryPercentage,
        lastResult = lastResult,
    )

    private fun isLockedOf(status: SesameBleMechStatus): Boolean? =
        when {
            status.isInLockRange -> true
            status.isInUnlockRange -> false
            else -> null
        }

    /** 読めた角度と状態を保存する。施錠・解錠のどちらの範囲でもなければ、施錠状態は変えない。 */
    private suspend fun save(
        status: SesameBleMechStatus,
        isLocked: Boolean?,
    ) {
        val measurement =
            SesameStatusMeasurement(
                batteryPercentage = status.batteryPercentage,
                position = status.position,
                route = SesameStatusRoute.BLE,
            )
        isLocked?.let { store(it, measurement) }
    }

    private suspend fun saveCommandResult(command: SesameCommand) {
        store(command == SesameCommand.LOCK, SesameStatusMeasurement.ofRoute(SesameStatusRoute.BLE))
    }

    private suspend fun store(
        isLocked: Boolean,
        measurement: SesameStatusMeasurement,
    ) {
        val lockStateStore = SesameDeviceStores.lockState(appContext)
        lockStateStore.save(credentials.uuid, isLocked, System.currentTimeMillis(), measurement)
        lockStateStore.load(credentials.uuid)?.let { SesameStatusSyncer(appContext).sync(credentials.uuid, it) }
        SesameWidgetUpdater.updateAll(appContext)
    }
}

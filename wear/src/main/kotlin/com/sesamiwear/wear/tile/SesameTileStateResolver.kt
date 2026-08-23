package com.sesamiwear.wear.tile

import android.content.Context
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameDeviceListReader
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader

/**
 * Tile/Complication表示用の表示名・状態解決をまとめる（BL-071、複数デバイス一括操作対応）。
 * 対象デバイスuuidが[SesameWearProtocol.ALL_DEVICES_TARGET_UUID]（「全デバイス」選択、
 * [com.sesamiwear.wear.ui.DeviceSelectionScreen]参照）の場合は登録済み全デバイスの状態を
 * [TileDisplayStateResolver.resolveAggregate]で集約し、それ以外は単一デバイスの状態を解決する。
 * いずれもDataItemが一定時間以上古い場合は状態取得をリクエストする（BL-061と同様の巻き戻り防止
 * 対応、コマンド実行直後に古いGET結果へ上書きされることを避けるため）。
 * Android Google Play Services依存の薄いアダプタのためユニットテスト対象外
 * （表示状態の判定ロジック自体は[TileDisplayStateResolver]でテスト済み）。
 */
object SesameTileStateResolver {
    suspend fun resolveDisplayName(
        context: Context,
        deviceUuid: String,
    ): String =
        if (deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID) {
            ALL_DEVICES_DISPLAY_NAME
        } else {
            SesameDeviceListReader.readLatest(context)
                .find { it.uuid == deviceUuid }
                ?.displayName
                ?.ifBlank { null }
                ?: deviceUuid
        }

    suspend fun resolveState(
        context: Context,
        deviceUuid: String,
        nodeId: String?,
    ): TileDisplayState =
        if (deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID) {
            resolveAggregateState(context, nodeId)
        } else {
            resolveSingleDeviceState(context, deviceUuid, nodeId)
        }

    private suspend fun resolveSingleDeviceState(
        context: Context,
        deviceUuid: String,
        nodeId: String?,
    ): TileDisplayState {
        val snapshot = SesameStatusSnapshotReader.readLatest(context, deviceUuid)
        requestStatusIfStale(context, nodeId, deviceUuid, snapshot?.updatedAtEpochMillis)
        return TileDisplayStateResolver.resolve(nodeId != null, false, snapshot?.isLocked)
    }

    private suspend fun resolveAggregateState(
        context: Context,
        nodeId: String?,
    ): TileDisplayState {
        val devices = SesameDeviceListReader.readLatest(context)
        val lockStates =
            devices.map { device ->
                val snapshot = SesameStatusSnapshotReader.readLatest(context, device.uuid)
                requestStatusIfStale(context, nodeId, device.uuid, snapshot?.updatedAtEpochMillis)
                snapshot?.isLocked
            }
        return TileDisplayStateResolver.resolveAggregate(nodeId != null, false, lockStates)
    }

    private suspend fun requestStatusIfStale(
        context: Context,
        nodeId: String?,
        deviceUuid: String,
        updatedAtEpochMillis: Long?,
    ) {
        val isStale =
            updatedAtEpochMillis == null ||
                System.currentTimeMillis() - updatedAtEpochMillis > STATUS_STALE_THRESHOLD_MILLIS
        if (nodeId != null && isStale) {
            SesameCommandSenderProvider.create(context).requestStatus(nodeId, deviceUuid)
        }
    }

    private const val ALL_DEVICES_DISPLAY_NAME = "全デバイス"
    private const val STATUS_STALE_THRESHOLD_MILLIS = 30_000L
}

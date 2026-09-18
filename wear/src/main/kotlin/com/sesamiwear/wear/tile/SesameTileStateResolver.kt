package com.sesamiwear.wear.tile

import android.content.Context
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.core.display.SesameStatusFreshness
import com.sesamiwear.wear.demo.DemoLockStateStore
import com.sesamiwear.wear.messaging.SesameDeviceListReader
import com.sesamiwear.wear.messaging.SesameStatusSnapshotReader

/**
 * Tile / Complicationが表示する状態と、その状態をいつ取得したかの文言（BL-142）。
 * [freshnessLabel]がnullの場合は鮮度を表示しない（デモモードは取得という概念が無いため）。
 */
data class SesameTileStatus(
    val state: TileDisplayState,
    val freshnessLabel: String?,
)

/**
 * Tile/Complication表示用の表示名・状態解決をまとめる（BL-071、複数デバイス一括操作対応）。
 * 対象デバイスuuidが[SesameDeviceTargets.isAllDevices]（「全デバイス」選択、
 * [com.sesamiwear.wear.ui.DeviceSelectionScreen]参照）の場合は登録済み全デバイスの状態を
 * [TileDisplayStateResolver.resolveAggregate]で集約し、それ以外は単一デバイスの状態を解決する。
 * 対象デバイスuuidが[SesameDemoMode.DEMO_DEVICE_UUID]（デモモード、BL-109）の場合は
 * wear単体で保持するダミー状態（[DemoLockStateStore]）から解決する。
 *
 * **状態取得のリクエストは一切送らない（BL-142）。** 以前はDataItemが30秒以上古ければ
 * `PATH_STATUS_REQUEST`を送っていたが、これはTileの描画ごと・Complicationの定期更新ごと
 * （`UPDATE_PERIOD_SECONDS`=600秒）に評価されるため、Sesame Web APIの月間リクエスト上限
 * （1000回）を大幅に超過していた。状態の更新契機は、施錠/解錠の成功と、利用者が明示的に
 * タップしたとき（[com.sesamiwear.wear.action.SesameStatusRefreshActivity]）だけにする。
 * 代わりに、保存済みスナップショットの古さを[SesameStatusFreshness]の文言として表示へ添える。
 *
 * 表示名の決定規則は[SesameDeviceTargets.displayName]（BL-119でcoreへ移設）が持つ。
 * Android Google Play Services依存の薄いアダプタのためユニットテスト対象外
 * （表示状態の判定ロジック自体は[TileDisplayStateResolver]・[SesameDemoMode]・
 * [SesameStatusFreshness]でテスト済み）。
 */
object SesameTileStateResolver {
    suspend fun resolveDisplayName(
        context: Context,
        deviceUuid: String,
    ): String {
        // 固定文言（デモ用デバイス・全デバイス）の場合はDataItemを読まない（BL-119の移設前と同じ）。
        val needsDeviceList = !SesameDemoMode.isDemoDevice(deviceUuid) && !SesameDeviceTargets.isAllDevices(deviceUuid)
        val registeredDevices = if (needsDeviceList) SesameDeviceListReader.readLatest(context) else emptyList()
        return SesameDeviceTargets.displayName(deviceUuid, registeredDevices)
    }

    suspend fun resolveStatus(
        context: Context,
        deviceUuid: String,
        nodeId: String?,
    ): SesameTileStatus =
        when {
            SesameDemoMode.isDemoDevice(deviceUuid) ->
                SesameTileStatus(
                    state = SesameDemoMode.displayState(DemoLockStateStore(context).isLocked()),
                    // デモはSesame APIから取得しないため、鮮度という概念が無い。
                    freshnessLabel = null,
                )
            SesameDeviceTargets.isAllDevices(deviceUuid) -> resolveAggregateStatus(context, nodeId)
            else -> resolveSingleDeviceStatus(context, deviceUuid, nodeId)
        }

    private suspend fun resolveSingleDeviceStatus(
        context: Context,
        deviceUuid: String,
        nodeId: String?,
    ): SesameTileStatus {
        val snapshot = SesameStatusSnapshotReader.readLatest(context, deviceUuid)
        return SesameTileStatus(
            state = TileDisplayStateResolver.resolve(nodeId != null, false, snapshot?.isLocked),
            freshnessLabel = freshnessLabelOf(snapshot?.updatedAtEpochMillis),
        )
    }

    private suspend fun resolveAggregateStatus(
        context: Context,
        nodeId: String?,
    ): SesameTileStatus {
        val snapshots =
            SesameDeviceListReader.readLatest(context).map { device ->
                SesameStatusSnapshotReader.readLatest(context, device.uuid)
            }
        val oldestUpdatedAt = SesameStatusFreshness.oldestOf(snapshots.map { it?.updatedAtEpochMillis })
        return SesameTileStatus(
            state = TileDisplayStateResolver.resolveAggregate(nodeId != null, false, snapshots.map { it?.isLocked }),
            freshnessLabel = freshnessLabelOf(oldestUpdatedAt),
        )
    }

    private fun freshnessLabelOf(updatedAtEpochMillis: Long?): String =
        SesameStatusFreshness.label(updatedAtEpochMillis, System.currentTimeMillis())
}

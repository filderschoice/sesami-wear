package com.sesamiwear.wear.action

import android.content.Context
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.wear.messaging.SesameDeviceListReader

/**
 * コマンド送信・状態更新の対象デバイスuuid一覧を解決する（BL-071、複数デバイス一括操作対応）。
 * [deviceUuid]が[SesameWearProtocol.ALL_DEVICES_TARGET_UUID]（「全デバイス」選択）の場合は
 * 登録済み全デバイスのuuidを、それ以外は単一デバイスのuuidのみを返す。
 * [SesameActionActivity]と[SesameStatusRefreshActivity]で共用する。
 */
object SesameActionTargetResolver {
    suspend fun resolveDeviceUuids(
        context: Context,
        deviceUuid: String,
    ): List<String> =
        if (deviceUuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID) {
            SesameDeviceListReader.readLatest(context).map { it.uuid }
        } else {
            listOf(deviceUuid)
        }
}

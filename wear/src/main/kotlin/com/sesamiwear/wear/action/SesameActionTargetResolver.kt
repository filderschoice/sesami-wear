package com.sesamiwear.wear.action

import android.content.Context
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.wear.messaging.SesameDeviceListReader

/**
 * コマンド送信・状態更新の対象デバイスuuid一覧を解決する（BL-071、複数デバイス一括操作対応）。
 * 展開規則は[SesameDeviceTargets.targetUuids]（BL-119でcoreへ移設、mobileのウィジェットと共通）が持ち、
 * ここはmobile側から同期された登録済みデバイス一覧を読み取って渡すだけのAndroid依存アダプタ。
 * 一覧の読み取りは「全デバイス」選択時にだけ行う（単一デバイス時は不要なため）。
 * [SesameActionActivity]と[SesameStatusRefreshActivity]で共用する。
 */
object SesameActionTargetResolver {
    suspend fun resolveDeviceUuids(
        context: Context,
        deviceUuid: String,
    ): List<String> {
        val registeredDevices =
            if (SesameDeviceTargets.isAllDevices(
                    deviceUuid,
                )
            ) {
                SesameDeviceListReader.readLatest(context)
            } else {
                emptyList()
            }
        return SesameDeviceTargets.targetUuids(deviceUuid, registeredDevices)
    }
}

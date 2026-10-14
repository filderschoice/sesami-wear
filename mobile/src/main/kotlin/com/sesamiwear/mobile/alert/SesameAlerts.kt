package com.sesamiwear.mobile.alert

import android.content.Context
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * 状態の変化を受けて「お知らせ」を出す配線（BL-238）。
 *
 * `SesameDeviceCommandExecutorFactory`がロック状態の変化（取得・操作の成功）ごとに呼ぶ。
 * 設定（[SesameAlertSettingsStore]）がオンで、判定（[LowBatteryAlertTracker]）が通知すべきと返したときだけ
 * 通知する。デモ用デバイスは実物の電池を持たないため対象外。
 * 通知に出す名前は利用者が付けた表示名で、uuidは出さない（未設定なら「セサミ」）。
 * 配線だけの薄いアダプタのためユニットテスト対象外（判定と設定はそれぞれテスト済み）。
 */
class SesameAlerts(
    context: Context,
    private val loadCredentials: () -> List<SesameCredentials>,
) {
    private val appContext = context.applicationContext
    private val store = SharedPreferencesKeyValueStore.forAlerts(appContext)
    private val settings = SesameAlertSettingsStore(store)
    private val lowBattery = LowBatteryAlertTracker(store)

    fun onStatusChanged(
        uuid: String,
        snapshot: SesameStatusSnapshot,
    ) {
        if (SesameDemoMode.isDemoDevice(uuid)) return
        // 判定は設定がオフでも進め、通知済みの記録を残す。オンへ戻した直後に、既に下回っていた分が
        // まとめて通知されないようにするため。
        val notify = lowBattery.shouldNotify(uuid, snapshot.batteryPercentage)
        if (notify && settings.isLowBatteryEnabled()) {
            SesameAlertNotifier(appContext).notifyLowBattery(
                uuid = uuid,
                deviceName = displayNameOf(uuid),
                percentage = requireNotNull(snapshot.batteryPercentage),
            )
        }
    }

    private fun displayNameOf(uuid: String): String = nameOf(loadCredentials(), uuid)

    companion object {
        private const val UNNAMED_DEVICE = "セサミ"

        /** 通知に出す名前。利用者が付けた表示名で、未設定なら「セサミ」（uuidは出さない）。 */
        fun displayNameOf(
            context: Context,
            uuid: String,
        ): String = nameOf(SesameDeviceStores.credentials(context).loadAll(), uuid)

        private fun nameOf(
            credentials: List<SesameCredentials>,
            uuid: String,
        ): String = credentials.find { it.uuid == uuid }?.displayName?.ifBlank { null } ?: UNNAMED_DEVICE
    }
}

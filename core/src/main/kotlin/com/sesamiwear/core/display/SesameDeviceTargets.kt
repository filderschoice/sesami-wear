package com.sesamiwear.core.display

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol

/**
 * 操作対象デバイス（1台・「全デバイス」・デモ用デバイス）の選択肢と、表示名・コマンド送信先の解決を
 * まとめる（BL-119）。
 *
 * もとはwearの`SesameActionTargetResolver`（全デバイス時の対象uuid展開、BL-071）、
 * `DeviceSelectionScreen`（「2台以上なら全デバイスを先頭に出す」判定、BL-071/BL-109）、
 * `SesameTileStateResolver`（表示名の解決）に分かれていた。mobileのホーム画面ウィジェットでも
 * 同じ選択肢・同じ展開規則を使うため、Android非依存の判定部分だけをここへ集めている。
 * 登録済みデバイス一覧の取得方法（wearはDataItem、mobileは資格情報ストア）は呼び出し側が担う。
 */
object SesameDeviceTargets {
    /** 「全デバイス」の表示名（Tileのデバイス名チップ、デバイス選択画面のチップで共通）。 */
    const val ALL_DEVICES_DISPLAY_NAME = "全デバイス"

    /** 「全デバイス」を選択肢へ出す登録台数の下限。1台のみなら個別選択と等価で冗長なため出さない。 */
    const val MIN_DEVICES_FOR_ALL_DEVICES_CHOICE = 2

    /** デバイス選択画面の1項目。[uuid]は割り当てとして保存する値、[label]はチップの表示文言。 */
    data class Choice(val uuid: String, val label: String)

    fun isAllDevices(uuid: String): Boolean = uuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID

    /**
     * デバイス選択画面へ並べる選択肢を、表示順に返す。
     * 登録済みデバイスが0台ならデモ用デバイスのみ（[SesameDemoMode.selectableDevices]）、
     * 2台以上なら先頭に「全デバイス」を置き、続けて各デバイスを登録順に並べる。
     */
    fun choices(registeredDevices: List<SesameDeviceSummary>): List<Choice> {
        val devices = SesameDemoMode.selectableDevices(registeredDevices)
        val deviceChoices = devices.map { Choice(uuid = it.uuid, label = it.displayName.ifBlank { it.uuid }) }
        return if (devices.size >= MIN_DEVICES_FOR_ALL_DEVICES_CHOICE) {
            listOf(Choice(SesameWearProtocol.ALL_DEVICES_TARGET_UUID, ALL_DEVICES_DISPLAY_NAME)) + deviceChoices
        } else {
            deviceChoices
        }
    }

    /**
     * 割り当て済みの[deviceUuid]の表示名。デモ用デバイス・「全デバイス」は固定文言、
     * それ以外は登録済み一覧の表示名（空欄ならuuid、一覧に無ければuuid）を返す。
     */
    fun displayName(
        deviceUuid: String,
        registeredDevices: List<SesameDeviceSummary>,
    ): String =
        when {
            SesameDemoMode.isDemoDevice(deviceUuid) -> SesameDemoMode.DEMO_DEVICE_DISPLAY_NAME
            isAllDevices(deviceUuid) -> ALL_DEVICES_DISPLAY_NAME
            else ->
                registeredDevices
                    .find { it.uuid == deviceUuid }
                    ?.displayName
                    ?.ifBlank { null }
                    ?: deviceUuid
        }

    /**
     * コマンド送信・状態更新の対象uuid一覧。「全デバイス」なら登録済み全デバイスのuuidを登録順に、
     * それ以外は[deviceUuid]のみを返す（受け手は単一デバイス処理をN回行うだけで済む、BL-071）。
     */
    fun targetUuids(
        deviceUuid: String,
        registeredDevices: List<SesameDeviceSummary>,
    ): List<String> =
        if (isAllDevices(deviceUuid)) {
            registeredDevices.map { it.uuid }
        } else {
            listOf(deviceUuid)
        }
}

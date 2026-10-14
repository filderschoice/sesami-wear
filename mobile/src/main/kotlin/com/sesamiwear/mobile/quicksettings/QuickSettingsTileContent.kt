package com.sesamiwear.mobile.quicksettings

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.widget.SesameWidgetModel
import com.sesamiwear.mobile.widget.WidgetTapAction

/**
 * スマートフォンのクイック設定パネルの「Sesame」タイル（BL-240）に出す内容とタップ時の動作。
 *
 * パネルのタイルは端末に1つしか置けず、ウィジェットのように対象を選ぶ画面を持たせにくい。そこで対象は
 * 登録が1台ならその1台、2台以上なら「全デバイス」に固定する（[targetUuid]）。表示と操作のルールは
 * ホーム画面ウィジェットと同じで、判定は`SesameWidgetModelResolver`と[WidgetTapAction]を流用する
 * （施錠中→解錠の確認画面、解錠中・一部解錠→確認なしで施錠、通信中→何もしない）。
 * ウィジェットと違い、状態が分からないときはタップで状態を取得しなおす（パネルには「更新」ボタンを
 * 置けないため）。Sesameが未登録ならアプリを開く。
 *
 * Android非依存のためユニットテスト対象（`TileService`への反映は[SesameQuickSettingsTileService]）。
 */
data class QuickSettingsTileContent(
    val label: String,
    val subtitle: String,
    val tileState: TileState,
    val iconKind: IconKind,
    val tap: Tap,
) {
    /** `android.service.quicksettings.Tile`の状態に対応する。 */
    enum class TileState { ACTIVE, INACTIVE, UNAVAILABLE }

    /** タイルのアイコン。施錠中だけ閉じた錠、それ以外は開いた錠。 */
    enum class IconKind { LOCKED, UNLOCKED }

    sealed interface Tap {
        data object OpenApp : Tap

        data class Run(val deviceUuid: String, val command: SesameCommand) : Tap

        data class ConfirmUnlock(val deviceUuid: String, val displayName: String) : Tap

        data class Refresh(val deviceUuid: String) : Tap

        data object None : Tap
    }

    companion object {
        const val DEFAULT_LABEL = "Sesame"
        const val NOT_REGISTERED_SUBTITLE = "タップして登録"

        /** パネルのタイルの対象。未登録ならnull、1台ならその1台、2台以上なら全デバイス。 */
        fun targetUuid(registeredDevices: List<SesameDeviceSummary>): String? =
            when (registeredDevices.size) {
                0 -> null
                1 -> registeredDevices.single().uuid
                else -> SesameWearProtocol.ALL_DEVICES_TARGET_UUID
            }

        fun from(model: SesameWidgetModel): QuickSettingsTileContent =
            when (model) {
                SesameWidgetModel.Unconfigured ->
                    QuickSettingsTileContent(
                        label = DEFAULT_LABEL,
                        subtitle = NOT_REGISTERED_SUBTITLE,
                        tileState = TileState.INACTIVE,
                        iconKind = IconKind.UNLOCKED,
                        tap = Tap.OpenApp,
                    )
                is SesameWidgetModel.Configured ->
                    QuickSettingsTileContent(
                        label = labelOf(model),
                        subtitle = model.statusLabel,
                        tileState = tileStateOf(model.state),
                        iconKind = if (model.state == TileDisplayState.LOCKED) IconKind.LOCKED else IconKind.UNLOCKED,
                        tap = tapOf(model),
                    )
            }

        /**
         * 表示名が未設定のデバイスは、ウィジェットではuuidを名前に代えて出す（`SesameDeviceTargets.displayName`）。
         * パネルはロック画面からも見えるため、uuidは出さず既定の名前にする。
         */
        private fun labelOf(model: SesameWidgetModel.Configured): String =
            model.displayName.takeIf { it.isNotBlank() && it != model.deviceUuid } ?: DEFAULT_LABEL

        private fun tileStateOf(state: TileDisplayState): TileState =
            when (state) {
                TileDisplayState.LOCKED -> TileState.ACTIVE
                TileDisplayState.IN_PROGRESS -> TileState.UNAVAILABLE
                else -> TileState.INACTIVE
            }

        private fun tapOf(model: SesameWidgetModel.Configured): Tap =
            when (val action = WidgetTapAction.forModel(model)) {
                is WidgetTapAction.Run -> Tap.Run(model.deviceUuid, action.command)
                is WidgetTapAction.Confirm -> Tap.ConfirmUnlock(model.deviceUuid, model.displayName)
                WidgetTapAction.None ->
                    if (model.state == TileDisplayState.UNKNOWN) Tap.Refresh(model.deviceUuid) else Tap.None
                WidgetTapAction.OpenConfiguration -> Tap.OpenApp
            }
    }
}

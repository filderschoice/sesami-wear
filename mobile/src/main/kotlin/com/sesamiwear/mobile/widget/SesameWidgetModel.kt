package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.core.display.SesameDeviceTargets
import com.sesamiwear.core.display.SesameTileContent

/**
 * ホーム画面ウィジェット1つ分の表示内容（BL-121）。Glanceの描画コードはこの値をそのまま並べるだけにし、
 * 文言・色・状態の決定はAndroid非依存の[SesameWidgetModelResolver]でユニットテストする。
 */
sealed interface SesameWidgetModel {
    /** 対象デバイスが未設定、または割り当て済みのデバイスが選択肢から消えた（削除された）状態。 */
    data object Unconfigured : SesameWidgetModel {
        const val MESSAGE = "タップして設定"
    }

    /**
     * 対象デバイスが決まっている状態。文言・色は wear の Tile と同じ
     * `core.display.SesameTileContent` から決める（BL-119）。状態色は右側（状態表示）にだけ使う。
     */
    data class Configured(
        val deviceUuid: String,
        val displayName: String,
        val state: TileDisplayState,
        val isAllDevices: Boolean,
    ) : SesameWidgetModel {
        val statusIcon: String get() = SesameTileContent.statusIcon(state)
        val statusLabel: String get() = SesameTileContent.statusLabel(state, isAllDevices)
        val actionLabel: String? get() = SesameTileContent.actionLabel(state, isAllDevices)
        val backgroundColorArgb: Int get() = SesameTileContent.backgroundColorArgb(state)
        val textColorArgb: Int get() = SesameTileContent.statusTextColorArgb(state)
    }
}

/**
 * 割り当て済みuuid・登録済みデバイス・保存済みのロック状態から、ウィジェットの表示内容を決める（BL-121）。
 *
 * - 未割り当て → [SesameWidgetModel.Unconfigured]
 * - デモ用デバイス → 登録済みデバイスが0台のときだけ有効（1台でも登録されたら未設定へ戻す。
 *   wearの選択肢と同じく、実デバイスとデモを取り違えないため）。状態は保存値、無ければ初期状態（施錠中）。
 * - 全デバイス → 登録済みデバイスが1台以上のとき有効。状態はwearと同じ集約規則
 *   （`TileDisplayStateResolver.resolveAggregate`、1台でも未取得なら状態不明）。
 * - 実デバイス → 登録済みのときだけ有効。未取得なら状態不明。
 *
 * mobileは自分自身がSesame APIを呼ぶため、wearの「スマホ未接続」は存在しない（常に接続扱い）。
 * 通信中の表示（IN_PROGRESS）はタップ操作（BL-122）で[isCommandInProgress]を渡す。
 */
object SesameWidgetModelResolver {
    fun resolve(
        assignedUuid: String?,
        registeredDevices: List<SesameDeviceSummary>,
        lockStateOf: (String) -> Boolean?,
        isCommandInProgress: Boolean = false,
    ): SesameWidgetModel {
        val uuid = assignedUuid ?: return SesameWidgetModel.Unconfigured
        val state =
            when {
                SesameDemoMode.isDemoDevice(uuid) ->
                    if (SesameDemoMode.isAvailable(registeredDevices)) {
                        TileDisplayStateResolver.resolve(
                            isPhoneConnected = true,
                            isCommandInProgress = isCommandInProgress,
                            isLocked = lockStateOf(uuid) ?: SesameDemoMode.INITIAL_IS_LOCKED,
                        )
                    } else {
                        null
                    }
                SesameDeviceTargets.isAllDevices(uuid) ->
                    registeredDevices.takeIf { it.isNotEmpty() }?.let { devices ->
                        TileDisplayStateResolver.resolveAggregate(
                            isPhoneConnected = true,
                            isCommandInProgress = isCommandInProgress,
                            lockStates = devices.map { lockStateOf(it.uuid) },
                        )
                    }
                registeredDevices.any { it.uuid == uuid } ->
                    TileDisplayStateResolver.resolve(
                        isPhoneConnected = true,
                        isCommandInProgress = isCommandInProgress,
                        isLocked = lockStateOf(uuid),
                    )
                else -> null
            }
        return if (state == null) {
            SesameWidgetModel.Unconfigured
        } else {
            SesameWidgetModel.Configured(
                deviceUuid = uuid,
                displayName = SesameDeviceTargets.displayName(uuid, registeredDevices),
                state = state,
                isAllDevices = SesameDeviceTargets.isAllDevices(uuid),
            )
        }
    }
}

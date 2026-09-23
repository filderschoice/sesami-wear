package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.mobile.state.LockStateStore

/**
 * 撮影モード（BL-212）で1台の撮影用デバイスへ与える状態。
 *
 * [isLocked]がnullなら「未取得」、[failure]があれば「直近の取得・操作に失敗」の表示になる。
 * [ageMillis]は最終取得時刻を今からどれだけ前にするか（0なら「たった今」）。
 */
data class ShowcaseDeviceState(
    val isLocked: Boolean?,
    val batteryPercentage: Int? = null,
    val position: Int? = null,
    val route: SesameStatusRoute? = null,
    val failure: SesameStatusFailure? = null,
    val ageMillis: Long = 0,
) {
    /**
     * [store]の[uuid]の保存値を、この状態へ丸ごと置き換える。
     *
     * [LockStateStore.save]は分からない項目に前回の値を残すため、いったん消してから書く
     * （電池残量を「不明」へ戻す、といった切り替えができるように）。
     */
    fun writeTo(
        store: LockStateStore,
        uuid: String,
        nowMillis: Long,
    ) {
        store.remove(uuid)
        if (isLocked != null) {
            store.save(
                uuid = uuid,
                isLocked = isLocked,
                updatedAtEpochMillis = nowMillis - ageMillis,
                measurement = SesameStatusMeasurement(batteryPercentage, position, route),
            )
        }
        failure?.let { store.saveFailure(uuid, it) }
    }

    companion object {
        /** 保存済みの[snapshot]を、操作画面で1項目だけ変えるための元の状態へ戻す（未保存なら「未取得」）。 */
        fun of(
            snapshot: SesameStatusSnapshot?,
            nowMillis: Long,
        ): ShowcaseDeviceState =
            ShowcaseDeviceState(
                isLocked = snapshot?.isLocked,
                batteryPercentage = snapshot?.batteryPercentage,
                position = snapshot?.position,
                route = snapshot?.lastRoute,
                failure = snapshot?.lastFailure,
                ageMillis = snapshot?.updatedAtEpochMillis?.let { (nowMillis - it).coerceAtLeast(0) } ?: 0,
            )
    }
}

/** 撮影用デバイスの見本（BL-212）。uuidは実物と衝突しない固定値で、APIキー・秘密鍵はダミー値。 */
object ShowcasePresets {
    val devices: List<Pair<SesameCredentials, ShowcaseDeviceState>> =
        listOf(
            device(index = 1, name = "玄関", ShowcaseDeviceState(true, 85, position = 12, SesameStatusRoute.BLE)),
            device(index = 2, name = "勝手口", ShowcaseDeviceState(false, 62, position = 250, SesameStatusRoute.WEB_API)),
            device(index = 3, name = "ガレージ", ShowcaseDeviceState(true, 40, position = 15, SesameStatusRoute.BLE)),
        )

    private fun device(
        index: Int,
        name: String,
        state: ShowcaseDeviceState,
    ): Pair<SesameCredentials, ShowcaseDeviceState> =
        SesameCredentials(
            uuid = "00000000-0000-4000-8000-00000000000$index",
            apiKey = ShowcaseCredentialsKeyValueStore.DUMMY_API_KEY,
            secretKeyHex = ShowcaseCredentialsKeyValueStore.DUMMY_SECRET_KEY_HEX,
            displayName = name,
        ) to state
}

package com.sesamiwear.wear.demo

import android.content.Context
import com.sesamiwear.core.SesameDemoMode

/**
 * デモモード（BL-109、[SesameDemoMode]）のダミー施錠状態をwear側のみで永続化する。
 * 実デバイスの状態はmobile側がDataItemで同期するが（[com.sesamiwear.wear.messaging.SesameStatusSnapshotReader]）、
 * デモ用デバイスは実在せずスマホ側の関与がないため、wear単体で完結させる。
 * secretKey等の機密情報は扱わないため暗号化ストレージは使わず、通常のSharedPreferencesで保存する
 * （[com.sesamiwear.wear.tile.TileDeviceAssignmentStore]と同じ方針）。
 */
class DemoLockStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isLocked(): Boolean = preferences.getBoolean(KEY_IS_LOCKED, SesameDemoMode.INITIAL_IS_LOCKED)

    fun setLocked(isLocked: Boolean) {
        preferences.edit().putBoolean(KEY_IS_LOCKED, isLocked).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "demo_lock_state"
        const val KEY_IS_LOCKED = "is_locked"
    }
}

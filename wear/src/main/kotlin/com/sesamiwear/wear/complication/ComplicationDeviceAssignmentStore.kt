package com.sesamiwear.wear.complication

import android.content.Context

/**
 * Complicationインスタンス（[complicationInstanceId]、Wear Watchfaceがcomplicationスロットごとに
 * 割り振る固有ID）ごとに、表示対象Sesameデバイスのuuidを永続化する（BL-054、複数Complication
 * インスタンス方式、[com.sesamiwear.wear.tile.TileDeviceAssignmentStore]と同型）。
 * secretKey等の機密情報は扱わないため暗号化ストレージは使わず、通常のSharedPreferencesで保存する。
 */
class ComplicationDeviceAssignmentStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun assignedDeviceUuid(complicationInstanceId: Int): String? =
        preferences.getString(keyFor(complicationInstanceId), null)

    fun assignDevice(
        complicationInstanceId: Int,
        uuid: String,
    ) {
        preferences.edit().putString(keyFor(complicationInstanceId), uuid).apply()
    }

    private fun keyFor(complicationInstanceId: Int) = "$KEY_PREFIX$complicationInstanceId"

    private companion object {
        const val PREFERENCES_NAME = "complication_device_assignments"
        const val KEY_PREFIX = "complication_"
    }
}

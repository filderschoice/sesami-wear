package com.sesamiwear.wear.tile

import android.content.Context

/**
 * Tileインスタンス（[tileId]、Wear Tilesがタイル追加ごとに割り振る固有ID）ごとに、
 * 操作対象Sesameデバイスのuuidを永続化する（BL-052、複数Tileインスタンス方式）。
 * secretKey等の機密情報は扱わないため暗号化ストレージは使わず、通常のSharedPreferencesで保存する。
 */
class TileDeviceAssignmentStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun assignedDeviceUuid(tileId: Int): String? = preferences.getString(keyFor(tileId), null)

    fun assignDevice(
        tileId: Int,
        uuid: String,
    ) {
        preferences.edit().putString(keyFor(tileId), uuid).apply()
    }

    private fun keyFor(tileId: Int) = "$KEY_PREFIX$tileId"

    private companion object {
        const val PREFERENCES_NAME = "tile_device_assignments"
        const val KEY_PREFIX = "tile_"
    }
}

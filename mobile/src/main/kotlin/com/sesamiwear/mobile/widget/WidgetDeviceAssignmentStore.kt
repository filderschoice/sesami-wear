package com.sesamiwear.mobile.widget

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameKeyValueStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * ホーム画面ウィジェットのインスタンス（appWidgetId）ごとに、操作対象デバイスのuuidを保存する（BL-121）。
 * 値は実デバイスのuuid・`SesameWearProtocol.ALL_DEVICES_TARGET_UUID`・`SesameDemoMode.DEMO_DEVICE_UUID`の
 * いずれかで、機密情報を含まないため保存先は非暗号化SharedPreferencesでよい（wearの
 * `TileDeviceAssignmentStore`と同方針）。全インスタンス分を1つのJSONオブジェクトにして単一キーへ保存し、
 * ウィジェットの削除時に[remove]で個別に消す（[SesameKeyValueStore]にキー単位の削除が無いため）。
 */
class WidgetDeviceAssignmentStore(private val keyValueStore: SesameKeyValueStore) {
    @Synchronized
    fun assignedDeviceUuid(appWidgetId: Int): String? = loadAll()[appWidgetId.toString()]

    @Synchronized
    fun assign(
        appWidgetId: Int,
        uuid: String,
    ) {
        saveAll(loadAll() + (appWidgetId.toString() to uuid))
    }

    @Synchronized
    fun remove(appWidgetIds: Collection<Int>) {
        val assignments = loadAll()
        val remaining = assignments - appWidgetIds.map { it.toString() }.toSet()
        if (remaining.size != assignments.size) saveAll(remaining)
    }

    /** [uuid]を割り当てているインスタンスの割り当てを解除する（デモの割り当て解除などに使う）。 */
    @Synchronized
    fun unassignDevice(uuid: String) {
        val assignments = loadAll()
        val remaining = assignments.filterValues { it != uuid }
        if (remaining.size != assignments.size) saveAll(remaining)
    }

    /**
     * 登録済みデバイスが変わったときに呼ぶ。1台でも登録されていれば、デモ用デバイスを割り当てていた
     * インスタンスの割り当てを解除し「タップして設定」へ戻す（BL-123。実デバイスとデモを取り違えないため、
     * wearの選択肢と同じく登録後はデモを提示しない）。
     */
    @Synchronized
    fun onRegisteredDevicesChanged(registeredDeviceCount: Int) {
        if (registeredDeviceCount > 0) unassignDevice(SesameDemoMode.DEMO_DEVICE_UUID)
    }

    private fun loadAll(): Map<String, String> {
        val json = keyValueStore.getString(KEY_ASSIGNMENTS) ?: return emptyMap()
        val root =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (
                @Suppress("SwallowedException") e: SerializationException,
            ) {
                null
            }
        return root.orEmpty().mapNotNull { (id, value) ->
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { id to it }
        }.toMap()
    }

    private fun saveAll(assignments: Map<String, String>) {
        keyValueStore.putString(
            KEY_ASSIGNMENTS,
            JsonObject(assignments.mapValues { JsonPrimitive(it.value) }).toString(),
        )
    }
}

private const val KEY_ASSIGNMENTS = "widget_device_assignments"

package com.sesamiwear.mobile.state

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.SesameStatusSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Sesameデバイスごとのロック状態（施錠中かどうかと更新時刻）をmobile端末内に保存する（BL-120）。
 *
 * wearはmobileから同期されたDataItemで状態を表示するが、mobileのホーム画面ウィジェット（BL-121以降）は
 * Data Layerを経由しないため、mobile側にも最後に分かった状態を持たせる。uuid・真偽値・時刻のみで
 * 機密情報を含まないため、実際の保存先は非暗号化のSharedPreferencesでよい
 * （wearの`TileDeviceAssignmentStore`と同方針。保存先は[SesameKeyValueStore]で注入する）。
 *
 * 全デバイス分を1つのJSONオブジェクト（uuid → {isLocked, updatedAtEpochMillis}）にして単一キーで保存する
 * （[SesameKeyValueStore]にキー単位の削除が無く、登録デバイス数は数台のため）。mobileモジュールは
 * kotlinx.serializationのコンパイラプラグインを適用していないため、`@Serializable`ではなく
 * JsonObjectを直接組み立てる（R8のkeepルールも不要になる）。
 * ウォッチ経由とウィジェット経由の書き込みが並行しうるため、読み書きは同期化する。
 */
class LockStateStore(private val keyValueStore: SesameKeyValueStore) {
    /** [uuid]の保存済み状態。一度も取得・操作していなければnull。 */
    @Synchronized
    fun load(uuid: String): SesameStatusSnapshot? = loadAll()[uuid]

    @Synchronized
    fun save(
        uuid: String,
        isLocked: Boolean,
        updatedAtEpochMillis: Long,
    ) {
        saveAll(loadAll() + (uuid to SesameStatusSnapshot(isLocked, updatedAtEpochMillis)))
    }

    /** 資格情報を削除したデバイスの状態を消す。 */
    @Synchronized
    fun remove(uuid: String) {
        val states = loadAll()
        if (uuid in states) saveAll(states - uuid)
    }

    private fun loadAll(): Map<String, SesameStatusSnapshot> {
        val json = keyValueStore.getString(KEY_LOCK_STATES) ?: return emptyMap()
        val root =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (
                @Suppress("SwallowedException") e: SerializationException,
            ) {
                null
            }
        // 壊れた保存値・欠けた項目は「未取得」と同じ扱いにする（次の操作・状態取得で上書きされる）。
        return root.orEmpty().mapNotNull { (uuid, value) ->
            val entry = value as? JsonObject
            val isLocked = (entry?.get(FIELD_IS_LOCKED) as? JsonPrimitive)?.booleanOrNull
            val updatedAt = (entry?.get(FIELD_UPDATED_AT) as? JsonPrimitive)?.longOrNull
            if (isLocked != null && updatedAt != null) uuid to SesameStatusSnapshot(isLocked, updatedAt) else null
        }.toMap()
    }

    private fun saveAll(states: Map<String, SesameStatusSnapshot>) {
        val json =
            JsonObject(
                states.mapValues { (_, snapshot) ->
                    buildJsonObject {
                        put(FIELD_IS_LOCKED, snapshot.isLocked)
                        put(FIELD_UPDATED_AT, snapshot.updatedAtEpochMillis)
                    }
                },
            )
        keyValueStore.putString(KEY_LOCK_STATES, json.toString())
    }
}

private const val KEY_LOCK_STATES = "lock_states"
private const val FIELD_IS_LOCKED = "isLocked"
private const val FIELD_UPDATED_AT = "updatedAtEpochMillis"

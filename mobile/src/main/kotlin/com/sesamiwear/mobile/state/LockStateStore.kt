package com.sesamiwear.mobile.state

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
 * 排他はインスタンス単位ではなくクラス単位（[LOCK]）で行う。「全デバイス」の操作では
 * `SesameMessageListenerService`がデバイスごとに別コルーチンで本クラスを生成するため、
 * インスタンス単位の`@Synchronized`では単一キー（`lock_states`）へのread-modify-writeが
 * 後勝ちとなり、一部デバイスの更新が失われていた（BL-157）。
 */
class LockStateStore(private val keyValueStore: SesameKeyValueStore) {
    /** [uuid]の保存済み状態。一度も取得・操作していなければnull。 */
    fun load(uuid: String): SesameStatusSnapshot? = synchronized(LOCK) { loadAll()[uuid] }

    /**
     * [uuid]の施錠状態を保存する。[measurement]で分かった電池残量・角度・経路も同時に反映し、
     * 分からなかった項目は前回の値を残す（BL-166）。Web API経由の施錠/解錠は状態を返さないため、
     * 電池残量・角度は前回の値のまま経路だけが更新される。
     */
    fun save(
        uuid: String,
        isLocked: Boolean,
        updatedAtEpochMillis: Long,
        measurement: SesameStatusMeasurement = SesameStatusMeasurement(),
    ) = synchronized(LOCK) {
        val states = loadAll()
        // 成功したので直近の失敗は消す（BL-140）。電池残量・角度・経路は引き継ぐ。
        val updated =
            (states[uuid] ?: SesameStatusSnapshot(isLocked = null, updatedAtEpochMillis = null))
                .copy(isLocked = isLocked, updatedAtEpochMillis = updatedAtEpochMillis, lastFailure = null)
                .merge(measurement)
        saveAll(states + (uuid to updated))
    }

    /**
     * 直近の取得・操作が[failure]で失敗したことを記録する（BL-140）。
     * 最後に分かった施錠状態と、その取得時刻はそのまま残す（BL-142の「最後に分かった状態を
     * 出し続ける」設計に合わせる）。一度も取得できていないデバイスでは失敗だけを保存する。
     */
    fun saveFailure(
        uuid: String,
        failure: SesameStatusFailure,
    ) = synchronized(LOCK) {
        val states = loadAll()
        val previous = states[uuid]
        saveAll(
            states +
                (
                    uuid to
                        SesameStatusSnapshot(
                            isLocked = previous?.isLocked,
                            updatedAtEpochMillis = previous?.updatedAtEpochMillis,
                            lastFailure = failure,
                            // 電池残量・角度・経路は最後に分かった値を残す（BL-166）。
                            batteryPercentage = previous?.batteryPercentage,
                            position = previous?.position,
                            lastRoute = previous?.lastRoute,
                        )
                ),
        )
    }

    /** 資格情報を削除したデバイスの状態を消す。 */
    fun remove(uuid: String) =
        synchronized(LOCK) {
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
            val failure =
                SesameStatusFailure.ofNameOrNull((entry?.get(FIELD_LAST_FAILURE) as? JsonPrimitive)?.contentOrNull)
            // 電池残量・角度・経路は後から追加した項目（BL-166）。古い保存値にはキーが無くnullになる。
            val battery = (entry?.get(FIELD_BATTERY_PERCENTAGE) as? JsonPrimitive)?.intOrNull
            val position = (entry?.get(FIELD_POSITION) as? JsonPrimitive)?.intOrNull
            val route = SesameStatusRoute.ofNameOrNull((entry?.get(FIELD_LAST_ROUTE) as? JsonPrimitive)?.contentOrNull)
            val hasLockState = isLocked != null && updatedAt != null
            if (!hasLockState && failure == null) {
                null
            } else {
                uuid to
                    SesameStatusSnapshot(
                        isLocked = if (hasLockState) isLocked else null,
                        updatedAtEpochMillis = if (hasLockState) updatedAt else null,
                        lastFailure = failure,
                        batteryPercentage = battery,
                        position = position,
                        lastRoute = route,
                    )
            }
        }.toMap()
    }

    private fun saveAll(states: Map<String, SesameStatusSnapshot>) {
        val json =
            JsonObject(
                states.mapValues { (_, snapshot) ->
                    buildJsonObject {
                        snapshot.isLocked?.let { put(FIELD_IS_LOCKED, it) }
                        snapshot.updatedAtEpochMillis?.let { put(FIELD_UPDATED_AT, it) }
                        snapshot.lastFailure?.let { put(FIELD_LAST_FAILURE, it.name) }
                        snapshot.batteryPercentage?.let { put(FIELD_BATTERY_PERCENTAGE, it) }
                        snapshot.position?.let { put(FIELD_POSITION, it) }
                        snapshot.lastRoute?.let { put(FIELD_LAST_ROUTE, it.name) }
                    }
                },
            )
        keyValueStore.putString(KEY_LOCK_STATES, json.toString())
    }
}

/**
 * プロセス内の全[LockStateStore]インスタンスで共有する排他ロック（BL-157）。
 * 保存先が同一プロセス内のSharedPreferences1ファイルのみのため、プロセス内の排他で足りる。
 */
private val LOCK = Any()

private const val KEY_LOCK_STATES = "lock_states"
private const val FIELD_IS_LOCKED = "isLocked"
private const val FIELD_UPDATED_AT = "updatedAtEpochMillis"
private const val FIELD_LAST_FAILURE = "lastFailure"
private const val FIELD_BATTERY_PERCENTAGE = "batteryPercentage"
private const val FIELD_POSITION = "position"
private const val FIELD_LAST_ROUTE = "lastRoute"

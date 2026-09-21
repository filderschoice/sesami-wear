package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameKeyValueStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 一度見つけたSesameのBLEアドレスをuuidごとに覚えておく（BL-189）。
 *
 * 2026-09-20の実機検証（BL-165）で、BLE1往復の実測が2.0〜3.5秒あり、経路選択に与えていた上限
 * （合計1,800ms）では**毎回打ち切られてインターネット経由へ倒れていた**ことが判明した。
 * 内訳のうち支配的なのは探索（スキャン）で、接続以降は短い。そこで
 * **探索で見つけたアドレスを保存し、次回からは探索を飛ばして直接接続する**。
 *
 * アドレスの発見は、Web APIの通信と並行して走る到達確認（[SesameBleReachability.shouldProbe]）が
 * 担う。利用者を待たせる経路上では探索を行わないため、上限を広げずに成功率を上げられる。
 *
 * 記録は2段構えで持つ（BL-193）。
 *
 * - [load]（現用）: 直接接続に使うアドレス。直接接続に失敗すると[remove]で捨てられ、
 *   次の操作では探索からやり直す。古いアドレスへ毎回接続を試みて利用者を待たせないため。
 * - [loadLastKnown]（最後に成功したアドレス）: [remove]でも消さない。到達確認の探索が外れたときに
 *   「直接つながるか」を確かめる予備として使う（[SesameBleConnector.probeReachable]）。
 *
 * 2段構えにしたのは、2026-09-21のBL-191の実機検証で、**保存済みアドレスへの直接接続は成功する一方で
 * 到達確認の探索は3回とも当たらない**と分かったため。1段だけだと、直接接続が一度失敗して記録が
 * 消えた時点で「アドレス無し → 探索が当たらない → 到達実績が付かない → BLEを試さない」から
 * 戻れなくなっていた。予備のアドレスが古いままでも、探索が当たれば[save]が両方を上書きするため
 * 自然に直る（捨てる契機を別に設ける必要はない）。
 *
 * **アドレスはログへ出さない**（端末を識別しうる値のため。`SesameBleDebugReceiver`のKDocと同じ方針）。
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class SesameBleAddressCache(private val store: SesameKeyValueStore) {
    /** [uuid]のデバイスの、直接接続に使うBLEアドレス。無ければnull。 */
    fun load(uuid: String): String? = entries(KEY_ADDRESSES)[uuid]

    /**
     * [uuid]のデバイスの、最後に接続できたBLEアドレス（BL-193）。
     * [remove]で現用の記録を捨てたあとも残るため、到達確認の予備として使える。
     */
    fun loadLastKnown(uuid: String): String? = load(uuid) ?: entries(KEY_LAST_ADDRESSES)[uuid]

    /** 接続できたアドレスを、現用と「最後に成功したアドレス」の両方へ記録する。 */
    fun save(
        uuid: String,
        address: String,
    ) {
        if (address.isBlank()) return
        put(KEY_ADDRESSES, uuid, address)
        put(KEY_LAST_ADDRESSES, uuid, address)
    }

    /**
     * 使えなくなったアドレスの記録を捨てる。捨てるのは現用の記録だけで、
     * 「最後に成功したアドレス」は残す（BL-193）。
     */
    fun remove(uuid: String) {
        val entries = entries(KEY_ADDRESSES)
        if (uuid in entries) write(KEY_ADDRESSES, entries - uuid)
    }

    private fun put(
        key: String,
        uuid: String,
        address: String,
    ) {
        val entries = entries(key)
        if (entries[uuid] == address) return
        write(key, entries + (uuid to address))
    }

    private fun entries(key: String): Map<String, String> {
        val json = store.getString(key) ?: return emptyMap()
        val root =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (
                @Suppress("SwallowedException") e: SerializationException,
            ) {
                null
            }
        // 壊れた保存値は「記録なし」と同じ扱いにする（次の到達確認で書き直される）。
        return root.orEmpty().mapNotNull { (uuid, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { uuid to it }
        }.toMap()
    }

    private fun write(
        key: String,
        entries: Map<String, String>,
    ) {
        val json = JsonObject(entries.mapValues { (_, address) -> JsonPrimitive(address) })
        store.putString(key, json.toString())
    }

    private companion object {
        const val KEY_ADDRESSES = "ble_addresses"
        const val KEY_LAST_ADDRESSES = "ble_last_addresses"
    }
}

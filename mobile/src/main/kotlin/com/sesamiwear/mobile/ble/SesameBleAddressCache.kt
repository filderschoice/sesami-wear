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
 * 保存値が古い（デバイスのアドレスが変わった・別の場所にいる）場合は直接接続が失敗するので、
 * そのときは記録を捨てて次の到達確認で取り直す。**アドレスはログへ出さない**
 * （端末を識別しうる値のため。`SesameBleDebugReceiver`のKDocと同じ方針）。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class SesameBleAddressCache(private val store: SesameKeyValueStore) {
    /** [uuid]のデバイスの、最後に接続できたBLEアドレス。無ければnull。 */
    fun load(uuid: String): String? = entries()[uuid]

    /** 接続できたアドレスを記録する。 */
    fun save(
        uuid: String,
        address: String,
    ) {
        if (address.isBlank()) return
        val entries = entries()
        if (entries[uuid] == address) return
        write(entries + (uuid to address))
    }

    /** 使えなくなったアドレスの記録を捨てる。 */
    fun remove(uuid: String) {
        val entries = entries()
        if (uuid in entries) write(entries - uuid)
    }

    private fun entries(): Map<String, String> {
        val json = store.getString(KEY_ADDRESSES) ?: return emptyMap()
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

    private fun write(entries: Map<String, String>) {
        val json = JsonObject(entries.mapValues { (_, address) -> JsonPrimitive(address) })
        store.putString(KEY_ADDRESSES, json.toString())
    }

    private companion object {
        const val KEY_ADDRESSES = "ble_addresses"
    }
}

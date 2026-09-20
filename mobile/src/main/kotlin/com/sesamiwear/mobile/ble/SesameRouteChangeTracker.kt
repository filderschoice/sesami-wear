package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.SesameStatusRoute
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * デバイスごとに「前回どの経路で動いたか」を覚え、**変わったときだけ**利用者へ知らせる（BL-190）。
 *
 * 経路の切り替わりは、BL-168のトーストで毎回伝える設計だった。しかし
 * (1) 2026-09-20の実機検証で、背景から出したトーストは通知が無効な端末では抑止されること、
 * (2) 圏外に居る間は操作のたびに同じ知らせが出て煩わしいこと、の2点が分かったため、
 * 通知へ変えたうえで**変化の瞬間だけ**に絞る。
 *
 * 最初の1回（記録が無い状態）は知らせない。比較対象が無く、利用者にとっては
 * 「切り替わった」ではなく「そういうものとして始まった」ためである。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class SesameRouteChangeTracker(private val store: SesameKeyValueStore) {
    /**
     * [uuid]のデバイスを[route]で操作したことを記録し、**前回から変わっていればtrue**を返す。
     * 記録が無い場合はfalse（初回は知らせない）。
     */
    fun onRouteUsed(
        uuid: String,
        route: SesameStatusRoute,
    ): Boolean {
        val entries = entries()
        val previous = entries[uuid]
        if (previous != route.name) write(entries + (uuid to route.name))
        return previous != null && previous != route.name
    }

    /** 資格情報を削除したデバイスの記録を消す。 */
    fun remove(uuid: String) {
        val entries = entries()
        if (uuid in entries) write(entries - uuid)
    }

    private fun entries(): Map<String, String> {
        val json = store.getString(KEY_LAST_ROUTE) ?: return emptyMap()
        val root =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (
                @Suppress("SwallowedException") e: SerializationException,
            ) {
                null
            }
        // 壊れた保存値は「記録なし」と同じ扱いにする（次の操作が初回と同じになるだけ）。
        return root.orEmpty().mapNotNull { (uuid, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { uuid to it }
        }.toMap()
    }

    private fun write(entries: Map<String, String>) {
        val json = JsonObject(entries.mapValues { (_, route) -> JsonPrimitive(route) })
        store.putString(KEY_LAST_ROUTE, json.toString())
    }

    private companion object {
        const val KEY_LAST_ROUTE = "last_notified_route"
    }
}

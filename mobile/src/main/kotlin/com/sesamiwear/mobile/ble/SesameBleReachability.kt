package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.display.SesameBleConnectionLabel.State
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * デバイスごとの「最後にBLEで到達できた時刻」を保存し、BLEを先に試すかどうかを決める（BL-152）。
 *
 * 実行時間の制限（BL-137、ウィジェットのタップは約10秒で打ち切られる）があるため、
 * 圏外のデバイスへ毎回BLEスキャンを仕掛けると、そのぶんWeb APIへ倒れるまでの時間が伸びる。
 * そこで**直近に到達できた実績があるときだけBLEを先に試す**。実績が無ければWeb APIから始め、
 * その通信と並行して短いスキャン（[shouldProbe]）で実績を取り直す。
 *
 * uuidと時刻しか持たないため、保存先は非暗号化のSharedPreferencesでよい（[LockStateStore]と同方針）。
 * 全デバイス分を1つのJSONオブジェクトにして単一キーへ保存する。
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 *
 * 既定値の根拠:
 * - [REACHABLE_TTL_MILLIS]（30分）: 自宅にいる間は圏内であり続ける一方、外出後にいつまでもBLEを
 *   先に試すと毎回の操作が遅くなる。外出しても最初の1回だけ無駄になる長さとして30分を選んだ。
 * - [PROBE_INTERVAL_MILLIS]（5分）: 帰宅してからBLE経路が復活するまでの遅れの上限。
 *   スキャンはWeb APIの通信と並行して行うため、利用者から見た所要時間はほぼ変わらない。
 *   当初は15分だったが、2026-09-20の実機検証（BL-165）で到達確認自体がしばしば失敗すると
 *   分かったため、失敗しても取り返しやすいよう5分へ縮めた（BL-189）。
 *   スキャンは操作の契機でしか行わないため、常駐の電力消費は増えない。
 */
class SesameBleReachability(
    private val store: SesameKeyValueStore = NoOpKeyValueStore,
    private val reachableTtlMillis: Long = REACHABLE_TTL_MILLIS,
    private val probeIntervalMillis: Long = PROBE_INTERVAL_MILLIS,
) {
    /** [uuid]へBLEを先に試すべきか。直近[reachableTtlMillis]以内に到達できていればtrue。 */
    fun preferBle(
        uuid: String,
        nowMillis: Long,
    ): Boolean {
        val reachableAt = load()[uuid]?.reachableAtEpochMillis ?: return false
        return nowMillis - reachableAt in 0..reachableTtlMillis
    }

    /**
     * BLEでの操作を試した結果を記録する。
     * 到達できた場合は時刻を更新し、できなかった場合は実績を消して次回はWeb APIから始めさせる。
     */
    fun record(
        uuid: String,
        nowMillis: Long,
        reachable: Boolean,
    ) {
        val entries = load()
        val previous = entries[uuid]
        save(
            entries +
                (
                    uuid to
                        Entry(
                            reachableAtEpochMillis = if (reachable) nowMillis else null,
                            probedAtEpochMillis = previous?.probedAtEpochMillis,
                        )
                ),
        )
    }

    /**
     * Web APIの通信と並行して到達確認のスキャンを行うべきか。
     * BLEを先に試す状態ならスキャンは不要で、前回の確認から[probeIntervalMillis]未満でも行わない。
     */
    fun shouldProbe(
        uuid: String,
        nowMillis: Long,
    ): Boolean {
        if (preferBle(uuid, nowMillis)) return false
        val probedAt = load()[uuid]?.probedAtEpochMillis
        return probedAt == null || nowMillis - probedAt !in 0..probeIntervalMillis
    }

    /** 到達確認のスキャンの結果を記録する。確認した時刻は成否によらず残す。 */
    fun recordProbe(
        uuid: String,
        nowMillis: Long,
        reachable: Boolean,
    ) {
        val entries = load()
        save(
            entries +
                (
                    uuid to
                        Entry(
                            reachableAtEpochMillis = if (reachable) nowMillis else null,
                            probedAtEpochMillis = nowMillis,
                        )
                ),
        )
    }

    /** 資格情報を削除したデバイスの記録を消す。 */
    fun remove(uuid: String) {
        val entries = load()
        if (uuid in entries) save(entries - uuid)
    }

    /**
     * 画面へ出すための到達状況（BL-190）。
     *
     * 経路選択の内部状態を利用者から見えるようにするためのもので、判定は[preferBle]と同じ基準を使う
     * （表示と実際の挙動を食い違わせないため）。確認した実績そのものが無い場合は[State.UNKNOWN]で、
     * 「届かなかった」とは書き分ける。
     */
    fun status(
        uuid: String,
        nowMillis: Long,
    ): Status {
        val entry = load()[uuid]
        return when {
            preferBle(uuid, nowMillis) -> Status(State.IN_RANGE, entry?.reachableAtEpochMillis)
            entry?.probedAtEpochMillis != null -> Status(State.OUT_OF_RANGE, entry.probedAtEpochMillis)
            else -> Status(State.UNKNOWN, null)
        }
    }

    /** [status]の結果。 */
    data class Status(
        val state: State,
        /** 最後に到達確認を行った時刻。[State.UNKNOWN]ではnull。 */
        val lastCheckedAtEpochMillis: Long?,
    )

    private data class Entry(
        val reachableAtEpochMillis: Long?,
        val probedAtEpochMillis: Long?,
    )

    private fun load(): Map<String, Entry> {
        val json = store.getString(KEY_REACHABILITY) ?: return emptyMap()
        val root =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (
                @Suppress("SwallowedException") e: SerializationException,
            ) {
                null
            }
        // 壊れた保存値は「実績なし」と同じ扱いにする（次の操作で書き直される）。
        return root.orEmpty().mapNotNull { (uuid, value) ->
            val entry = value as? JsonObject
            val reachableAt = (entry?.get(FIELD_REACHABLE_AT) as? JsonPrimitive)?.longOrNull
            val probedAt = (entry?.get(FIELD_PROBED_AT) as? JsonPrimitive)?.longOrNull
            if (reachableAt == null && probedAt == null) null else uuid to Entry(reachableAt, probedAt)
        }.toMap()
    }

    private fun save(entries: Map<String, Entry>) {
        val json =
            JsonObject(
                entries.mapValues { (_, entry) ->
                    buildJsonObject {
                        entry.reachableAtEpochMillis?.let { put(FIELD_REACHABLE_AT, it) }
                        entry.probedAtEpochMillis?.let { put(FIELD_PROBED_AT, it) }
                    }
                },
            )
        store.putString(KEY_REACHABILITY, json.toString())
    }

    companion object {
        /** 到達実績を「有効」とみなす長さ（ミリ秒）。 */
        const val REACHABLE_TTL_MILLIS = 30L * 60 * 1000

        /** 到達確認のスキャンを行う間隔の下限（ミリ秒）。 */
        const val PROBE_INTERVAL_MILLIS = 5L * 60 * 1000

        private const val KEY_REACHABILITY = "ble_reachability"
        private const val FIELD_REACHABLE_AT = "reachableAtEpochMillis"
        private const val FIELD_PROBED_AT = "probedAtEpochMillis"
    }
}

/**
 * 何も保存しない[SesameKeyValueStore]。BLE経路を配線していない呼び出し元（ユニットテストや、
 * BLEを使わない構成）でも[SesameBleReachability]がそのまま使えるようにするための既定値で、
 * 到達実績が残らないため常にWeb APIから始まる。
 */
private object NoOpKeyValueStore : SesameKeyValueStore {
    override fun putString(
        key: String,
        value: String,
    ) = Unit

    override fun getString(key: String): String? = null

    override fun clear() = Unit
}

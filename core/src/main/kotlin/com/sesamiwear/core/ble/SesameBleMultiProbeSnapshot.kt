package com.sesamiwear.core.ble

import com.sesamiwear.core.SesameDeviceSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「まとめて接続確認」（BL-280）のある時点の進捗。登録した全デバイスぶんの[SesameBleProbeSnapshot]を、
 * 名前つきで並べたもの。スマホの画面の描画と、ウォッチへ返す進捗メッセージ
 * （[encode]、`SesameWearProtocol.PATH_BLE_PROBE_PROGRESS_ALL`）に使う。機密情報は含まない。
 *
 * 並びは登録順のまま保つ（接続確認を1台ずつ順番に行うため、画面でも進む順が分かるように）。
 */
@Serializable
data class SesameBleMultiProbeSnapshot(
    val entries: List<Entry>,
) {
    /** 1台ぶん。[name]は表示名（空なら呼び出し側がuuidの代わりの表示を決める）。 */
    @Serializable
    data class Entry(
        val uuid: String,
        val name: String,
        val snapshot: SesameBleProbeSnapshot,
    ) {
        /**
         * ウォッチの1行に収める状態の文言（「発見・約3m」「接続OK・電池100%」）。段階の短縮名に、
         * [SesameBleProbeSnapshot.compactLines]の2行目を「・」でつなぐ。
         */
        fun statusLine(): String {
            val (first, second) = snapshot.compactLines()
            return if (second == null) first else "$first・$second"
        }
    }

    /** [uuid]（大文字小文字は区別しない）の1台だけを[snapshot]へ差し替えた進捗。該当が無ければそのまま返す。 */
    fun withSnapshot(
        uuid: String,
        snapshot: SesameBleProbeSnapshot,
    ): SesameBleMultiProbeSnapshot =
        copy(
            entries = entries.map { if (it.uuid.equals(uuid, ignoreCase = true)) it.copy(snapshot = snapshot) else it },
        )

    /** ウォッチへ送るバイト列（JSON）。 */
    fun encode(): ByteArray = JSON.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /** [devices]の全台を「探索中」にした開始時点の進捗。 */
        fun initial(devices: List<SesameDeviceSummary>): SesameBleMultiProbeSnapshot =
            SesameBleMultiProbeSnapshot(
                devices.map { Entry(it.uuid, it.displayName, SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING)) },
            )

        /** [encode]の逆。壊れたペイロードはnullを返す（新旧のバージョン混在で落ちない）。 */
        fun decode(payload: ByteArray): SesameBleMultiProbeSnapshot? =
            try {
                JSON.decodeFromString(serializer(), String(payload, Charsets.UTF_8))
            } catch (
                @Suppress("SwallowedException") e: IllegalArgumentException,
            ) {
                null
            }
    }
}

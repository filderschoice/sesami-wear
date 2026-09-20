package com.sesamiwear.core.diagnostics

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.display.SesameRouteLabel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 診断ログの保存と整形（BL-188）。
 *
 * 通信エラーなどが起きたときに、利用者から内容を連携してもらって解析するためのもの。
 * 施錠・解錠・状態取得の**成功と失敗の両方**を新しい順に[MAX_ENTRIES]件だけ保持する
 * （2026-09-20のユーザー判断。失敗だけだと「そのとき何が起きていたか」の前後関係が分からないため）。
 *
 * **保存する値に資格情報は含まれない**（[SesameDiagnosticsEntry]のKDoc）。そのため保存先は
 * 非暗号化の[SesameKeyValueStore]でよい（`LockStateStore`と同方針）。
 *
 * Android非依存のためユニットテスト対象。時刻の整形だけ[ZoneId]を受け取る。
 */
class SesameDiagnosticsLog(
    private val keyValueStore: SesameKeyValueStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    /** 1件追記する。[MAX_ENTRIES]を超えた古い分は捨てる。 */
    @Synchronized
    fun record(entry: SesameDiagnosticsEntry) {
        val updated = (listOf(entry) + load()).take(MAX_ENTRIES)
        keyValueStore.putString(KEY_ENTRIES, Json.encodeToString(updated))
    }

    /** 新しい順の一覧。保存値が壊れていた場合は空を返す（表示のための機能で、失敗させる意味が無いため）。 */
    @Synchronized
    fun load(): List<SesameDiagnosticsEntry> {
        val json = keyValueStore.getString(KEY_ENTRIES) ?: return emptyList()
        return try {
            Json.decodeFromString<List<SesameDiagnosticsEntry>>(json)
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            emptyList()
        }
    }

    /** すべて消す。 */
    @Synchronized
    fun clear() {
        keyValueStore.putString(KEY_ENTRIES, Json.encodeToString(emptyList<SesameDiagnosticsEntry>()))
    }

    /** 1件を画面へ出す1行にする（「09/20 14:35 玄関 解錠 インターネット 成功」）。 */
    fun format(entry: SesameDiagnosticsEntry): String =
        listOfNotNull(
            TIME_FORMATTER.format(Instant.ofEpochMilli(entry.epochMillis).atZone(zoneId)),
            entry.deviceName,
            entry.operation.label,
            entry.route?.let { SesameRouteLabel.name(it) },
            // 理由は結果に続けて書く（間に空白を入れると「失敗 （認証エラー）」と離れて読みにくい）。
            entry.outcome.label + entry.reason?.let { "（$it）" }.orEmpty(),
        ).joinToString(" ")

    /**
     * 連携用の全文（[header]の各行に続けて、新しい順の全件）。
     *
     * [header]にはアプリのバージョンやAndroidのバージョンなど、解析に要る環境の情報を渡す
     * （組み立てはAndroid側。ここは受け取った行をそのまま先頭へ置くだけ）。
     */
    fun formatAll(header: List<String> = emptyList()): String {
        val entries = load()
        val body = if (entries.isEmpty()) listOf(EMPTY_MESSAGE) else entries.map { format(it) }
        return (header + body).joinToString("\n")
    }

    companion object {
        /** 保持する件数（2026-09-20のユーザー判断）。 */
        const val MAX_ENTRIES = 50

        /** 1件も無いときに画面と連携用の全文へ出す文言。 */
        const val EMPTY_MESSAGE = "記録はまだありません"

        private const val KEY_ENTRIES = "diagnostics_entries"

        /** 年は出さない（50件しか持たず、年をまたぐ範囲にならないため）。 */
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")
    }
}

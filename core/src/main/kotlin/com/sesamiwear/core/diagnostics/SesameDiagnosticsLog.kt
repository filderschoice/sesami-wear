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
 * あわせて、`Log.w`へ出している通信の詳細（[SesameDiagnosticsDetail]）を[MAX_DETAILS]行まで保持する（BL-302）。
 *
 * **保存する値に資格情報は含まれない**（[SesameDiagnosticsEntry] / [SesameDiagnosticsDetail]のKDoc）。
 * そのため保存先は非暗号化の[SesameKeyValueStore]でよい（`LockStateStore`と同方針）。
 *
 * Android非依存のためユニットテスト対象。時刻の整形だけ[ZoneId]を受け取る。
 */
class SesameDiagnosticsLog(
    private val keyValueStore: SesameKeyValueStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    /** 1件追記する。[MAX_ENTRIES]を超えた古い分は捨てる。 */
    fun record(entry: SesameDiagnosticsEntry) =
        synchronized(LOCK) {
            val updated = (listOf(entry) + load()).take(MAX_ENTRIES)
            keyValueStore.putString(KEY_ENTRIES, Json.encodeToString(updated))
        }

    /** 新しい順の一覧。保存値が壊れていた場合は空を返す（表示のための機能で、失敗させる意味が無いため）。 */
    fun load(): List<SesameDiagnosticsEntry> = synchronized(LOCK) { decodeOrEmpty(KEY_ENTRIES) }

    /**
     * 「通信の詳細」を1行追記する（BL-302）。[message]は[SesameDiagnosticsDetail.sanitize]を通してから保存し、
     * [MAX_DETAILS]を超えた古い行は捨てる。伏せた結果が空になった行は残さない。
     */
    fun recordDetail(
        epochMillis: Long,
        message: String,
    ) = synchronized(LOCK) {
        val sanitized = SesameDiagnosticsDetail.sanitize(message)
        if (sanitized.isNotEmpty()) {
            val detail = SesameDiagnosticsDetail(epochMillis, sanitized)
            val updated = (listOf(detail) + loadDetails()).take(MAX_DETAILS)
            keyValueStore.putString(KEY_DETAILS, Json.encodeToString(updated))
        }
    }

    /** 「通信の詳細」の新しい順の一覧。保存値が無い・壊れていた場合は空を返す。 */
    fun loadDetails(): List<SesameDiagnosticsDetail> = synchronized(LOCK) { decodeOrEmpty(KEY_DETAILS) }

    /** すべて消す（「通信の詳細」も含む）。 */
    fun clear() =
        synchronized(LOCK) {
            keyValueStore.putString(KEY_ENTRIES, Json.encodeToString(emptyList<SesameDiagnosticsEntry>()))
            keyValueStore.putString(KEY_DETAILS, Json.encodeToString(emptyList<SesameDiagnosticsDetail>()))
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

    /** 「通信の詳細」1行を画面へ出す形にする（「09/20 14:35:00 unlock accepted: HTTP 200 body=2B」）。 */
    fun formatDetail(detail: SesameDiagnosticsDetail): String =
        TIME_FORMATTER.format(Instant.ofEpochMilli(detail.epochMillis).atZone(zoneId)) + " " + detail.message

    /**
     * 連携用の全文（[header]の各行に続けて、新しい順の全件。「通信の詳細」があれば[DETAILS_HEADING]に続けて置く）。
     *
     * [header]にはアプリのバージョンやAndroidのバージョンなど、解析に要る環境の情報を渡す
     * （組み立てはAndroid側。ここは受け取った行をそのまま先頭へ置くだけ）。
     */
    fun formatAll(header: List<String> = emptyList()): String {
        val entries = load()
        val body = if (entries.isEmpty()) listOf(EMPTY_MESSAGE) else entries.map { format(it) }
        val details = loadDetails()
        val detailLines = if (details.isEmpty()) emptyList() else listOf(DETAILS_HEADING) + details.map(::formatDetail)
        return (header + body + detailLines).joinToString("\n")
    }

    private inline fun <reified T> decodeOrEmpty(key: String): List<T> {
        val json = keyValueStore.getString(key) ?: return emptyList()
        return try {
            Json.decodeFromString<List<T>>(json)
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            emptyList()
        }
    }

    companion object {
        /** 保持する件数（2026-09-20のユーザー判断）。 */
        const val MAX_ENTRIES = 50

        /** 「通信の詳細」を保持する行数（BL-302）。施錠・解錠1回で数行出るため、[MAX_ENTRIES]より多めに持つ。 */
        const val MAX_DETAILS = 100

        /** 画面と連携用の全文で「通信の詳細」の前に置く見出し。 */
        const val DETAILS_HEADING = "---- 通信の詳細（新しい順）"

        /** 1件も無いときに画面と連携用の全文へ出す文言。 */
        const val EMPTY_MESSAGE = "記録はまだありません"

        private const val KEY_ENTRIES = "diagnostics_entries"
        private const val KEY_DETAILS = "diagnostics_details"

        // 同じ保存先を指す別インスタンスが複数できる（画面・実行口ごとに生成する）ため、インスタンスではなく
        // プロセス全体で1つのロックを使う（読み込みから書き込みまでの間に、他方の追記が消えないように）。
        private val LOCK = Any()

        /** 年は出さない（50件しか持たず、年をまたぐ範囲にならないため）。 */
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")
    }
}

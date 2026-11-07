package com.sesamiwear.mobile.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 保持した接続（BL-279）の入れ物。キーごとに1つだけ持ち、[ttlMillis]を過ぎたら自動で閉じる。
 *
 * **所有権を渡す方式**: 使う側は[take]で取り出して（入れ物からは消え、時間切れのタイマーも止まる）使い、
 * 成功したら[retain]で戻す（時間切れを数え直す）。取り出している間は他の処理が同じ接続を使えないため、
 * ウィジェットとウォッチの操作が同時に来ても、1本の接続へ同時に書き込まない。
 * 戻さなければ、使う側が閉じる（失敗した接続を残さない）。
 *
 * Android非依存（[AutoCloseable]とコルーチンだけ）のため、仮想時間の単体テストで検証できる。
 */
class HeldSessionStore<S : AutoCloseable>(
    private val scope: CoroutineScope,
    private val ttlMillis: Long,
) {
    private class Entry<S>(
        val value: S,
        val expiry: Job,
    )

    private val lock = Any()
    private val entries = HashMap<String, Entry<S>>()

    /** [value]を[key]で保持する。同じキーに前のものがあれば閉じる。[ttlMillis]後に自動で閉じる。 */
    fun retain(
        key: String,
        value: S,
    ) {
        val replaced =
            synchronized(lock) {
                val previous = entries.remove(key)
                previous?.expiry?.cancel()
                entries[key] = Entry(value, scope.launch { expireAfterTtl(key, value) })
                previous?.value?.takeIf { it !== value }
            }
        replaced?.close()
    }

    /** [key]の保持を取り出す。無ければnull。取り出したら入れ物からは消え、時間切れも止まる。 */
    fun take(key: String): S? =
        synchronized(lock) {
            entries.remove(key)?.also { it.expiry.cancel() }?.value
        }

    /** [key]を保持しているか。 */
    fun has(key: String): Boolean = synchronized(lock) { entries.containsKey(key) }

    /** 保持している全部を閉じる。 */
    fun releaseAll() {
        val all =
            synchronized(lock) {
                entries.values.toList().also { list ->
                    list.forEach { it.expiry.cancel() }
                    entries.clear()
                }
            }
        all.forEach { it.value.close() }
    }

    private suspend fun expireAfterTtl(
        key: String,
        value: S,
    ) {
        delay(ttlMillis)
        val expired =
            synchronized(lock) {
                entries[key]?.takeIf { it.value === value }?.also { entries.remove(key) }
            }
        expired?.value?.close()
    }
}

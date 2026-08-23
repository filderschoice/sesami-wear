package com.sesamiwear.mobile.messaging

/**
 * 同一デバイス（キー）への短時間内の重複コマンドを検知する（BL-062）。
 * Tileタップの連打によりSesame APIコマンドが繰り返し送信され、結果としてハプティクスが
 * 連続再生される不具合への対策。時刻取得を注入可能にしてAndroid非依存でテストできる。
 */
class CommandDebouncer(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lastAcceptedAtMillis = mutableMapOf<String, Long>()

    /**
     * [key]（デバイスuuid想定）のコマンドを処理してよければtrueを返し、直近の受理時刻を更新する。
     * 直近の受理から[windowMillis]未満しか経過していない場合はfalseを返し、状態は更新しない。
     */
    @Synchronized
    fun shouldProcess(key: String): Boolean {
        val now = nowMillis()
        val last = lastAcceptedAtMillis[key]
        if (last != null && now - last < windowMillis) return false
        lastAcceptedAtMillis[key] = now
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 2000L
    }
}

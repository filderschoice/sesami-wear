package com.sesamiwear.mobile.showcase

/**
 * 掲載用の撮影のために、撮影モードの目印（題名の横の「📷 撮影モード」と⋮メニューの項目）を
 * しばらく隠す判定（BL-249）。**デバッグ版にだけ含まれる。**
 *
 * 目印を隠すとメニューから撮影モードの操作画面を開けなくなり、戻す操作を画面に置けない。そこで
 * 隠す時間を[HIDE_MILLIS]に限り、過ぎれば自動で表示へ戻す（戻し忘れを起こさない）。
 * 端末の時計を戻した場合に長く隠れたままにならないよう、終了時刻が今から[HIDE_MILLIS]より先なら
 * 隠さない。Android非依存のためユニットテスト対象。
 */
object ShowcaseMarkHiding {
    /** 隠す時間（10分）。掲載用の数枚を撮るのに足りる長さ。 */
    const val HIDE_MILLIS = 10 * 60 * 1000L

    /** 今から隠したときの終了時刻。 */
    fun hiddenUntil(nowEpochMillis: Long): Long = nowEpochMillis + HIDE_MILLIS

    /** [hiddenUntilEpochMillis]（未設定は0）までの間なら隠す。 */
    fun isHidden(
        hiddenUntilEpochMillis: Long,
        nowEpochMillis: Long,
    ): Boolean = nowEpochMillis < hiddenUntilEpochMillis && hiddenUntilEpochMillis - nowEpochMillis <= HIDE_MILLIS
}

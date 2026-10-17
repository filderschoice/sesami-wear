package com.sesamiwear.mobile.shortcut

/**
 * ランチャーのアプリアイコン長押しメニュー（App Shortcuts）から実行できる操作（BL-239）。
 *
 * **解錠は載せない。** アイコンの長押しは誤操作が起きやすく、確認画面を挟まずに解錠できる導線を
 * 増やさないため（施錠はウィジェット・Tileでも確認なしで実行している）。
 * 対象は常に登録済みの全デバイス（ショートカットは1つの固定の項目で、デバイスを選べないため）。
 *
 * Intentのactionからの解釈と、実行後に出す短い文言を持つ。Android非依存のためユニットテスト対象。
 */
enum class AppShortcutAction(
    val intentAction: String,
    /** ショートカットのID。同じIDで登録し直すと上書きされる。 */
    val shortcutId: String,
    /** メニューに出す短い名前。 */
    val shortLabel: String,
    /** 幅に余裕があるときに出す名前。 */
    val longLabel: String,
) {
    /** 登録済みの全デバイスを施錠する。 */
    LOCK_ALL("com.sesamiwear.mobile.shortcut.LOCK_ALL", "lock_all", "全施錠", "すべてのSesameを施錠"),

    /** 登録済みの全デバイスの状態を取得しなおす。 */
    REFRESH_ALL("com.sesamiwear.mobile.shortcut.REFRESH_ALL", "refresh_all", "状態を更新", "すべてのSesameの状態を更新"),
    ;

    /** 実行を受け付けたときにトーストで出す文言。[deviceCount]が0なら何もしないことを伝える。 */
    fun acceptedMessage(deviceCount: Int): String =
        when {
            deviceCount == 0 -> NO_DEVICE_MESSAGE
            this == LOCK_ALL -> if (deviceCount == 1) "施錠しています" else "${deviceCount}台を施錠しています"
            else -> if (deviceCount == 1) "状態を取得しています" else "${deviceCount}台の状態を取得しています"
        }

    companion object {
        /** Sesameが1台も登録されていないときの文言。デモ用デバイスはショートカットの対象にしない。 */
        const val NO_DEVICE_MESSAGE = "Sesameが登録されていません。アプリで登録してください"

        /** Intentのactionを解釈する。知らない値（解錠を含む）はnull。 */
        fun fromIntentAction(action: String?): AppShortcutAction? = entries.find { it.intentAction == action }
    }
}

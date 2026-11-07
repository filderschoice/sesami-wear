package com.sesamiwear.core.ble

/**
 * 「Bluetooth接続を確認」の進み具合（BL-271）。スマホのレーダー画面とウォッチの小画面で
 * 同じ段階名を使う。
 *
 * @property label スマホの画面向けの文言。
 * @property shortLabel ウォッチの円形画面向けの文言。過去に7文字の文言がタイルの幅に収まらず
 * 末尾省略された（BL-102 / BL-104）ため、5文字以内に収める。
 * @property isTerminal これ以上進まない（確認が終わった）段階か。
 * @property isProblem 利用者の対処が要る失敗の段階か。
 */
enum class SesameBleProbeStage(
    val label: String,
    val shortLabel: String,
    val isTerminal: Boolean,
    val isProblem: Boolean,
) {
    /** 周囲をスキャンして探している。 */
    SCANNING("探索中", "探索中", isTerminal = false, isProblem = false),

    /** スキャンでSesameの電波を捉えた（接続はまだ）。 */
    FOUND("発見", "発見", isTerminal = false, isProblem = false),

    /** GATT接続を試みている。 */
    CONNECTING("接続中", "接続中", isTerminal = false, isProblem = false),

    /** 接続できたので、ログイン（鍵の確認）をしている。 */
    LOGGING_IN("ログイン中", "認証中", isTerminal = false, isProblem = false),

    /** ログインまで成功した（Bluetoothで操作できる状態）。 */
    CONNECTED("接続OK", "接続OK", isTerminal = true, isProblem = false),

    /** 制限時間内にSesameの電波を捉えられなかった（圏外、または他のアプリが接続中）。 */
    NOT_FOUND("圏外", "圏外", isTerminal = true, isProblem = true),

    /** 電波は捉えたが、接続またはログインに失敗した。 */
    FAILED("接続に失敗", "失敗", isTerminal = true, isProblem = true),

    /** Bluetoothの権限が無い。 */
    PERMISSION_DENIED("権限がありません", "権限なし", isTerminal = true, isProblem = true),

    /** 端末のBluetoothが無効。 */
    BLUETOOTH_OFF("Bluetoothがオフ", "BT:オフ", isTerminal = true, isProblem = true),
}

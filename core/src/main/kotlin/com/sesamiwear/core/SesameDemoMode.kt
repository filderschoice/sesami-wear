package com.sesamiwear.core

import com.sesamiwear.core.api.SesameCommand

/**
 * 資格情報（apikey / secretKey / uuid）が未登録の状態でも、ダミーの施錠状態でTile・Complication・
 * デバイス選択画面を一通り操作できるようにするデモモードの定義（BL-109）。
 *
 * Google Playのクローズドテストでは、Sesame 5実機・Hub 3・APIキーを持たないテスターが
 * 資格情報の登録画面から先へ進めず、アプリの操作感を確認できない。デモモードはその状態でも
 * 操作導線を一通り体験できるようにするためのもので、次の2点を前提とする。
 *
 * 1. デモ用デバイス（[DEMO_DEVICE_UUID]）は実在しないため、Sesame APIへは一切リクエストを
 *    送らない。施錠/解錠はwear側のローカル状態を書き換えるだけで完結する
 *    （実際の分岐は`com.sesamiwear.wear.action.SesameActionActivity`等が[isDemoDevice]で行う）。
 * 2. mobile側に1台でもデバイスが登録された時点でデモモードは提示しない（[isAvailable]）。
 *    実デバイスとデモ用デバイスが選択肢に混在すると、実際には施錠されていないのに施錠済みと
 *    誤認する事故につながるため。
 */
object SesameDemoMode {
    /**
     * デモ用デバイスを表す特別な値。実際のSesameデバイスのuuid（UUID形式）および
     * [SesameWearProtocol.ALL_DEVICES_TARGET_UUID]と衝突しない固定文字列にしている。
     */
    const val DEMO_DEVICE_UUID = "__demo_device__"

    /**
     * Tile・Complication・デバイス選択画面に表示する名前。実デバイスと取り違えないよう「デモ」を含める。
     *
     * Tileのデバイス名チップ（左列76dp、CAPTION2）に収まる長さは1行あたり5文字程度で
     * （BL-102で「デバイス変更」6文字が収まらないことを確認済み）、当初の「デモ（体験用）」7文字は
     * チップの背景をはみ出して表示されていた（BL-115の実機検証で再現）。文言側を[MAX_DISPLAY_NAME_CHARS]
     * 以内へ短縮して対処する。デモである旨は、デバイス選択画面の見出し「デモモード」と説明文が補う。
     */
    const val DEMO_DEVICE_DISPLAY_NAME = "デモ"

    /** [DEMO_DEVICE_DISPLAY_NAME]がTileのデバイス名チップの1行へ収まる文字数の上限。 */
    const val MAX_DISPLAY_NAME_CHARS = 5

    /** デモ用デバイスの初期状態。安全側（施錠中）から始め、最初の操作を解錠にする。 */
    const val INITIAL_IS_LOCKED = true

    fun isDemoDevice(uuid: String): Boolean = uuid == DEMO_DEVICE_UUID

    /**
     * デモモードを提示してよいか。mobile側から同期された登録済みデバイス一覧[registeredDevices]が
     * 空（＝資格情報が未登録、またはまだ同期されていない）の場合に限り提示する。
     */
    fun isAvailable(registeredDevices: List<SesameDeviceSummary>): Boolean = registeredDevices.isEmpty()

    /**
     * デバイス選択画面へ並べる選択肢。登録済みデバイスがあればそれをそのまま、
     * なければデモ用デバイス1台のみを返す。
     */
    fun selectableDevices(registeredDevices: List<SesameDeviceSummary>): List<SesameDeviceSummary> =
        if (isAvailable(registeredDevices)) {
            listOf(SesameDeviceSummary(uuid = DEMO_DEVICE_UUID, displayName = DEMO_DEVICE_DISPLAY_NAME))
        } else {
            registeredDevices
        }

    /**
     * デモ用デバイスの表示状態。スマホ接続状態・DataItemの鮮度に依存せず、
     * ローカルに保持したロック状態[isLocked]をそのまま表示へ反映する
     * （スマホ未接続でも操作を体験できるようにするため）。
     */
    fun displayState(isLocked: Boolean): TileDisplayState =
        if (isLocked) TileDisplayState.LOCKED else TileDisplayState.UNLOCKED

    /** デモ用デバイスへ[command]を実行した後のロック状態。 */
    fun nextIsLocked(command: SesameCommand): Boolean = command == SesameCommand.LOCK
}

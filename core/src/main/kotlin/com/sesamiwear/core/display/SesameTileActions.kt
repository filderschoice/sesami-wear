package com.sesamiwear.core.display

import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand

/**
 * Tile・ウィジェット・クイック設定・ウォッチの状態一覧で、タップしたときの動作を決める。
 * wearのTileとmobileのホーム画面ウィジェットで同じ操作ルールを使うためcoreに置いている（BL-119）。
 *
 * 動作は[TapDecision]で表す（BL-295）。
 * - 施錠中・解錠中・一部解錠（MIXED）: [TapDecision.OpenChooser]。施錠・解錠を選ぶ画面を開き、押したボタンをそのまま実行する。
 *   表示と逆の操作（施錠中に施錠、一部だけ施錠のときに全解錠）も選べるようにするため（BL-295）。解錠中のタップも
 *   BL-304（2026-11-21のユーザー判断）で即施錠をやめ、1台・全デバイス、Tile・ウィジェット・クイック設定のすべてで選ぶ画面に統一した。
 * - 通信中・スマホ未接続・状態不明: [TapDecision.None]（タップ不可）。
 *
 * タイル・ウィジェット・クイック設定のタップだけで実行する経路は無い（すぐに施錠したいときは、ウォッチのアプリの一覧の末尾の
 * 「全施錠」か、スマホのアプリアイコンの長押しメニューの「全施錠」を使う。操作パネルの上段もBL-307で選ぶ画面へまとめた）。
 *
 * 解錠は、選ぶ画面で「解錠」を押す明示操作を経てから実行する。タップだけで解錠する経路は作らない
 * （[com.sesamiwear.core.api.SesameCommandConfirmation]の方針を、選ぶ画面の解錠ボタンで満たす）。
 */
object SesameTileActions {
    /** タップしたときの動作。 */
    sealed interface TapDecision {
        /** 施錠・解錠を選ぶ画面を開く。 */
        data object OpenChooser : TapDecision

        /** 操作できない。 */
        data object None : TapDecision
    }

    /**
     * 表示状態から、従来の「表示と逆の操作を1つだけ提示する」コマンドを返す。
     * BL-295以前の規則で、挙動は変えていない（新しいタップの規則は[tapDecisionForState]）。
     */
    fun commandForState(state: TileDisplayState): SesameCommand? =
        when (state) {
            TileDisplayState.LOCKED -> SesameCommand.UNLOCK
            TileDisplayState.UNLOCKED, TileDisplayState.MIXED -> SesameCommand.LOCK
            TileDisplayState.IN_PROGRESS, TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN -> null
        }

    /** Tile・ホーム画面ウィジェット・クイック設定タイルのタップの動作。単体・全デバイスで同じ。 */
    fun tapDecisionForState(state: TileDisplayState): TapDecision =
        when (state) {
            TileDisplayState.LOCKED, TileDisplayState.MIXED, TileDisplayState.UNLOCKED -> TapDecision.OpenChooser
            TileDisplayState.IN_PROGRESS, TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN -> TapDecision.None
        }

    /** ウォッチアプリのデバイス一覧の行タップの動作。状態によらず（状態不明でも）常に選ぶ画面を開く。 */
    fun listTapDecision(): TapDecision = TapDecision.OpenChooser

    /** 選ぶ画面のボタンの文言。全デバイス対象なら「全施錠」「全解錠」。 */
    fun commandLabel(
        command: SesameCommand,
        isAllDevices: Boolean,
    ): String {
        val base = if (command == SesameCommand.LOCK) "施錠" else "解錠"
        return if (isAllDevices) "全$base" else base
    }
}

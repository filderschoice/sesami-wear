package com.sesamiwear.mobile.widget

import com.sesamiwear.core.display.SesameTileContent

/**
 * ホーム画面ウィジェットの押せる部分へ付ける読み上げ文言（contentDescription、BL-260）。
 *
 * 画面上の文字をそのまま読ませると、状態アイコンの絵文字（🔒）や順送りの「◀」「▶」が記号名で読まれ、
 * 押すと何が起きるか（解錠の確認画面・即時の施錠・状態取得）が音声だけでは分からない。そこで、
 * TalkBackが押せる部分ごとに「何の・どんな状態で・押すとどうなるか」を1文で読めるよう、ここで文言を組み立てる。
 * 絵文字は読ませず、電池残量は「電池残量85%」のように言葉で表す。
 *
 * Android非依存のためユニットテスト対象。Glanceへの適用は[SesameWidget]側で行う。
 */
object WidgetAccessibilityLabels {
    /** 未設定のウィジェット全面（タップで選択画面を開く）。 */
    const val UNCONFIGURED = "Sesami Wearのウィジェット。タップして操作するSesameを選びます"

    /** 「変更」チップ（選択画面を開く）。 */
    const val CHANGE = "操作するSesameを変更"

    /** 2マス×1マス表示の「◀」（対象デバイスを1つ前へ戻す、BL-175）。 */
    const val CYCLE_BACKWARD = "前のSesameへ切り替え"

    /** 2マス×1マス表示の「▶」（対象デバイスを1つ後ろへ進める、BL-175）。 */
    const val CYCLE_FORWARD = "次のSesameへ切り替え"

    /** 「更新」チップ（4x2）と、2マス×1マス表示のデバイス名チップ（どちらもタップで状態取得）。 */
    fun refresh(displayName: String): String = "${displayName}の状態を更新"

    /**
     * 状態表示（タップで施錠・解錠の確認画面など）の読み上げ文言。
     * デバイス名・状態・最終取得時刻または失敗の理由・電池残量・押したときの操作を、この順に読点でつなぐ。
     * 操作できない状態（通信中・状態不明）では操作の部分を省く（押しても何も起きないため）。
     */
    fun status(model: SesameWidgetModel.Configured): String =
        listOfNotNull(
            model.displayName,
            model.statusLabel.trimEnd('.'),
            model.detailLabel,
            battery(model.batteryPercentage),
            model.actionLabel,
        ).joinToString("、")

    /** 電池残量の読み上げ。残量が少ないときは画面の⚠に相当する注意を先に言う（BL-257）。 */
    internal fun battery(percentage: Int?): String? =
        percentage?.let {
            if (SesameTileContent.isLowBattery(it)) "電池残量が少なくなっています $it%" else "電池残量$it%"
        }
}

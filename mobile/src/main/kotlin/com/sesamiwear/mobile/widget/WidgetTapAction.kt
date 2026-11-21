package com.sesamiwear.mobile.widget

import com.sesamiwear.core.display.SesameTileActions

/**
 * ウィジェット右側（状態表示）をタップしたときの動作（BL-122）。操作ルールはwearのTileと同じで、
 * `SesameTileActions.tapDecisionForState`（施錠中・解錠中・一部解錠→施錠・解錠を選ぶ画面、通信中・状態不明→なし。
 * BL-295 / BL-304。解錠中の即施錠はBL-304でやめた）で決める。解錠は選ぶ画面で「解錠」を押す明示操作を経てから実行される。
 */
sealed interface WidgetTapAction {
    /** 対象デバイスが未設定のため、選択画面を開く。 */
    data object OpenConfiguration : WidgetTapAction

    /** 施錠・解錠を選ぶ画面を開く（BL-295）。 */
    data object Choose : WidgetTapAction

    /** 操作できない状態（通信中・状態不明）。タップしても何もしない。 */
    data object None : WidgetTapAction

    companion object {
        fun forModel(model: SesameWidgetModel): WidgetTapAction =
            when (model) {
                SesameWidgetModel.Unconfigured -> OpenConfiguration
                is SesameWidgetModel.Configured -> {
                    when (SesameTileActions.tapDecisionForState(model.state)) {
                        SesameTileActions.TapDecision.OpenChooser -> Choose
                        SesameTileActions.TapDecision.None -> None
                    }
                }
            }
    }
}

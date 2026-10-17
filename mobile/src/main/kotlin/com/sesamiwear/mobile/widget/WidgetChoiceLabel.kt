package com.sesamiwear.mobile.widget

import com.sesamiwear.core.display.SesameDeviceTargets

/**
 * ウィジェットの対象デバイスの選択画面で、各選択肢のボタンへ出す文言（BL-261）。
 *
 * 「変更」や長押しメニューから開いたとき、どれが現在の対象か分からないと、選び直す必要があるかを
 * 判断できない。そこで現在割り当てている選択肢にだけ[SELECTED_SUFFIX]を付ける。
 * 追加直後（未割り当て）や、割り当て先が選択肢から消えている（削除済み・デモ解除）場合は何も付けない。
 *
 * Android非依存のためユニットテスト対象。
 */
object WidgetChoiceLabel {
    const val SELECTED_SUFFIX = "（選択中）"

    fun of(
        choice: SesameDeviceTargets.Choice,
        assignedUuid: String?,
    ): String = if (choice.uuid == assignedUuid) "${choice.label}$SELECTED_SUFFIX" else choice.label
}

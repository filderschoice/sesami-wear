package com.sesamiwear.mobile.widget

import com.sesamiwear.core.haptics.HapticPattern
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome

/**
 * ホーム画面ウィジェットの施錠/解錠の実行結果から、鳴らすハプティクスを決める（BL-129）。
 * Android非依存のためユニットテスト対象。
 *
 * 「全デバイス」対象では登録台数ぶんの結果が返るため、次の規則で1つへまとめる。
 * - 1台でも失敗していれば[HapticPattern.FAILURE]。一部だけ成功した状態を「成功」と伝えないため。
 * - すべて[Outcome.DEBOUNCED]（連打として無視した）なら**鳴らさない**（null）。
 *   APIを呼んでいないため伝えるべき結果が無く、連打のたびに振動させると抑止の意味が薄れる。
 *   wear側も重複として無視した場合は結果を返さず振動しない（BL-062）ので、挙動が揃う。
 * - それ以外（成功のみ、または成功と重複の混在）は[HapticPattern.SUCCESS]。
 *
 * 状態取得（デバイス名のタップ）は対象外で鳴らさない。wear側の状態取得もFire-and-forgetで
 * 結果を返さず振動しない（BL-061）ため、こちらも揃える。
 */
object WidgetHapticResolver {
    fun resolve(outcomes: List<Outcome>): HapticPattern? =
        when {
            outcomes.isEmpty() -> null
            outcomes.any { it == Outcome.FAILURE } -> HapticPattern.FAILURE
            outcomes.all { it == Outcome.DEBOUNCED } -> null
            else -> HapticPattern.SUCCESS
        }
}

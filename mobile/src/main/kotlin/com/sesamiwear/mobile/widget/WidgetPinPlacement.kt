package com.sesamiwear.mobile.widget

import com.sesamiwear.core.display.SesameDeviceTargets

/**
 * アプリの設定メニューからホーム画面へウィジェットを置いたとき（BL-262）の、対象デバイスの自動割り当て。
 *
 * `AppWidgetManager.requestPinAppWidget`で置いたウィジェットは、ランチャーによっては選択画面
 * （`android:configure`）を開かずに配置され、「タップして設定」のまま残る。選択肢が1つしかない
 * （登録1台、または登録0台でデモのみ）なら選ぶ余地が無いため、配置の完了通知を受けた時点でそれを割り当てる。
 * 2つ以上なら利用者に選んでもらう必要があるため割り当てず、従来どおり「タップして設定」から選ぶ。
 * 既に割り当て済み（ランチャーが選択画面を開き、利用者が選び終えている）場合は上書きしない。
 *
 * Android非依存のためユニットテスト対象。
 */
object WidgetPinPlacement {
    /** 自動で割り当てるuuid。割り当てない場合はnull。 */
    fun autoAssignUuid(
        choices: List<SesameDeviceTargets.Choice>,
        currentAssignment: String?,
    ): String? = if (currentAssignment != null) null else choices.singleOrNull()?.uuid
}

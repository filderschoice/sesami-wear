package com.sesamiwear.mobile.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * ホーム画面ウィジェット（[SesameWidget]）の`AppWidgetProvider`（BL-121）。ウィジェットが削除されたら
 * そのインスタンスの対象デバイス割り当てを消す。薄いアダプタのためユニットテスト対象外。
 */
class SesameWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SesameWidget()

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        SesameWidgetRepository.assignmentStore(context).remove(appWidgetIds.toList())
    }
}

package com.sesamiwear.mobile.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * ウィジェット一覧へ「2 × 1」として並べるための変種（BL-186）。
 *
 * **表示も操作も[SesameWidget]と完全に同じ**で、違うのは`appwidget-provider`が宣言する初期サイズだけ
 * （`sesame_widget_small_info.xml`）。Androidは1つのproviderへ初期サイズを1つしか持たせられないため、
 * 「置いた直後から小さく使いたい」を満たすにはproviderをもう1つ宣言するしかない。
 *
 * **別クラスにしているのは`GlanceAppWidgetManager`の都合。** 同じ[GlanceAppWidget]の実装クラスを
 * 2つのレシーバへ割り当てると、クラスからレシーバを引く対応表が片方で上書きされ、
 * `getGlanceIds`が一方のインスタンスを取りこぼしうる（＝再描画が届かなくなる）。
 * [SesameWidgetUpdater]は両方のクラスを走査する。
 */
class SesameWidgetSmall : SesameWidget()

/**
 * [SesameWidgetSmall]の`AppWidgetProvider`（BL-186）。削除時の後始末は[SesameWidgetReceiver]と同じ。
 * 対象デバイスの割り当ては appWidgetId ごとに保存しており、appWidgetId は provider をまたいで
 * 一意のため、2種類が混在しても取り違えは起きない。
 */
class SesameWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SesameWidgetSmall()

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        SesameWidgetRepository.assignmentStore(context).remove(appWidgetIds.toList())
    }
}

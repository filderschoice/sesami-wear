package com.sesamiwear.mobile.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * ホーム画面ウィジェットの再描画を要求する（BL-121）。資格情報の保存・削除、対象デバイスの割り当て、
 * ロック状態の変化のあとに呼ぶ。各インスタンスの状態へ更新トークンを書き込んでから`update`を呼び、
 * 描画側（[SesameWidget]）に保存値を読み直させる。Glance呼び出しのみの薄いアダプタのためユニットテスト対象外。
 */
object SesameWidgetUpdater {
    /**
     * 配置済みのすべてのウィジェットを再描画する。
     *
     * 初期サイズ違いの2種類（[SesameWidget] / [SesameWidgetSmall]、BL-186）はレシーバが別のため、
     * **両方のクラスを走査する**。片方だけだと、もう一方で置いたウィジェットへ再描画が届かない。
     */
    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        listOf(SesameWidget(), SesameWidgetSmall()).forEach { widget ->
            manager.getGlanceIds(widget.javaClass).forEach { refresh(context, widget, it) }
        }
    }

    /**
     * [appWidgetId]のウィジェットだけを再描画する（選択画面での割り当て直後）。
     *
     * 描画内容は2種類で同一のため、どちらのインスタンスで描いても同じ結果になる
     * （[SesameWidgetSmall]は初期サイズだけが違う、BL-186）。
     */
    suspend fun update(
        context: Context,
        appWidgetId: Int,
    ) {
        refresh(context, SesameWidget(), GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))
    }

    private suspend fun refresh(
        context: Context,
        widget: SesameWidget,
        glanceId: GlanceId,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SesameWidget.REFRESH_TOKEN_KEY] = System.nanoTime()
        }
        widget.update(context, glanceId)
    }
}

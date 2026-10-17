package com.sesamiwear.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * アプリの設定メニューから、ホーム画面へウィジェットを置くよう要求する（BL-262）。
 *
 * ホーム画面の長押し→ウィジェット一覧から探す手順は、ウィジェットの存在自体に気付きにくい。
 * `AppWidgetManager.requestPinAppWidget`（API 26以降、minSdkと同じ）を使うと、ランチャーが
 * 確認画面を出し、利用者の承認でホーム画面の空き位置へ置かれる。置くのは既定の大きさ（4 × 2）の
 * [SesameWidgetReceiver]。配置の完了は[WidgetPinnedReceiver]が受け、選択肢が1つなら対象を自動で割り当てる。
 *
 * 要求に対応するかはランチャー次第のため、非対応なら[isSupported]がfalseになり、メニュー側は
 * 長押しからの追加を案内する。Android依存の配線だけのためユニットテスト対象外。
 */
object WidgetPinRequester {
    fun isSupported(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** 配置を要求する。ランチャーが要求を受け付けなかった場合はfalse。 */
    fun request(context: Context): Boolean {
        val appContext = context.applicationContext
        // ランチャーが配置したウィジェットのIDを`EXTRA_APPWIDGET_ID`として書き足すため、MUTABLEにする
        // （宛先は自アプリの非公開レシーバを明示しているため、書き換えられるのはextraだけ）。
        val callback =
            PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(appContext, WidgetPinnedReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        return AppWidgetManager.getInstance(appContext).requestPinAppWidget(
            ComponentName(appContext, SesameWidgetReceiver::class.java),
            null,
            callback,
        )
    }
}

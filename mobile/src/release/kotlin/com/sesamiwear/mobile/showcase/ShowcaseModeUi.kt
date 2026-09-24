package com.sesamiwear.mobile.showcase

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * 撮影モード（BL-213）の画面部品の**リリース版スタブ**。メニュー項目も表示も出さない。
 * 実装は`src/debug`の同名オブジェクト。関数を増やすときは両方へ同じ形で足す。
 */
@Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
object ShowcaseModeUi {
    fun menuEntryOrNull(context: Context): ShowcaseMenuEntry? = null

    @Composable
    fun Indicator() = Unit
}

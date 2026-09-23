package com.sesamiwear.mobile.showcase

import android.content.Intent

/**
 * 設定メニューへ足す撮影モード（BL-213）の項目。デバッグ版の`ShowcaseModeUi.menuEntryOrNull`だけが返し、
 * リリース版は常にnullを返すため、リリース版のメニューには現れない。
 */
data class ShowcaseMenuEntry(
    val title: String,
    val subtitle: String,
    val intent: Intent,
)

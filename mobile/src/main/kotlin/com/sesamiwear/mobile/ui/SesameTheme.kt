package com.sesamiwear.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * mobile側の画面共通のテーマ（BL-181 / BL-245）。
 *
 * もとは`MaterialTheme`へcolorSchemeを渡しておらず、端末の設定に関係なく常にライトで描画していた。
 * 暗い部屋でアプリを開くと画面全体が白く光り、ホーム画面ウィジェット（暗色固定）・
 * ウォッチのTile（黒背景）とも見た目が食い違っていた。
 *
 * **Material Youの動的カラーは使わない。** 施錠状態の色（緑＝施錠中／赤＝解錠中／紫＝一部解錠、
 * `core.display.SesameTileContent`）が壁紙由来の色と競合し、状態の読み取りを鈍らせるため。
 * 状態色はTile・ウィジェットと同じ固定値で、テーマはその周りの地の色だけを担う。
 *
 * 地の色は、アプリアイコン・ストア画像と同じ紺（`#1E3A5F`）を主色にする（BL-245、2026-10-15のユーザー判断）。
 * BL-245より前はM3の既定（紫系）のままで、アイコンの印象と画面の印象が食い違っていた。
 * 主色だけを差し替え、面（surface）の色はM3の既定を使う。ダークでは紺を明るくした色を主色にする
 * （暗い背景の上で紺の文字・ボタンは読めないため）。
 *
 * システムバーのアイコン色は、テーマに追随させる必要があるため各Activity側で切り替える
 * （`MainActivity.applySystemBarIcons`）。
 */
@Composable
fun SesameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF1E3A5F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E4FF),
        onPrimaryContainer = Color(0xFF001C3A),
        secondary = Color(0xFF545F70),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD8E3F8),
        onSecondaryContainer = Color(0xFF111C2B),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA4C8FF),
        onPrimary = Color(0xFF00315E),
        primaryContainer = Color(0xFF1E3A5F),
        onPrimaryContainer = Color(0xFFD3E4FF),
        secondary = Color(0xFFBCC7DB),
        onSecondary = Color(0xFF263141),
        secondaryContainer = Color(0xFF3C4758),
        onSecondaryContainer = Color(0xFFD8E3F8),
    )

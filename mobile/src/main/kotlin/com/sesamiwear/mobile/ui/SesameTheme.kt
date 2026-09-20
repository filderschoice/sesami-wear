package com.sesamiwear.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * mobile側の画面共通のテーマ（BL-181）。
 *
 * もとは`MaterialTheme`へcolorSchemeを渡しておらず、端末の設定に関係なく常にライトで描画していた。
 * 暗い部屋でアプリを開くと画面全体が白く光り、ホーム画面ウィジェット（暗色固定）・
 * ウォッチのTile（黒背景）とも見た目が食い違っていた。
 *
 * **Material Youの動的カラーは使わない。** 施錠状態の色（緑＝施錠中／赤＝解錠中／紫＝一部解錠、
 * `core.display.SesameTileContent`）が壁紙由来の色と競合し、状態の読み取りを鈍らせるため。
 * 状態色はTile・ウィジェットと同じ固定値で、テーマはその周りの地の色だけを担う。
 *
 * システムバーのアイコン色は、テーマに追随させる必要があるため各Activity側で切り替える
 * （`MainActivity.applySystemBarIcons`）。
 */
@Composable
fun SesameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

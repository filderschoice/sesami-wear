package com.sesamiwear.mobile.widget

import com.sesamiwear.core.display.SesameTileContent

/**
 * ホーム画面ウィジェットの中立色（状態色以外）の配色を、端末のライト/ダークテーマごとに持つ（BL-263）。
 *
 * BL-262まではウォッチのTileに合わせて常に暗色だったが、ライトテーマ・明るい壁紙の端末で浮いて見えるため、
 * 背景・チップ・デバイス名の帯・中立色の文字だけを端末のテーマへ追随させる（2026-10-17、ユーザー判断）。
 * **状態色（右下の状態表示の背景と文字）は変えない。** 施錠中＝緑・解錠中＝赤の意味はウォッチと同じに保つ。
 * ダークテーマの値は従来の値そのもの（ウォッチのTileと共通の[SesameTileContent]の定数）。
 *
 * 明暗の関係はダークと同じにする: 押せるチップ（[Scheme.chip]）は背景からはっきり浮かせ、表示専用の
 * デバイス名の帯（[Scheme.nameHeader]）はチップより背景に近い色にしてボタンと区別する（BL-210）。
 *
 * Android非依存のためユニットテスト対象（文字と背景のコントラスト比を検査する）。
 * Glanceへは`androidx.glance.color.ColorProvider(day, night)`で渡し、Android 12以降は再描画なしで
 * テーマの切り替えに追随する（それより前は描画時のテーマで決まる）。
 */
object SesameWidgetPalette {
    /** ライトテーマの配色。 */
    val DAY =
        Scheme(
            background = 0xFFF4F4F4.toInt(),
            chip = 0xFFD9D9D9.toInt(),
            nameHeader = 0xFFE8E8E8.toInt(),
            text = 0xFF1F1F1F.toInt(),
        )

    /** ダークテーマの配色（従来の値）。 */
    val NIGHT =
        Scheme(
            background = 0xFF121212.toInt(),
            chip = SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB,
            nameHeader = SesameTileContent.NAME_HEADER_COLOR_ARGB,
            text = 0xFFFFFFFF.toInt(),
        )

    /** 1つのテーマでの中立色一式（ARGB）。 */
    data class Scheme(
        val background: Int,
        val chip: Int,
        val nameHeader: Int,
        val text: Int,
    )
}

package com.sesamiwear.wear.tile

import com.sesamiwear.core.TileDisplayState

/**
 * [TileDisplayState] からTileの表示文言を決定する。Android非依存のためユニットテスト対象。
 * [statusLabel]/[actionLabel]の`isAllDevices`は、対象が単一デバイスか登録済み全デバイス
 * （BL-071、複数デバイス一括操作）かで文言を切り替えるためのフラグ。
 */
object SesameTileContent {
    /**
     * 状態を一目で区別できる絵文字アイコン（BL-063、視認性向上）。MIXEDは複数デバイス一括操作
     * （BL-071）で施錠中/解錠中のデバイスが混在している状態を表す。
     */
    fun statusIcon(state: TileDisplayState): String =
        when (state) {
            TileDisplayState.LOCKED -> "🔒"
            TileDisplayState.UNLOCKED -> "🔓"
            TileDisplayState.MIXED -> "🔀"
            TileDisplayState.IN_PROGRESS -> "⏳"
            TileDisplayState.DISCONNECTED -> "📵"
            TileDisplayState.UNKNOWN -> "❔"
        }

    fun statusLabel(
        state: TileDisplayState,
        isAllDevices: Boolean = false,
    ): String =
        when (state) {
            TileDisplayState.LOCKED -> if (isAllDevices) "全施錠中" else "施錠中"
            TileDisplayState.UNLOCKED -> if (isAllDevices) "全解錠中" else "解錠中"
            TileDisplayState.MIXED -> "施錠/解錠混在"
            TileDisplayState.IN_PROGRESS -> "通信中..."
            TileDisplayState.DISCONNECTED -> "スマホ未接続"
            TileDisplayState.UNKNOWN -> "状態不明"
        }

    fun actionLabel(
        state: TileDisplayState,
        isAllDevices: Boolean = false,
    ): String? =
        when (state) {
            TileDisplayState.LOCKED -> if (isAllDevices) "タップで全解錠" else "タップで解錠"
            TileDisplayState.UNLOCKED -> if (isAllDevices) "タップで全施錠" else "タップで施錠"
            TileDisplayState.MIXED -> "タップで全施錠"
            TileDisplayState.IN_PROGRESS, TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN -> null
        }

    /**
     * 状態をひと目で区別できる背景色（ARGB）。施錠中=緑、解錠中=赤、混在=紫、通信中=黄、
     * それ以外（未接続・不明）=グレー。
     */
    fun backgroundColorArgb(state: TileDisplayState): Int =
        when (state) {
            TileDisplayState.LOCKED -> COLOR_LOCKED_GREEN
            TileDisplayState.UNLOCKED -> COLOR_UNLOCKED_RED
            TileDisplayState.MIXED -> COLOR_MIXED_PURPLE
            TileDisplayState.IN_PROGRESS -> COLOR_IN_PROGRESS_AMBER
            TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN -> COLOR_NEUTRAL_GRAY
        }

    /**
     * [backgroundColorArgb]の背景に対してコントラストを確保するテキスト色（ARGB、BL-063）。
     * 通信中（明るいアンバー背景）のみ濃色、それ以外（緑・赤・紫・グレーの中〜暗いトーン）は白系にする。
     */
    fun statusTextColorArgb(state: TileDisplayState): Int =
        when (state) {
            TileDisplayState.IN_PROGRESS -> COLOR_TEXT_DARK
            TileDisplayState.LOCKED, TileDisplayState.UNLOCKED, TileDisplayState.MIXED,
            TileDisplayState.DISCONNECTED, TileDisplayState.UNKNOWN,
            -> COLOR_TEXT_LIGHT
        }

    /**
     * 状態色と無関係な中立チップ（Tileのデバイス名/デバイス変更チップ、施錠/解錠確認画面の
     * キャンセルボタン等）に共通で使うダークグレー（BL-063/BL-070）。
     */
    const val CHIP_NEUTRAL_COLOR_ARGB = 0xFF424242.toInt()

    private const val COLOR_LOCKED_GREEN = 0xFF4CAF50.toInt()
    private const val COLOR_UNLOCKED_RED = 0xFFF44336.toInt()
    private const val COLOR_MIXED_PURPLE = 0xFF7E57C2.toInt()
    private const val COLOR_IN_PROGRESS_AMBER = 0xFFFFC107.toInt()
    private const val COLOR_NEUTRAL_GRAY = 0xFF9E9E9E.toInt()
    private const val COLOR_TEXT_LIGHT = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_DARK = 0xFF212121.toInt()
}

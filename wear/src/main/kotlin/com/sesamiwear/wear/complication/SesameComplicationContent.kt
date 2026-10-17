package com.sesamiwear.wear.complication

import com.sesamiwear.core.TileDisplayState

/**
 * [TileDisplayState] からComplication（文字盤表示、表示領域が非常に小さい）向けの短い文言を決定する。
 * Android非依存のためユニットテスト対象。
 */
object SesameComplicationContent {
    fun shortText(state: TileDisplayState): String =
        when (state) {
            TileDisplayState.LOCKED -> "施錠"
            TileDisplayState.UNLOCKED -> "解錠"
            TileDisplayState.MIXED -> "混在"
            TileDisplayState.IN_PROGRESS -> "通信中"
            TileDisplayState.DISCONNECTED -> "未接続"
            TileDisplayState.UNKNOWN -> "不明"
        }

    /**
     * `LONG_TEXT`枠向けの文言（BL-072）。`SHORT_TEXT`枠しか受け付けない文字盤で本データソースが
     * 選べない・表示されない事象を避けるため、`LONG_TEXT`もサポート対象に加えた際に追加した。
     * 表示領域に余裕があるため、状態文言に加えて対象デバイス名も併記する。
     *
     * [detailLabel]（`core.display.SesameStatusDetail`が組み立てた「3分前」「認証エラー」等）を
     * 渡すと括弧付きで末尾へ添える（BL-142 / BL-140）。Complicationは自動での状態取得を
     * 行わなくなったため、表示がどれだけ古いか・直近の取得が失敗していないかを利用者が判断
     * できるようにする。`SHORT_TEXT`枠は表示できる文字数が非常に少ないため対象外とし、
     * [shortText]は状態文言のみのままにする。
     */
    fun longText(
        displayName: String,
        state: TileDisplayState,
        detailLabel: String? = null,
    ): String {
        val base = "$displayName ${shortText(state)}"
        return if (detailLabel == null) base else "$base（$detailLabel）"
    }

    /**
     * アイコンだけの型（`MONOCHROMATIC_IMAGE` / `SMALL_IMAGE`）と`RANGED_VALUE`の画像に使うアイコンの種類（BL-256）。
     * 実際の画像（drawable）への対応付けはAndroid依存のため呼び出し側が持つ。
     */
    enum class StateIcon {
        LOCKED,
        UNLOCKED,
        IN_PROGRESS,
        DISCONNECTED,
        UNKNOWN,
    }

    /**
     * 表示状態からアイコンの種類を決める。一部解錠（MIXED）は「開いている扉がある」ことを示すため
     * 解錠のアイコンにする（文字の無い枠で施錠のアイコンを出すと、全台施錠済みと誤読されるため）。
     */
    fun stateIcon(state: TileDisplayState): StateIcon =
        when (state) {
            TileDisplayState.LOCKED -> StateIcon.LOCKED
            TileDisplayState.UNLOCKED, TileDisplayState.MIXED -> StateIcon.UNLOCKED
            TileDisplayState.IN_PROGRESS -> StateIcon.IN_PROGRESS
            TileDisplayState.DISCONNECTED -> StateIcon.DISCONNECTED
            TileDisplayState.UNKNOWN -> StateIcon.UNKNOWN
        }

    /** 電池残量のゲージ（`RANGED_VALUE`、BL-256）の下限・上限。 */
    const val BATTERY_MIN_VALUE = 0f
    const val BATTERY_MAX_VALUE = 100f

    /** 電池残量が未取得のときにゲージへ添える文字。0%と誤読されないよう数字を出さない。 */
    const val BATTERY_UNKNOWN_TEXT = "--"

    /** 電池残量のゲージの値と、ゲージへ添える短い文字（「85%」）。 */
    data class BatteryGauge(
        val value: Float,
        val text: String,
    )

    /**
     * 電池残量（%）からゲージを作る。未取得（null）ならゲージを空にして[BATTERY_UNKNOWN_TEXT]を添える。
     * 範囲外の値は0〜100へ丸める（文字盤側が範囲外の値を受け付けないため）。
     */
    fun batteryGauge(percentage: Int?): BatteryGauge {
        val clamped = percentage?.coerceIn(BATTERY_MIN_VALUE.toInt(), BATTERY_MAX_VALUE.toInt())
        return if (clamped == null) {
            BatteryGauge(BATTERY_MIN_VALUE, BATTERY_UNKNOWN_TEXT)
        } else {
            BatteryGauge(clamped.toFloat(), "$clamped%")
        }
    }

    /**
     * `RANGED_VALUE`枠の読み上げ文言。状態の文言（[stateDescription]）に電池残量を添える
     * （「玄関 施錠 電池85%」「玄関 施錠 電池残量不明」）。
     */
    fun batteryDescription(
        stateDescription: String,
        percentage: Int?,
    ): String {
        val battery = if (percentage == null) "電池残量不明" else "電池${batteryGauge(percentage).text}"
        return "$stateDescription $battery"
    }
}

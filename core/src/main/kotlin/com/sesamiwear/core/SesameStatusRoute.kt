package com.sesamiwear.core

/**
 * 施錠/解錠・状態取得を実行した経路（BL-166）。
 *
 * BL-152で経路の使い分けを入れた当初は「利用者は経路を意識しない」方針だったが、BLEで届かなかった
 * ときに利用者が原因を切り分けられないという問題があり、2026-09-19のユーザー判断で**経路を見せる**
 * 方針へ変更した（BL-168）。そのため、最後に使った経路を状態のスナップショットへ載せて
 * ウォッチまで同期する。
 *
 * 表示側の文言・アイコンは`core.display.SesameRouteLabel`が持つ。
 */
enum class SesameStatusRoute {
    /** Bluetoothでセサミへ直接つないで実行した。Sesame Web APIのリクエストを消費しない。 */
    BLE,

    /** Sesame Web API（インターネット）経由で実行した。 */
    WEB_API,
    ;

    companion object {
        /** 保存値・DataItemから復元する。未知の値・nullはnull（経路不明）として扱う。 */
        fun ofNameOrNull(name: String?): SesameStatusRoute? = entries.find { it.name == name }
    }
}

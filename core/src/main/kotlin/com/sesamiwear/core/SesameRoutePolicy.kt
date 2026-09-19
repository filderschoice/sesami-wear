package com.sesamiwear.core

/**
 * 施錠/解錠・状態取得にどの経路を使うかの方針（BL-167）。全デバイス共通で、利用者が設定画面から選ぶ。
 *
 * **「Bluetooth固定」は用意しない。** セサミの電波圏外では一切操作できなくなり、利用者が
 * 締め出されるため。BLEが使えないときに必ずWeb APIへ倒れる構造は、どの方針でも維持する。
 */
enum class SesameRoutePolicy {
    /**
     * 既定。直近にBluetoothで到達できたデバイスはBluetoothを先に試し、届かなければ
     * インターネット経由へ倒れる（BL-152の判定に従う）。
     */
    AUTO,

    /**
     * 常にインターネット経由で実行する。Bluetoothの探索・接続・到達確認をいずれも行わないため、
     * 電力とスキャン回数を消費しない。Bluetoothが不安定なときに確実な経路へ固定するための選択肢。
     */
    WEB_API_ONLY,
    ;

    /** この方針でBluetoothを使ってよいか。 */
    val allowsBle: Boolean get() = this == AUTO

    companion object {
        /** 未設定・未知の値のときに使う方針。 */
        val DEFAULT = AUTO

        /** 保存値から復元する。未知の値・nullは[DEFAULT]として扱う。 */
        fun ofNameOrDefault(name: String?): SesameRoutePolicy = entries.find { it.name == name } ?: DEFAULT
    }
}

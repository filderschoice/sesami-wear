package com.sesamiwear.core.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sesame 5状態取得APIのレスポンス。
 * フィールド構成は参考実装pysesame3（https://github.com/mochipon/pysesame3）の
 * pysesame3/helper.py（CHSesameProtocolMechStatus / CHSesame2MechStatusのdictコンストラクタ分岐）
 * を参照した。一次情報は公式ドキュメント
 * （https://candy-house.github.io/API_document/ja/SesameOS3/webapi.html）で、
 * 応答例にはbatteryPercentage/timestampも含まれるが、本アプリは使わず未知の項目として無視してデコードする。
 * 本アプリが使うのはbatteryVoltage/position/CHSesame2Statusで、isBatteryCriticalは
 * 含まれない（BLE mechstのrawバイト列からのみ算出可能）。
 * isInLockRange/isInUnlockRangeはCHSesame2Statusから導出する。
 * ただしpysesame3自体もこのレスポンス構造を将来変更されうる未確定事項として注意書きしており、
 * 実機疎通確認（BL-010、人手検証）で最終確認すること。
 */
@Serializable
data class SesameStatus(
    val batteryVoltage: Double,
    val position: Int,
    @SerialName("CHSesame2Status")
    val lockStatus: String,
) {
    val isInLockRange: Boolean get() = lockStatus == LOCKED_STATUS

    val isInUnlockRange: Boolean get() = !isInLockRange

    companion object {
        private const val LOCKED_STATUS = "locked"
    }
}

package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameBatteryLevel

/**
 * `MECH_STATUS`通知の本体（7バイト）を解釈した、Sesame 5の機構状態（BL-151）。
 *
 * バイト構成はリトルエンディアンで「電池の生値(2) / 目標角(2、符号付き) / 現在角(2、符号付き) /
 * ステータスビット(1)」。Web APIの状態取得（`core.api.SesameStatus`）が返すのと同じ情報を、
 * BLE経由ではこの通知から得る。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
data class SesameBleMechStatus(
    private val rawBattery: Int,
    val target: Int,
    val position: Int,
    private val statusFlags: Int,
) {
    /** 施錠範囲にあるか。Web APIの`CHSesame2Status == "locked"`に対応する。 */
    val isInLockRange: Boolean get() = statusFlags and FLAG_IN_LOCK_RANGE != 0

    /** 解錠範囲にあるか。施錠範囲でも解錠範囲でもない（＝一部解錠）状態がありうる。 */
    val isInUnlockRange: Boolean get() = statusFlags and FLAG_IN_UNLOCK_RANGE != 0

    /** 電池切れ間近としてデバイスが立てるフラグ。Web APIのレスポンスには含まれない情報。 */
    val isBatteryCritical: Boolean get() = statusFlags and FLAG_BATTERY_CRITICAL != 0

    /** 電池電圧（V）。生値はADCの読み値で、2倍して1000で割ると電圧になる。 */
    val batteryVoltage: Double get() = rawBattery * VOLTAGE_SCALE_NUMERATOR / VOLTAGE_SCALE_DENOMINATOR

    /** 電池残量（%）。換算表はWeb API経由の状態取得と共用する（BL-166）。 */
    val batteryPercentage: Int get() = SesameBatteryLevel.percentageOf(batteryVoltage)

    companion object {
        /** 通知本体のバイト数。これ未満・超過の場合は解釈しない。 */
        const val PAYLOAD_SIZE = 7

        private const val FLAG_IN_LOCK_RANGE = 0b0000_0010
        private const val FLAG_IN_UNLOCK_RANGE = 0b0000_0100
        private const val FLAG_BATTERY_CRITICAL = 0b0010_0000

        private const val VOLTAGE_SCALE_NUMERATOR = 2.0
        private const val VOLTAGE_SCALE_DENOMINATOR = 1000.0
        private const val BYTE_MASK = 0xFF

        /**
         * 通知本体[payload]を解釈する。長さが[PAYLOAD_SIZE]と一致しない場合はnullを返す
         * （将来デバイス側の仕様が変わっても落ちないようにするため、例外は投げない）。
         */
        fun parse(payload: ByteArray): SesameBleMechStatus? {
            if (payload.size != PAYLOAD_SIZE) return null
            return SesameBleMechStatus(
                rawBattery = readUInt16(payload, 0),
                target = readInt16(payload, 2),
                position = readInt16(payload, 4),
                statusFlags = payload[6].toInt() and BYTE_MASK,
            )
        }

        private fun readUInt16(
            source: ByteArray,
            offset: Int,
        ): Int = (source[offset].toInt() and BYTE_MASK) or ((source[offset + 1].toInt() and BYTE_MASK) shl 8)

        private fun readInt16(
            source: ByteArray,
            offset: Int,
        ): Int = readUInt16(source, offset).toShort().toInt()
    }
}

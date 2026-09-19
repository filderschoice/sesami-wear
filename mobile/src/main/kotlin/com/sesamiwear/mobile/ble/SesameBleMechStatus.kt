package com.sesamiwear.mobile.ble

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

    /** 電池残量（%）。電圧を既定のテーブルへ線形補間して求める。 */
    val batteryPercentage: Int get() = percentageOf(batteryVoltage)

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
         * 電圧と残量の対応表。gomalockの`VOLTAGE_LEVELS` / `BATTERY_PERCENTAGES`と同じ値で、
         * 公式アプリの表示に合わせた非線形な対応になっている。降順に並ぶ前提。
         */
        private val VOLTAGE_LEVELS =
            doubleArrayOf(5.85, 5.82, 5.79, 5.76, 5.73, 5.70, 5.65, 5.60, 5.55, 5.50, 5.40, 5.20, 5.10, 5.0, 4.8, 4.6)
        private val BATTERY_PERCENTAGES =
            doubleArrayOf(100.0, 95.0, 90.0, 85.0, 80.0, 70.0, 60.0, 50.0, 40.0, 32.0, 21.0, 13.0, 10.0, 7.0, 3.0, 0.0)

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

        /**
         * テーブルの範囲へ丸めたうえで、[voltage]が入る区間を探して線形補間する。
         * 下限以下（区間が見つからない）の場合は0%とする。
         */
        private fun percentageOf(voltage: Double): Int {
            val clamped = voltage.coerceIn(VOLTAGE_LEVELS.last(), VOLTAGE_LEVELS.first())
            val index =
                (0 until VOLTAGE_LEVELS.size - 1).firstOrNull { i ->
                    clamped > VOLTAGE_LEVELS[i + 1] && clamped <= VOLTAGE_LEVELS[i]
                }
            return if (index == null) {
                BATTERY_PERCENTAGES.last().toInt()
            } else {
                val upper = VOLTAGE_LEVELS[index]
                val lower = VOLTAGE_LEVELS[index + 1]
                val ratio = (clamped - lower) / (upper - lower)
                (
                    (BATTERY_PERCENTAGES[index] - BATTERY_PERCENTAGES[index + 1]) * ratio +
                        BATTERY_PERCENTAGES[index + 1]
                ).toInt()
            }
        }
    }
}

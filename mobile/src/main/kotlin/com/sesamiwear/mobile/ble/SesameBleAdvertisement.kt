package com.sesamiwear.mobile.ble

import java.util.Locale

/**
 * Sesameデバイスのアドバタイズに載る製造者データ（19バイト）の解釈（BL-151）。
 *
 * 構成はリトルエンディアンで「製品モデル(2) / 登録済みフラグ(1) / デバイスUUID(16、ビッグエンディアン)」。
 * スキャン結果から目的のuuidのデバイスを選ぶために使う。BLEアドレスはAndroidでは端末ごと・
 * 起動ごとに変わりうるため保存せず、常にこのuuidで突き合わせる。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
data class SesameBleAdvertisement(
    val productModelCode: Int,
    val isRegistered: Boolean,
    val deviceUuid: String,
) {
    /** 本アプリが操作対象とするSesame 5系のモデルか。 */
    val isSesame5Family: Boolean get() = productModelCode in SESAME_5_FAMILY_MODEL_CODES

    companion object {
        /** 製造者データのバイト数。 */
        const val PAYLOAD_SIZE = 19

        /**
         * Sesame 5 / Sesame 5 PRO / Sesame 5 US の製品モデルコード。
         * gomalockの`ProductModel`より、本アプリが対象とする機種だけを取る
         * （Sesame 6系は未検証のため含めない。対応する場合はここへ追加する）。
         */
        private val SESAME_5_FAMILY_MODEL_CODES = setOf(5, 7, 16)

        private const val BYTE_MASK = 0xFF
        private const val UUID_OFFSET = 3
        private const val UUID_SIZE = 16

        /**
         * 製造者データ[payload]を解釈する。長さが合わない場合はnullを返す
         * （Sesame以外のデバイスや、仕様変更後のデバイスで落ちないようにするため）。
         */
        fun parse(payload: ByteArray): SesameBleAdvertisement? {
            if (payload.size != PAYLOAD_SIZE) return null
            val modelCode = (payload[0].toInt() and BYTE_MASK) or ((payload[1].toInt() and BYTE_MASK) shl 8)
            return SesameBleAdvertisement(
                productModelCode = modelCode,
                isRegistered = payload[2].toInt() != 0,
                deviceUuid = formatUuid(payload, UUID_OFFSET),
            )
        }

        /**
         * 16バイトのUUIDを`8-4-4-4-12`の小文字表記へ整形する。
         * 保存済み資格情報のuuid（`SesameCredentials.uuid`）と大文字小文字を無視して比較する前提のため、
         * ここでは常に小文字で返す。
         */
        private fun formatUuid(
            source: ByteArray,
            offset: Int,
        ): String {
            val hex =
                buildString(UUID_SIZE * 2) {
                    for (i in 0 until UUID_SIZE) {
                        append(String.format(Locale.ROOT, "%02x", source[offset + i].toInt() and BYTE_MASK))
                    }
                }
            return listOf(
                hex.substring(0, 8),
                hex.substring(8, 12),
                hex.substring(12, 16),
                hex.substring(16, 20),
                hex.substring(20, 32),
            ).joinToString("-")
        }
    }
}

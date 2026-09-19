package com.sesamiwear.mobile.ble

import com.sesamiwear.mobile.ble.SesameBleProtocol.MTU_SIZE
import com.sesamiwear.mobile.ble.SesameBleProtocol.PacketFlag

/**
 * BLEのGATTパケット（先頭1バイトのヘッダ＋ペイロード）と、メッセージ全体との相互変換（BL-151）。
 *
 * Sesame OS3は1回の書き込み・通知を[MTU_SIZE]バイトに制限し、長いメッセージを分割して送る。
 * ヘッダのビットで「先頭か」「終端か」「終端なら暗号化されているか」を伝える仕様のため、
 * 送信側は分割してヘッダを付け、受信側は先頭フラグで組み立て直す。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
object SesameBlePacketCodec {
    /** 1パケットに載せられるペイロードの最大バイト数（ヘッダ1バイトを除く）。 */
    const val MAX_PAYLOAD_SIZE = MTU_SIZE - 1

    /**
     * [data]を送信用のパケット列へ分割する。各要素がGATTへの1回の書き込みに対応する。
     * [isEncrypted]は終端パケットのヘッダにだけ影響する（途中のパケットは種別を持たない）。
     * 空の[data]でも1パケット（ヘッダのみ）を返す。
     */
    fun split(
        data: ByteArray,
        isEncrypted: Boolean,
    ): List<ByteArray> {
        if (data.isEmpty()) return listOf(byteArrayOf(header(isBeginning = true, isEnd = true, isEncrypted)))
        return (data.indices step MAX_PAYLOAD_SIZE).map { offset ->
            val end = minOf(offset + MAX_PAYLOAD_SIZE, data.size)
            val headerByte =
                header(
                    isBeginning = offset == 0,
                    isEnd = end == data.size,
                    isEncrypted = isEncrypted,
                )
            byteArrayOf(headerByte) + data.copyOfRange(offset, end)
        }
    }

    private fun header(
        isBeginning: Boolean,
        isEnd: Boolean,
        isEncrypted: Boolean,
    ): Byte {
        var value = 0
        if (isBeginning) value = value or PacketFlag.BEGINNING
        if (isEnd) value = value or if (isEncrypted) PacketFlag.ENCRYPTED_END else PacketFlag.PLAINTEXT_END
        return value.toByte()
    }
}

/**
 * 受信した通知パケットを1つのメッセージへ組み立てる（BL-151）。
 *
 * 1接続につき1インスタンスを使い、[append]が終端パケットを受け取ったときだけ
 * 組み上がった[Message]を返す。先頭フラグの付いたパケットでバッファを初期化するため、
 * 途中のパケットを取りこぼしても次のメッセージから復帰できる。
 * スレッド安全ではない（GATTのコールバックは同一スレッドへ直列化される前提）。
 */
class SesameBlePacketAssembler {
    private var buffer = ByteArray(0)

    /** 組み上がったメッセージ。[isEncrypted]は終端パケットのヘッダが示した種別。 */
    data class Message(
        val payload: ByteArray,
        val isEncrypted: Boolean,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is Message &&
                        isEncrypted == other.isEncrypted &&
                        payload.contentEquals(other.payload)
                )

        override fun hashCode(): Int = 31 * payload.contentHashCode() + isEncrypted.hashCode()
    }

    /**
     * 受信した1パケット[packet]を取り込む。終端パケットなら組み上がった[Message]を、
     * まだ続きがあるならnullを返す。空のパケットは無視してnullを返す。
     */
    fun append(packet: ByteArray): Message? {
        if (packet.isEmpty()) return null
        val header = packet[0].toInt()
        if (header and PacketFlag.BEGINNING != 0) buffer = ByteArray(0)
        buffer += packet.copyOfRange(1, packet.size)
        val isEncryptedEnd = header and PacketFlag.ENCRYPTED_END != 0
        val isEnd = isEncryptedEnd || (header and PacketFlag.PLAINTEXT_END != 0)
        return if (!isEnd) {
            null
        } else {
            Message(buffer, isEncryptedEnd).also { buffer = ByteArray(0) }
        }
    }

    /** 組み立て途中のバッファを捨てる。再接続時に前の接続の残りを持ち越さないために呼ぶ。 */
    fun reset() {
        buffer = ByteArray(0)
    }
}

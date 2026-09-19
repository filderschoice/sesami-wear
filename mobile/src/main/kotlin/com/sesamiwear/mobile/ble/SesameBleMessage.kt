package com.sesamiwear.mobile.ble

import com.sesamiwear.mobile.ble.SesameBleProtocol.HISTORY_TAG_MAX_LENGTH
import com.sesamiwear.mobile.ble.SesameBleProtocol.ItemCode
import com.sesamiwear.mobile.ble.SesameBleProtocol.OpCode
import com.sesamiwear.mobile.ble.SesameBleProtocol.ResultCode

/**
 * 組み上がったメッセージ本体（復号後）の解釈と、送信コマンドの組み立て（BL-151）。
 *
 * メッセージは「先頭1バイトがop code、以降が本体」で、応答（RESPONSE）は「項目コード＋結果コード＋残り」、
 * 通知（PUBLISH）は「項目コード＋残り」という構造。未知のコードは異常系ではなくnullで返し、
 * 呼び出し側が無視できるようにする（デバイスは本アプリが使わない通知も送ってくるため）。
 *
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
object SesameBleMessage {
    /** デバイスからの応答。[resultCode]が`SUCCESS`以外なら、そのコマンドは失敗している。 */
    data class Response(
        val itemCode: ItemCode,
        val resultCode: ResultCode,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is Response &&
                        itemCode == other.itemCode &&
                        resultCode == other.resultCode &&
                        payload.contentEquals(other.payload)
                )

        override fun hashCode(): Int {
            var result = itemCode.hashCode()
            result = 31 * result + resultCode.hashCode()
            return 31 * result + payload.contentHashCode()
        }
    }

    /** デバイスからの非同期通知。ログイン直後の`INITIAL` / `MECH_STATUS` / `MECH_SETTING`を受ける。 */
    data class Publish(
        val itemCode: ItemCode,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Publish && itemCode == other.itemCode && payload.contentEquals(other.payload))

        override fun hashCode(): Int = 31 * itemCode.hashCode() + payload.contentHashCode()
    }

    /** [Response]と[Publish]のいずれか。未知のop code・項目コードは解釈せずnullにする。 */
    sealed interface Parsed {
        data class AsResponse(val response: Response) : Parsed

        data class AsPublish(val publish: Publish) : Parsed
    }

    /**
     * 復号後のメッセージ本体[data]を解釈する。
     * 長さが足りない、op codeが未知、項目コード・結果コードが本アプリの定義に無い場合はnullを返す。
     */
    fun parse(data: ByteArray): Parsed? {
        if (data.isEmpty()) return null
        return when (OpCode.fromCode(data[0].toInt() and BYTE_MASK)) {
            OpCode.RESPONSE -> parseResponse(data).let { it?.let(Parsed::AsResponse) }
            OpCode.PUBLISH -> parsePublish(data).let { it?.let(Parsed::AsPublish) }
            null -> null
        }
    }

    private fun parseResponse(data: ByteArray): Response? {
        if (data.size < RESPONSE_HEADER_SIZE) return null
        val itemCode = ItemCode.fromCode(data[1].toInt() and BYTE_MASK)
        val resultCode = ResultCode.fromCode(data[2].toInt() and BYTE_MASK)
        return if (itemCode == null || resultCode == null) {
            null
        } else {
            Response(itemCode, resultCode, data.copyOfRange(RESPONSE_HEADER_SIZE, data.size))
        }
    }

    private fun parsePublish(data: ByteArray): Publish? {
        if (data.size < PUBLISH_HEADER_SIZE) return null
        return ItemCode.fromCode(data[1].toInt() and BYTE_MASK)
            ?.let { Publish(it, data.copyOfRange(PUBLISH_HEADER_SIZE, data.size)) }
    }

    /** 送信コマンドのバイト列（項目コード1バイト＋ペイロード）。暗号化はこの結果に対して行う。 */
    fun encodeCommand(
        itemCode: ItemCode,
        payload: ByteArray = ByteArray(0),
    ): ByteArray = byteArrayOf(itemCode.code.toByte()) + payload

    /**
     * 施錠/解錠時にデバイスの履歴へ残す操作者名を、長さ1バイト＋UTF-8本体の形式へ変換する。
     * [HISTORY_TAG_MAX_LENGTH]バイトを超える場合は、マルチバイト文字の途中で切らないよう
     * 文字単位で詰めて打ち切る。
     */
    fun encodeHistoryTag(historyName: String): ByteArray {
        val encoded = truncateToUtf8Bytes(historyName, HISTORY_TAG_MAX_LENGTH)
        return byteArrayOf(encoded.size.toByte()) + encoded
    }

    private fun truncateToUtf8Bytes(
        value: String,
        maxBytes: Int,
    ): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return bytes
        // マルチバイト文字の途中で切らないよう、末尾から1文字ずつ削って収まる長さを探す。
        return (value.length - 1 downTo 1)
            .asSequence()
            .map { value.substring(0, it).toByteArray(Charsets.UTF_8) }
            .firstOrNull { it.size <= maxBytes }
            ?: ByteArray(0)
    }

    private const val BYTE_MASK = 0xFF
    private const val RESPONSE_HEADER_SIZE = 3
    private const val PUBLISH_HEADER_SIZE = 2
}

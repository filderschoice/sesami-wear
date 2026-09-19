package com.sesamiwear.mobile.ble

import com.sesamiwear.mobile.ble.SesameBleProtocol.ItemCode
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 接続の受信チャネルから、目的のメッセージが届くまで読み進める処理（BL-151）。
 *
 * デバイスは本アプリが使わない通知も送ってくるため、待っているもの以外は読み飛ばす。
 * 暗号化された通知はセッションで復号し、復号できないものは捨てて次を待つ。
 * 時間切れ・接続断はいずれも「得られなかった」としてnullを返し、例外を外へ出さない
 * （呼び出し側は経路の成否だけを知れればよく、BL-152ではWeb APIへ倒れるため）。
 */
internal object SesameBleMessageReader {
    /** [itemCode]の通知が届くまで受信し、その本体を返す。 */
    suspend fun awaitPublish(
        connection: SesameBleConnection,
        itemCode: ItemCode,
        timeoutMillis: Long,
        session: SesameBleSession? = null,
    ): ByteArray? =
        await(connection, timeoutMillis, session) { parsed ->
            (parsed as? SesameBleMessage.Parsed.AsPublish)
                ?.publish
                ?.takeIf { it.itemCode == itemCode }
                ?.payload
        }

    /** [itemCode]への応答が届くまで受信して返す。 */
    suspend fun awaitResponse(
        connection: SesameBleConnection,
        itemCode: ItemCode,
        timeoutMillis: Long,
        session: SesameBleSession? = null,
    ): SesameBleMessage.Response? =
        await(connection, timeoutMillis, session) { parsed ->
            (parsed as? SesameBleMessage.Parsed.AsResponse)
                ?.response
                ?.takeIf { it.itemCode == itemCode }
        }

    private suspend fun <T> await(
        connection: SesameBleConnection,
        timeoutMillis: Long,
        session: SesameBleSession?,
        select: (SesameBleMessage.Parsed) -> T?,
    ): T? =
        withTimeoutOrNull(timeoutMillis) {
            try {
                var selected: T? = null
                for (message in connection.incoming) {
                    val body = if (message.isEncrypted) session?.decrypt(message.payload) else message.payload
                    selected = body?.let(SesameBleMessage::parse)?.let(select)
                    if (selected != null) break
                }
                selected
            } catch (
                @Suppress("SwallowedException") e: ClosedReceiveChannelException,
            ) {
                // 接続が切れてチャネルが閉じた。時間切れと同じく「得られなかった」として扱う。
                null
            } catch (
                @Suppress("SwallowedException") e: TimeoutCancellationException,
            ) {
                null
            }
        }
}

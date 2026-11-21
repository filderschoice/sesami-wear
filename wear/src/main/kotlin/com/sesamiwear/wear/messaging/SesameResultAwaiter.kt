package com.sesamiwear.wear.messaging

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 送信後にスマホから結果（[SesameWearProtocol.PATH_COMMAND_RESULT]）が返るのを待つ（BL-312）。
 * スマホが通信できないとき、送った施錠・解錠は何も知らされずに捨てられるため、
 * 一定時間内に結果が返らなければ「届かなかった」と判断できるようにする。
 * Android依存のためユニットテスト対象外（実機で確認）。
 */
object SesameResultAwaiter {
    /** 結果を待つ時間（ミリ秒）。 */
    const val TIMEOUT_MS = 10_000L

    /**
     * [send]を実行し、結果が[TIMEOUT_MS]以内に返ればtrue、返らなければfalseを返す。
     * 取りこぼさないよう、リスナーを登録してから[send]を呼ぶ。
     */
    suspend fun sendAndAwaitResult(
        context: Context,
        send: suspend () -> Unit,
    ): Boolean {
        val received = CompletableDeferred<Unit>()
        val client = Wearable.getMessageClient(context)
        val listener =
            MessageClient.OnMessageReceivedListener { event ->
                if (event.path == SesameWearProtocol.PATH_COMMAND_RESULT) received.complete(Unit)
            }
        client.addListener(listener)
        try {
            send()
            return withTimeoutOrNull(TIMEOUT_MS) { received.await() } != null
        } finally {
            client.removeListener(listener)
        }
    }
}

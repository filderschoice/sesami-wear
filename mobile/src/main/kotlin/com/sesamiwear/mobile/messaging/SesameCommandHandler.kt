package com.sesamiwear.mobile.messaging

import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameApiException
import com.sesamiwear.core.api.SesameCommand

/**
 * Wear側から受信したメッセージパスをSesameコマンドへ変換し、Sesame APIを呼び出して結果を返す。
 * Android（WearableListenerService）非依存のため、ユニットテストで検証できる。
 */
class SesameCommandHandler(
    private val apiClient: SesameApiClient,
    private val secretKey: ByteArray,
) {
    suspend fun handle(path: String): SesameCommandResult {
        val command = commandForPath(path) ?: return SesameCommandResult.FAILURE
        return try {
            apiClient.sendCommand(command, secretKey)
            SesameCommandResult.SUCCESS
        } catch (
            @Suppress("SwallowedException") e: SesameApiException,
        ) {
            // Data Layer APIへは成功/失敗の1バイトのみを返す仕様のため、例外の詳細は伝搬しない。
            SesameCommandResult.FAILURE
        }
    }

    private fun commandForPath(path: String): SesameCommand? =
        when (path) {
            SesameWearProtocol.PATH_LOCK_REQUEST -> SesameCommand.LOCK
            SesameWearProtocol.PATH_UNLOCK_REQUEST -> SesameCommand.UNLOCK
            else -> null
        }
}

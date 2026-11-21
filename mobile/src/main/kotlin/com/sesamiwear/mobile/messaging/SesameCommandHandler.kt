package com.sesamiwear.mobile.messaging

import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameApiException
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.api.SesameCommandReceipt

/**
 * Wear側から受信したメッセージパスをSesameコマンドへ変換し、Sesame APIを呼び出して結果を返す。
 * Android（WearableListenerService）非依存のため、ユニットテストで検証できる。
 *
 * @param onFailure API呼び出しが失敗したときに1度だけ呼ばれる。失敗の理由を呼び出し元
 * （[com.sesamiwear.mobile.command.SesameDeviceCommandExecutor]）がlogcatへ残すための口で、
 * 既定では何もしない（BL-139）。
 * @param onSuccess HTTP 2xxを受け取ったときに1度だけ呼ばれる診断ログ用の口。既定では何もしない。
 * 例外を投げても握りつぶし、送信結果（成功）には影響させない。
 */
class SesameCommandHandler(
    private val apiClient: SesameApiClient,
    private val secretKey: ByteArray,
    private val onFailure: (SesameApiException) -> Unit = {},
    private val onSuccess: (SesameCommandReceipt) -> Unit = {},
) {
    suspend fun handle(path: String): SesameCommandResult {
        val command = commandForPath(path) ?: return SesameCommandResult.FAILURE
        return execute(command)
    }

    /** [command]をSesame APIへ送信する。Data Layerを経由しない実行口（BL-120）からも使う。 */
    suspend fun execute(command: SesameCommand): SesameCommandResult =
        try {
            val receipt = apiClient.sendCommand(command, secretKey)
            // ログ用の口が何を投げても、送信は成功している。送信結果へ影響させない。
            runCatching { onSuccess(receipt) }
            SesameCommandResult.SUCCESS
        } catch (e: SesameApiException) {
            // Data Layer APIへは成功/失敗の1バイトのみを返す仕様のため、例外の詳細は伝搬しない。
            // 切り分けに必要な情報は[onFailure]経由でlogcatへ残す（BL-139）。
            onFailure(e)
            SesameCommandResult.FAILURE
        }

    private fun commandForPath(path: String): SesameCommand? =
        when (path) {
            SesameWearProtocol.PATH_LOCK_REQUEST -> SesameCommand.LOCK
            SesameWearProtocol.PATH_UNLOCK_REQUEST -> SesameCommand.UNLOCK
            else -> null
        }
}

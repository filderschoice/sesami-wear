package com.sesamiwear.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * CANDY HOUSE Sesame API（新WebAPI, sesame2系）のクライアント。
 * 状態取得（GET）はapikeyのみで可能。施錠/解錠（POST）はsecretKeyによるAES-CMAC署名が必須。
 *
 * 失敗は種類を問わず[SesameApiException]へ正規化して送出する（[asApiCall]、BL-133）。
 */
class SesameApiClient(
    private val uuid: String,
    private val apiKey: String,
    private val httpClient: OkHttpClient = sharedHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStatus(): SesameStatus =
        withContext(Dispatchers.IO) {
            asApiCall {
                val request =
                    Request.Builder()
                        .url("$baseUrl/$uuid")
                        .get()
                        .addHeader(HEADER_API_KEY, apiKey)
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    val body =
                        response.body?.string()
                            ?: throw SesameApiException("Empty response body (HTTP ${response.code})")
                    if (!response.isSuccessful) {
                        throw SesameApiException("Sesame API error: HTTP ${response.code} - $body")
                    }
                    json.decodeFromString(SesameStatus.serializer(), body)
                }
            }
        }

    /**
     * 施錠/解錠コマンドを送信する。
     *
     * @param secretKey Sesame QRコードから取得したコマンド署名用の鍵。
     * @param historyTag Sesameの操作履歴に残るタグ文字列。
     */
    suspend fun sendCommand(
        command: SesameCommand,
        secretKey: ByteArray,
        historyTag: String = DEFAULT_HISTORY_TAG,
    ): Unit =
        withContext(Dispatchers.IO) {
            asApiCall {
                val requestPayload =
                    SesameCommandRequest(
                        cmd = command.code,
                        history = Base64.getEncoder().encodeToString(historyTag.toByteArray()),
                        sign = SesameCommandSigner.sign(secretKey),
                    )
                val request =
                    Request.Builder()
                        .url("$baseUrl/$uuid/cmd")
                        .post(
                            json.encodeToString(SesameCommandRequest.serializer(), requestPayload)
                                .toRequestBody(JSON_MEDIA_TYPE),
                        )
                        .addHeader(HEADER_API_KEY, apiKey)
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        throw SesameApiException("Sesame API error: HTTP ${response.code} - $body")
                    }
                }
            }
        }

    /**
     * [block]の失敗を[SesameApiException]へ正規化する（BL-133）。対象は通信の失敗（[IOException]）、
     * 想定外の応答本文（[SerializationException]）、接続先URLが不正な場合（[IllegalArgumentException]）で、
     * いずれも呼び出し側は「APIを呼べなかった」として同じ扱いをすればよい。
     * [block]が投げた[SesameApiException]（HTTPエラー応答）はそのまま通す。
     *
     * 例外メッセージへは原因例外の型名だけを載せる。uuidを含むURLや応答内容がログへ流れないようにするため。
     * コルーチンのキャンセル（`CancellationException`は`IllegalStateException`の派生）は捕捉対象に
     * 含まれないため、従来どおり呼び出し元へ伝わる。
     */
    private fun <T> asApiCall(block: () -> T): T {
        val failure: Exception =
            try {
                return block()
            } catch (e: IOException) {
                e
            } catch (e: SerializationException) {
                e
            } catch (e: IllegalArgumentException) {
                e
            }
        throw SesameApiException("Sesame API call failed: ${failure.javaClass.simpleName}", failure)
    }

    @Serializable
    private data class SesameCommandRequest(
        val cmd: Int,
        val history: String,
        val sign: String,
    )

    companion object {
        private const val DEFAULT_BASE_URL = "https://app.candyhouse.co/api/sesame2"
        private const val DEFAULT_HISTORY_TAG = "sesami-wear"
        private const val HEADER_API_KEY = "x-api-key"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // 1回の呼び出しに与える上限（BL-133 / BL-137）。ウィジェットのタップはBroadcastReceiverの
        // goAsyncで実行し、Glanceの`actionSendBroadcast`が`FLAG_RECEIVER_FOREGROUND`を付けるため、
        // 実行時間の制限は約10秒（バックグラウンド受信の60秒ではない）。これを超えるとプロセスごと
        // ANRで強制終了され、「通信中...」の表示が解除されないまま固着する（実機で確認、BL-137）。
        // OkHttpの既定（接続・読み書きとも10秒）では間に合わないため、制限内へ収まる値にしている。
        // 接続・読み書き個別の上限に加えて全体の上限（callTimeout）も設定し、再試行が重なっても
        // 待ち続けないようにする。
        private const val CONNECT_TIMEOUT_SECONDS = 3L
        private const val IO_TIMEOUT_SECONDS = 3L
        private const val CALL_TIMEOUT_SECONDS = 6L

        /**
         * 既定のHTTPクライアント。[OkHttpClient]はスレッドプールとコネクションプールを持つため、
         * 呼び出しごとに生成せずプロセス内で共有する。テストは独自のクライアントを渡す。
         */
        private val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }
}

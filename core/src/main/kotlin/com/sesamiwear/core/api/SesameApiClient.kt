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
import okhttp3.Response
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
                    if (!response.isSuccessful) throw httpErrorOf(response.code)
                    val body =
                        response.body?.string()
                            ?: throw SesameApiException("Empty response body (HTTP ${response.code})")
                    json.decodeFromString(SesameStatus.serializer(), body)
                }
            }
        }

    /**
     * 施錠/解錠コマンドを送信する。
     *
     * @param secretKey Sesame QRコードから取得したコマンド署名用の鍵。
     * @param historyTag Sesameの操作履歴に残るタグ文字列。
     * @return HTTP 2xxを受け取った事実（ステータスコードと応答本文の長さ。長さは最大64KiBまで数える）。クラウドが受理しただけで
     * 実機の動作は保証しない。診断ログ用で、呼び出し側の成否判定には影響しない。
     */
    suspend fun sendCommand(
        command: SesameCommand,
        secretKey: ByteArray,
        historyTag: String = DEFAULT_HISTORY_TAG,
    ): SesameCommandReceipt =
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
                    if (!response.isSuccessful) throw httpErrorOf(response.code)
                    bodyReceiptOf(response)
                }
            }
        }

    /**
     * 応答本文のバイト数を数えて控えにする。本文の中身は保持しない。成功の返却を遅らせず、メモリも使わないよう、
     * 最大[MAX_BODY_COUNT_BYTES]バイトまで読み捨てて数え、超えたら打ち切る（[SesameCommandReceipt.bodyTruncated]）。
     * 読み取りに失敗しても成功扱い（従来どおり本文は使わない）のまま、長さだけ不明（null）にする。
     */
    private fun bodyReceiptOf(response: Response): SesameCommandReceipt {
        val total =
            try {
                countBodyBytes(response)
            } catch (_: IOException) {
                return SesameCommandReceipt(response.code, bodyLength = null)
            }
        return if (total > MAX_BODY_COUNT_BYTES) {
            SesameCommandReceipt(response.code, bodyLength = MAX_BODY_COUNT_BYTES, bodyTruncated = true)
        } else {
            SesameCommandReceipt(response.code, bodyLength = total)
        }
    }

    /** 本文を読み捨てて数える。上限を超えた時点で読むのをやめる（返す値は上限を超えうる）。 */
    private fun countBodyBytes(response: Response): Int {
        val stream = response.body?.byteStream() ?: return 0
        val buffer = ByteArray(BODY_READ_CHUNK_BYTES)
        var total = 0
        stream.use {
            while (total <= MAX_BODY_COUNT_BYTES) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
            }
        }
        return total
    }

    /**
     * HTTPのエラー応答を[SesameApiException]へ変換する。メッセージへは応答本文を含めない（BL-133 / BL-139）。
     * 本文には認証エラーの詳細など運用上の情報が入りうるため、ログへ流れる可能性のある[Exception.message]
     * からは除き、呼び出し側が失敗の種類を判断するための[SesameApiException.httpStatusCode]だけを残す。
     */
    private fun httpErrorOf(statusCode: Int): SesameApiException =
        SesameApiException("Sesame API error: HTTP $statusCode", httpStatusCode = statusCode)

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

        // 応答本文の長さを数える上限（診断ログ用）。超えたら読むのを打ち切る。
        private const val MAX_BODY_COUNT_BYTES = 64 * 1024
        private const val BODY_READ_CHUNK_BYTES = 8 * 1024
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

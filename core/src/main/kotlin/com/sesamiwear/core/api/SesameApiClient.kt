package com.sesamiwear.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * CANDY HOUSE Sesame API（新WebAPI, sesame2系）のクライアント。
 * 状態取得（GET）はapikeyのみで可能。施錠/解錠（POST）はsecretKeyによるAES-CMAC署名が必須。
 */
class SesameApiClient(
    private val uuid: String,
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStatus(): SesameStatus =
        withContext(Dispatchers.IO) {
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
    }
}

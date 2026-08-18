package com.sesamiwear.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * CANDY HOUSE Sesame API（新WebAPI, sesame2系）のクライアント。
 * 状態取得（GET）はapikeyのみで可能。施錠/解錠（POST、AES-CMAC署名必須）はBL-004で追加する。
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

    companion object {
        private const val DEFAULT_BASE_URL = "https://app.candyhouse.co/api/sesame2"
        private const val HEADER_API_KEY = "x-api-key"
    }
}

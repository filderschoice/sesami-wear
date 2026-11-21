package com.sesamiwear.mobile.messaging

import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameApiException
import com.sesamiwear.core.api.SesameCommandReceipt
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SesameCommandHandlerTest {
    private lateinit var server: MockWebServer
    private lateinit var apiClient: SesameApiClient
    private lateinit var handler: SesameCommandHandler

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiClient =
            SesameApiClient(
                uuid = "test-uuid",
                apiKey = "test-api-key",
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
            )
        handler = SesameCommandHandler(apiClient, secretKey = ByteArray(16))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lock request path succeeds when API responds successfully`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val result = handler.handle(SesameWearProtocol.PATH_LOCK_REQUEST)

            assertEquals(SesameCommandResult.SUCCESS, result)
            assertEquals("/test-uuid/cmd", server.takeRequest().path)
        }

    @Test
    fun `unlock request path succeeds when API responds successfully`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))

            val result = handler.handle(SesameWearProtocol.PATH_UNLOCK_REQUEST)

            assertEquals(SesameCommandResult.SUCCESS, result)
        }

    @Test
    fun `returns failure when API responds with an error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_FORBIDDEN))

            val result = handler.handle(SesameWearProtocol.PATH_LOCK_REQUEST)

            assertEquals(SesameCommandResult.FAILURE, result)
        }

    @Test
    fun `returns failure for an unknown path without calling the API`() =
        runTest {
            val result = handler.handle("/unknown/path")

            assertEquals(SesameCommandResult.FAILURE, result)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `onSuccess is called with the receipt on success`() =
        runTest {
            server.enqueue(MockResponse().setBody("ok").setResponseCode(HTTP_OK))
            val receipts = mutableListOf<SesameCommandReceipt>()
            val h = handlerWith(onSuccess = { receipts.add(it) })

            val result = h.handle(SesameWearProtocol.PATH_LOCK_REQUEST)

            assertEquals(SesameCommandResult.SUCCESS, result)
            assertEquals(listOf(SesameCommandReceipt(httpStatusCode = HTTP_OK, bodyLength = 2)), receipts)
        }

    @Test
    fun `an exception thrown by onSuccess does not change a successful result`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            var failures = 0
            val h =
                handlerWith(onSuccess = { error("log failure") }, onFailure = { failures++ })

            val result = h.handle(SesameWearProtocol.PATH_LOCK_REQUEST)

            assertEquals(SesameCommandResult.SUCCESS, result)
            assertEquals(0, failures)
        }

    private fun handlerWith(
        onSuccess: (SesameCommandReceipt) -> Unit,
        onFailure: (SesameApiException) -> Unit = {},
    ): SesameCommandHandler =
        SesameCommandHandler(
            apiClient = apiClient,
            secretKey = ByteArray(16),
            onFailure = onFailure,
            onSuccess = onSuccess,
        )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
    }
}

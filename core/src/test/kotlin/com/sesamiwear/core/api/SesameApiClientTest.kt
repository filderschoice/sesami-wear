package com.sesamiwear.core.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SesameApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SesameApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            SesameApiClient(
                uuid = "test-uuid",
                apiKey = "test-api-key",
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses locked status response`() =
        runTest {
            val responseJson =
                """
                {
                  "batteryVoltage": 5.85,
                  "position": 42,
                  "CHSesame2Status": "locked"
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(responseJson).setResponseCode(HTTP_OK))

            val status = client.getStatus()

            assertEquals(5.85, status.batteryVoltage, DELTA)
            assertEquals(42, status.position)
            assertEquals("locked", status.lockStatus)
            assertEquals(true, status.isInLockRange)
            assertEquals(false, status.isInUnlockRange)

            val recordedRequest = server.takeRequest()
            assertEquals("test-api-key", recordedRequest.headers["x-api-key"])
            assertEquals("/test-uuid", recordedRequest.path)
        }

    @Test
    fun `derives isInUnlockRange when status is not locked`() =
        runTest {
            val responseJson =
                """
                {
                  "batteryVoltage": 5.2,
                  "position": -10,
                  "CHSesame2Status": "unlocked"
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(responseJson).setResponseCode(HTTP_OK))

            val status = client.getStatus()

            assertEquals(false, status.isInLockRange)
            assertEquals(true, status.isInUnlockRange)
        }

    @Test
    fun `ignores unknown response fields`() =
        runTest {
            val responseJson =
                """
                {
                  "batteryVoltage": 5.85,
                  "position": 42,
                  "CHSesame2Status": "locked",
                  "isBatteryCritical": false,
                  "unexpectedField": "should be ignored"
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(responseJson).setResponseCode(HTTP_OK))

            val status = client.getStatus()

            assertEquals(true, status.isInLockRange)
        }

    @Test
    fun `throws SesameApiException on http error`() =
        runTest {
            server.enqueue(
                MockResponse().setBody("""{"code":"unauthorized"}""").setResponseCode(HTTP_UNAUTHORIZED),
            )

            var thrown: SesameApiException? = null
            try {
                client.getStatus()
            } catch (e: SesameApiException) {
                thrown = e
            }
            assertNotNull(thrown)
        }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val DELTA = 0.0001
    }
}

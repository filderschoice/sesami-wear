package com.sesamiwear.core.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

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
            // 失敗の種類を呼び出し側が区別できるようステータスコードを保持する（BL-139）。
            assertEquals(HTTP_UNAUTHORIZED, thrown?.httpStatusCode)
            // 応答本文はメッセージへ載せない（ログへ流れうるため、BL-133 / BL-139）。
            assertFalse(thrown?.message.orEmpty().contains("unauthorized"))
        }

    @Test
    fun `http error on send command keeps the status code without the response body`() =
        runTest {
            server.enqueue(
                MockResponse().setBody("""{"Message":"explicit deny"}""").setResponseCode(HTTP_FORBIDDEN),
            )
            val dummySecretKey = ByteArray(16)

            var thrown: SesameApiException? = null
            try {
                client.sendCommand(SesameCommand.LOCK, dummySecretKey)
            } catch (e: SesameApiException) {
                thrown = e
            }
            assertEquals(HTTP_FORBIDDEN, thrown?.httpStatusCode)
            assertFalse(thrown?.message.orEmpty().contains("explicit deny"))
        }

    @Test
    fun `connection failure has no http status code`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            var thrown: SesameApiException? = null
            try {
                client.getStatus()
            } catch (e: SesameApiException) {
                thrown = e
            }
            assertNull(thrown?.httpStatusCode)
        }

    @Test
    fun `sends lock command with cmd, base64 history and hex sign`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(HTTP_OK))
            val dummySecretKey = ByteArray(16) // AES-CMAC鍵長(16バイト)を満たすダミー鍵、実資格情報ではない

            client.sendCommand(SesameCommand.LOCK, dummySecretKey)

            val recordedRequest = server.takeRequest()
            assertEquals("POST", recordedRequest.method)
            assertEquals("/test-uuid/cmd", recordedRequest.path)
            assertEquals("test-api-key", recordedRequest.headers["x-api-key"])

            val bodyJson = Json.parseToJsonElement(recordedRequest.body.readUtf8()).jsonObject
            assertEquals(LOCK_CMD_CODE, bodyJson.getValue("cmd").jsonPrimitive.int)
            assertEquals(SIGN_HEX_LENGTH, bodyJson.getValue("sign").jsonPrimitive.content.length)
        }

    @Test
    fun `throws SesameApiException when send command fails`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"code":"forbidden"}""").setResponseCode(HTTP_FORBIDDEN))
            val dummySecretKey = ByteArray(16)

            var thrown: SesameApiException? = null
            try {
                client.sendCommand(SesameCommand.UNLOCK, dummySecretKey)
            } catch (e: SesameApiException) {
                thrown = e
            }
            assertNotNull(thrown)
        }

    @Test
    fun `throws SesameApiException when the connection fails`() =
        runTest {
            // 圏外・名前解決失敗と同じく、OkHttpがIOExceptionを投げる状況（BL-133）。
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            var thrown: SesameApiException? = null
            try {
                client.getStatus()
            } catch (e: SesameApiException) {
                thrown = e
            }
            assertNotNull(thrown)
            assertTrue(rootCauseOf(thrown) is IOException)
        }

    @Test
    fun `throws SesameApiException when the response body is not valid json`() =
        runTest {
            server.enqueue(MockResponse().setBody("<html>maintenance</html>").setResponseCode(HTTP_OK))

            var thrown: SesameApiException? = null
            try {
                client.getStatus()
            } catch (e: SesameApiException) {
                thrown = e
            }
            assertNotNull(thrown)
            assertTrue(rootCauseOf(thrown) is SerializationException)
        }

    @Test
    fun `throws SesameApiException when sending a command cannot reach the server`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val dummySecretKey = ByteArray(16)

            var thrown: SesameApiException? = null
            try {
                client.sendCommand(SesameCommand.LOCK, dummySecretKey)
            } catch (e: SesameApiException) {
                thrown = e
            }
            assertNotNull(thrown)
            assertTrue(rootCauseOf(thrown) is IOException)
        }

    /**
     * 原因例外をたどって最も内側の例外を返す。kotlinx.coroutinesは`withContext`をまたぐ例外を
     * スタックトレース復元のために複製し、複製の`cause`へ元の例外を入れるため、`cause`を1段
     * 見るだけでは正規化前の例外にたどり着けない。
     */
    private fun rootCauseOf(throwable: Throwable?): Throwable? {
        var current = throwable?.cause ?: return null
        while (current.cause != null) current = requireNotNull(current.cause)
        return current
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val DELTA = 0.0001
        const val LOCK_CMD_CODE = 82
        const val SIGN_HEX_LENGTH = 32
    }
}

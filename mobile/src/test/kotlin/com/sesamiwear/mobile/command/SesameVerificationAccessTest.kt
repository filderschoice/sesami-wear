package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutorTestFixture.Companion.validCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SesameVerificationAccess]の単体検証（BL-293）。状態取得が想定外の例外を投げても送信成功後に
 * 例外を漏らさず「照合できなかった」へ倒すこと、取り消しは握らず伝えることを固定する。
 */
class SesameVerificationAccessTest {
    private val credentials = validCredentials
    private val responseLogs = mutableListOf<String>()

    private fun api(clientFactory: (SesameCredentials) -> SesameApiClient) =
        SesameApiAccess(clientFactory = clientFactory, logResponse = { responseLogs += it })

    private fun verification() = SesameVerificationAccess(isEnabled = { true }, pause = {})

    @Test
    fun `an unexpected exception while fetching is unverified and leaks nothing`() =
        runTest {
            val api = api { error("secret-looking-detail") }

            val result = verification().verify(api, credentials, SesameCommand.LOCK, sentAtMillis = 0L)

            assertEquals(SesameVerificationAccess.Result.Unverified, result)
            assertTrue(responseLogs.contains("verify status fetch: unexpected error"))
            assertTrue(responseLogs.none { it.contains("secret-looking-detail") })
        }

    @Test
    fun `a cancellation while fetching is rethrown with a cancelled log`() =
        runTest {
            val api = api { throw CancellationException("cancelled") }

            val failure =
                runCatching { verification().verify(api, credentials, SesameCommand.UNLOCK, sentAtMillis = 0L) }
                    .exceptionOrNull()

            assertTrue(failure is CancellationException)
            assertTrue(responseLogs.contains("verify UNLOCK: cancelled"))
        }
}

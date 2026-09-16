package com.sesamiwear.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class EntryPointGuardTest {
    @Test
    fun `returns true and does not report failure when the block succeeds`() =
        runTest {
            val failures = mutableListOf<String>()
            var executed = false

            val result = EntryPointGuard.run(onFailure = { failures += it }) { executed = true }

            assertTrue(result)
            assertTrue(executed)
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `swallows the exception and reports only its type name`() =
        runTest {
            val failures = mutableListOf<String>()

            val result =
                EntryPointGuard.run(onFailure = { failures += it }) {
                    // 接続先URL（uuidを含む）が例外メッセージへ入りうることを模した失敗。
                    throw IOException("https://example.invalid/uuid-front")
                }

            assertFalse(result)
            assertEquals(listOf("IOException"), failures)
        }

    @Test
    fun `caller continues after the guarded block fails`() =
        runTest {
            // 受信 → 実行（失敗）→ 後続処理（goAsyncの終了など）、の順序で後続が実行されることを示す。
            val steps = mutableListOf<String>()

            steps += "received"
            EntryPointGuard.run(onFailure = { steps += "failed" }) { throw IllegalStateException("boom") }
            steps += "continued"

            assertEquals(listOf("received", "failed", "continued"), steps)
        }

    @Test(expected = CancellationException::class)
    fun `does not swallow coroutine cancellation`() =
        runTest {
            EntryPointGuard.run(onFailure = {}) { throw CancellationException("cancelled") }
        }
}

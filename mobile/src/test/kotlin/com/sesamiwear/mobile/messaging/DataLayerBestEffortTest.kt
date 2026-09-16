package com.sesamiwear.mobile.messaging

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataLayerBestEffortTest {
    @Test
    fun `returns true and does not report failure when block succeeds`() =
        runTest {
            val failures = mutableListOf<Int>()
            var executed = false

            val result = DataLayerBestEffort.run(onFailure = { failures += it }) { executed = true }

            assertTrue(result)
            assertTrue(executed)
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `swallows ApiException and reports its status code`() =
        runTest {
            val failures = mutableListOf<Int>()

            val result =
                DataLayerBestEffort.run(onFailure = { failures += it }) {
                    throw ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))
                }

            assertFalse(result)
            assertEquals(listOf(CommonStatusCodes.API_NOT_CONNECTED), failures)
        }

    @Test
    fun `caller continues after the Data Layer call fails`() =
        runTest {
            // 資格情報の保存 → ウォッチ同期（失敗）→ 後続処理、の順序で後続処理が実行されることを示す。
            val steps = mutableListOf<String>()

            steps += "saved"
            DataLayerBestEffort.run(onFailure = { steps += "sync-failed" }) {
                throw ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED))
            }
            steps += "continued"

            assertEquals(listOf("saved", "sync-failed", "continued"), steps)
        }

    @Test
    fun `swallows non-ApiException failures as an unknown status code`() =
        runTest {
            val failures = mutableListOf<Int>()

            val result =
                DataLayerBestEffort.run(onFailure = { failures += it }) {
                    throw IllegalStateException("Play services unavailable")
                }

            assertFalse(result)
            assertEquals(listOf(DataLayerBestEffort.UNKNOWN_STATUS_CODE), failures)
        }

    @Test(expected = CancellationException::class)
    fun `does not swallow coroutine cancellation`() =
        runTest {
            DataLayerBestEffort.run(onFailure = {}) { throw CancellationException("cancelled") }
        }
}

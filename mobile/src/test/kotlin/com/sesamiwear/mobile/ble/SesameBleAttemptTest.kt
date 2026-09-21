package com.sesamiwear.mobile.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLEの失敗の段階を経路選択の2値へ落とす判定の検証（BL-191）。
 * 「圏内にいる証拠があるか」で、次の操作がBLEを再試行するかどうかが決まる。
 */
class SesameBleAttemptTest {
    @Test
    fun `a successful result carries the value`() {
        val attempt = SesameBleClient.Result.SUCCESS.toAttempt(VALUE)
        assertEquals(VALUE, attempt.value)
        assertNull(attempt.failure)
        assertTrue(attempt.foundInRange)
    }

    @Test
    fun `a device that was not found is treated as out of range`() {
        val attempt = SesameBleClient.Result.NOT_FOUND.toAttempt<String>(null)
        assertEquals(SesameBleFailure.NOT_REACHED, attempt.failure)
        assertFalse(attempt.foundInRange)
    }

    @Test
    fun `missing permissions are treated as out of range`() {
        // 権限が無い間は試し続けても成功しないため、BLEを先に試させない。
        val attempt = SesameBleClient.Result.PERMISSION_DENIED.toAttempt<String>(null)
        assertEquals(SesameBleFailure.NOT_REACHED, attempt.failure)
    }

    @Test
    fun `a failure after the device was found is treated as in range`() {
        listOf(
            SesameBleClient.Result.CONNECTION_FAILED,
            SesameBleClient.Result.LOGIN_FAILED,
            SesameBleClient.Result.COMMAND_REJECTED,
        ).forEach { result ->
            val attempt = result.toAttempt<String>(null)
            assertEquals(result.name, SesameBleFailure.REACHED_BUT_FAILED, attempt.failure)
            assertTrue(result.name, attempt.foundInRange)
        }
    }

    @Test
    fun `a success without a value counts as a failure in range`() {
        // 成功したのに値を取り出せない場合（機構状態の通知が届かない等）は、
        // 呼び出し側がWeb APIへ倒せるよう失敗として扱う。届いてはいるので圏内のまま。
        val attempt = SesameBleClient.Result.SUCCESS.toAttempt<String>(null)
        assertEquals(SesameBleFailure.REACHED_BUT_FAILED, attempt.failure)
        assertTrue(attempt.foundInRange)
    }

    @Test
    fun `the default attempt is out of range`() {
        val attempt = SesameBleAttempt.notReached<String>()
        assertNull(attempt.value)
        assertEquals(SesameBleFailure.NOT_REACHED, attempt.failure)
        assertFalse(attempt.foundInRange)
    }

    private companion object {
        const val VALUE = "ok"
    }
}

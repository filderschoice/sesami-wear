package com.sesamiwear.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 送信後の状態照合（BL-293）の判定の検証。応答のtimestampが送信時刻より古い値は採用せず、
 * 照合できない場合は「未反映」とは断定しない（安全側）ことを固定する。
 */
class SesameCommandVerificationTest {
    private val sentAtMillis = 1_700_000_005_500L

    private fun status(
        lockStatus: String,
        timestamp: Long?,
    ) = SesameStatus(batteryVoltage = 5.8, position = 11, lockStatus = lockStatus, timestamp = timestamp)

    private fun judge(
        command: SesameCommand,
        lockStatus: String,
        timestamp: Long?,
    ) = SesameCommandVerification.judge(command, status(lockStatus, timestamp), sentAtMillis)

    @Test
    fun `lock command and a locked fresh status are confirmed`() {
        val verdict = judge(SesameCommand.LOCK, "locked", 1_700_000_009L)
        assertEquals(SesameCommandVerification.Verdict.CONFIRMED, verdict)
    }

    @Test
    fun `unlock command and an unlocked fresh status are confirmed`() {
        val verdict =
            judge(SesameCommand.UNLOCK, "unlocked", 1_700_000_009L)
        assertEquals(SesameCommandVerification.Verdict.CONFIRMED, verdict)
    }

    @Test
    fun `lock command and an unlocked fresh status is not reflected`() {
        val verdict =
            judge(SesameCommand.LOCK, "unlocked", 1_700_000_009L)
        assertEquals(SesameCommandVerification.Verdict.NOT_REFLECTED, verdict)
    }

    @Test
    fun `unlock command and a locked fresh status is not reflected`() {
        val verdict = judge(SesameCommand.UNLOCK, "locked", 1_700_000_009L)
        assertEquals(SesameCommandVerification.Verdict.NOT_REFLECTED, verdict)
    }

    @Test
    fun `a status older than the send time is unverifiable even if it disagrees`() {
        val verdict = judge(SesameCommand.LOCK, "unlocked", 1_700_000_004L)
        assertEquals(SesameCommandVerification.Verdict.UNVERIFIABLE, verdict)
    }

    @Test
    fun `a status older than the send time is unverifiable even if it agrees`() {
        val verdict = judge(SesameCommand.LOCK, "locked", 1_700_000_004L)
        assertEquals(SesameCommandVerification.Verdict.UNVERIFIABLE, verdict)
    }

    @Test
    fun `a timestamp in the same second as the send time is fresh`() {
        val verdict = judge(SesameCommand.LOCK, "unlocked", 1_700_000_005L)
        assertEquals(SesameCommandVerification.Verdict.NOT_REFLECTED, verdict)
    }

    @Test
    fun `a missing timestamp cannot be judged`() {
        val verdict = judge(SesameCommand.LOCK, "unlocked", null)
        assertEquals(SesameCommandVerification.Verdict.UNVERIFIABLE, verdict)
    }

    @Test
    fun `a timestamp in milliseconds is compared on the same scale`() {
        val fresh = status("unlocked", 1_700_000_009_000L)
        val stale = status("unlocked", 1_700_000_004_000L)
        assertEquals(
            SesameCommandVerification.Verdict.NOT_REFLECTED,
            SesameCommandVerification.judge(SesameCommand.LOCK, fresh, sentAtMillis),
        )
        assertEquals(
            SesameCommandVerification.Verdict.UNVERIFIABLE,
            SesameCommandVerification.judge(SesameCommand.LOCK, stale, sentAtMillis),
        )
    }

    @Test
    fun `the status response keeps the timestamp and tolerates its absence`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val withTimestamp =
            json.decodeFromString(
                SesameStatus.serializer(),
                """{"batteryVoltage":5.8,"position":11,"CHSesame2Status":"locked","timestamp":1700000009}""",
            )
        val without =
            json.decodeFromString(
                SesameStatus.serializer(),
                """{"batteryVoltage":5.8,"position":11,"CHSesame2Status":"locked"}""",
            )
        assertEquals(1_700_000_009L, withTimestamp.timestamp)
        assertNull(without.timestamp)
    }
}

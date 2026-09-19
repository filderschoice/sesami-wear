package com.sesamiwear.mobile.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MECH_STATUS`通知の解釈の検証（BL-151）。
 * バイト列は`meronepy/gomalock`（MIT）の`Sesame5MechStatus`と同じ`<HhhB`の並びで構成している。
 */
class SesameBleMechStatusTest {
    @Test
    fun `parses a locked status`() {
        // battery=2900(5.8V) target=-1 position=254 flags=IS_IN_LOCK_RANGE|IS_STOP
        val status = SesameBleMechStatus.parse(hexToBytes("540BFFFFFE0012"))!!
        assertTrue(status.isInLockRange)
        assertFalse(status.isInUnlockRange)
        assertFalse(status.isBatteryCritical)
        assertEquals(-1, status.target)
        assertEquals(254, status.position)
        assertEquals(5.8, status.batteryVoltage, 1e-9)
        assertEquals(91, status.batteryPercentage)
    }

    @Test
    fun `parses an unlocked status with a negative position`() {
        // battery=2500(5.0V) target=-1 position=-32 flags=IS_IN_UNLOCK_RANGE|IS_STOP
        val status = SesameBleMechStatus.parse(hexToBytes("C409FFFFE0FF14"))!!
        assertFalse(status.isInLockRange)
        assertTrue(status.isInUnlockRange)
        assertEquals(-32, status.position)
        assertEquals(7, status.batteryPercentage)
    }

    @Test
    fun `parses the battery critical flag`() {
        val status = SesameBleMechStatus.parse(hexToBytes("FC080000000020"))!!
        assertTrue(status.isBatteryCritical)
        assertEquals(4.6, status.batteryVoltage, 1e-9)
        assertEquals(0, status.batteryPercentage)
    }

    @Test
    fun `reports neither range when the lock is partially open`() {
        // flags=IS_STOP のみ。施錠範囲でも解錠範囲でもない（一部解錠）状態。
        val status = SesameBleMechStatus.parse(hexToBytes("540BFFFF800010"))!!
        assertFalse(status.isInLockRange)
        assertFalse(status.isInUnlockRange)
    }

    @Test
    fun `clamps the battery percentage above the highest voltage level`() {
        // battery=3000 → 6.0V（テーブルの上限5.85Vを超える）
        val status = SesameBleMechStatus.parse(hexToBytes("B80B0000000002"))!!
        assertEquals(100, status.batteryPercentage)
    }

    @Test
    fun `returns null when the payload length does not match`() {
        assertNull(SesameBleMechStatus.parse(ByteArray(0)))
        assertNull(SesameBleMechStatus.parse(ByteArray(SesameBleMechStatus.PAYLOAD_SIZE - 1)))
        assertNull(SesameBleMechStatus.parse(ByteArray(SesameBleMechStatus.PAYLOAD_SIZE + 1)))
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

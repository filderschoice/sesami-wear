package com.sesamiwear.mobile.ble

import com.sesamiwear.core.display.SesameBleConnectionLabel.State
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLE到達実績にもとづく経路選択の検証（BL-152）。
 * 「直近に到達できたときだけBLEを先に試す」ことと、到達確認のスキャンを行う条件を固定する。
 */
class SesameBleReachabilityTest {
    private val store = InMemoryKeyValueStore()
    private val reachability = SesameBleReachability(store)

    @Test
    fun `a device with no history starts with the web api`() {
        assertFalse(reachability.preferBle(UUID, NOW))
    }

    @Test
    fun `ble is preferred right after a successful ble operation`() {
        reachability.record(UUID, NOW, reachable = true)
        assertTrue(reachability.preferBle(UUID, NOW))
    }

    @Test
    fun `ble stays preferred until the ttl elapses`() {
        reachability.record(UUID, NOW, reachable = true)
        assertTrue(reachability.preferBle(UUID, NOW + SesameBleReachability.REACHABLE_TTL_MILLIS))
        assertFalse(reachability.preferBle(UUID, NOW + SesameBleReachability.REACHABLE_TTL_MILLIS + 1))
    }

    @Test
    fun `a failed ble attempt clears the history so the next operation uses the web api`() {
        reachability.record(UUID, NOW, reachable = true)
        reachability.record(UUID, NOW + 1, reachable = false)
        assertFalse(reachability.preferBle(UUID, NOW + 2))
    }

    @Test
    fun `history is kept per device`() {
        reachability.record(UUID, NOW, reachable = true)
        assertTrue(reachability.preferBle(UUID, NOW))
        assertFalse(reachability.preferBle(OTHER_UUID, NOW))
    }

    @Test
    fun `a clock that moved backwards does not keep ble preferred`() {
        reachability.record(UUID, NOW, reachable = true)
        assertFalse(reachability.preferBle(UUID, NOW - 1))
    }

    @Test
    fun `probing is requested for a device that has never been probed`() {
        assertTrue(reachability.shouldProbe(UUID, NOW))
    }

    @Test
    fun `probing is skipped while ble is already preferred`() {
        reachability.record(UUID, NOW, reachable = true)
        assertFalse(reachability.shouldProbe(UUID, NOW))
    }

    @Test
    fun `probing is rate limited after a probe that found nothing`() {
        reachability.recordProbe(UUID, NOW, reachable = false)
        assertFalse(reachability.shouldProbe(UUID, NOW + SesameBleReachability.PROBE_INTERVAL_MILLIS))
        assertTrue(reachability.shouldProbe(UUID, NOW + SesameBleReachability.PROBE_INTERVAL_MILLIS + 1))
    }

    @Test
    fun `a successful probe makes ble preferred for the next operation`() {
        reachability.recordProbe(UUID, NOW, reachable = true)
        assertTrue(reachability.preferBle(UUID, NOW))
    }

    @Test
    fun `remove clears the history of one device only`() {
        reachability.record(UUID, NOW, reachable = true)
        reachability.record(OTHER_UUID, NOW, reachable = true)
        reachability.remove(UUID)
        assertFalse(reachability.preferBle(UUID, NOW))
        assertTrue(reachability.preferBle(OTHER_UUID, NOW))
    }

    @Test
    fun `a corrupted stored value is treated as no history`() {
        store.putString("ble_reachability", "{ this is not json")
        assertFalse(reachability.preferBle(UUID, NOW))
        assertTrue(reachability.shouldProbe(UUID, NOW))
    }

    @Test
    fun `the status shown to the user follows the same rule as the route selection`() {
        assertEquals(State.UNKNOWN, reachability.status(UUID, NOW).state)
        reachability.recordProbe(UUID, NOW, reachable = false)
        assertEquals(State.OUT_OF_RANGE, reachability.status(UUID, NOW).state)
        reachability.record(UUID, NOW, reachable = true)
        val inRange = reachability.status(UUID, NOW)
        assertEquals(State.IN_RANGE, inRange.state)
        assertEquals(NOW, inRange.lastCheckedAtEpochMillis)
    }

    @Test
    fun `an expired reachability reads as out of range rather than in range`() {
        reachability.recordProbe(UUID, NOW, reachable = true)
        val expired = NOW + SesameBleReachability.REACHABLE_TTL_MILLIS + 1
        assertEquals(State.OUT_OF_RANGE, reachability.status(UUID, expired).state)
    }

    @Test
    fun `the default constructor keeps everything on the web api`() {
        val disabled = SesameBleReachability()
        disabled.record(UUID, NOW, reachable = true)
        assertFalse(disabled.preferBle(UUID, NOW))
    }

    private companion object {
        const val UUID = "11111111-2222-3333-4444-555555555555"
        const val OTHER_UUID = "99999999-8888-7777-6666-555555555555"
        const val NOW = 1_700_000_000_000L
    }
}

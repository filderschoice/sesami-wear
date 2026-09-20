package com.sesamiwear.core.display

import com.sesamiwear.core.display.SesameBleConnectionLabel.State
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bluetoothの到達状況の表示文言の検証（BL-190）。
 * 「届いている／届かない／まだ確認していない」を書き分け、確認時刻を相対表記で添えることを固定する。
 */
class SesameBleConnectionLabelTest {
    @Test
    fun `in range shows when it was last checked`() {
        val label = SesameBleConnectionLabel.label(State.IN_RANGE, NOW - 3 * MINUTE, NOW)
        assertEquals("Bluetooth：圏内（3分前に確認）", label)
    }

    @Test
    fun `out of range is worded differently from unknown`() {
        val outOfRange = SesameBleConnectionLabel.label(State.OUT_OF_RANGE, NOW - MINUTE, NOW)
        assertEquals("Bluetooth：圏外（1分前に確認）", outOfRange)
        assertEquals(SesameBleConnectionLabel.UNKNOWN_LABEL, SesameBleConnectionLabel.label(State.UNKNOWN, NOW, NOW))
    }

    @Test
    fun `a check just now reads naturally without a particle`() {
        assertEquals("Bluetooth：圏内（たった今確認）", SesameBleConnectionLabel.label(State.IN_RANGE, NOW, NOW))
    }

    @Test
    fun `a missing check time falls back to unknown even if the state says otherwise`() {
        assertEquals(SesameBleConnectionLabel.UNKNOWN_LABEL, SesameBleConnectionLabel.label(State.IN_RANGE, null, NOW))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}

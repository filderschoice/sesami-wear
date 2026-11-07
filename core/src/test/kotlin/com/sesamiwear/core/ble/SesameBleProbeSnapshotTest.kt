package com.sesamiwear.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameBleProbeSnapshotTest {
    @Test
    fun `encode and decode round trip`() {
        val snapshot =
            SesameBleProbeSnapshot(
                stage = SesameBleProbeStage.CONNECTED,
                rssiDbm = -66,
                batteryPercentage = 80,
                position = 12,
            )
        assertEquals(snapshot, SesameBleProbeSnapshot.decode(snapshot.encode()))
    }

    @Test
    fun `decode ignores unknown keys and rejects broken payloads`() {
        val withExtra = """{"stage":"FOUND","rssiDbm":-70,"future":1}""".toByteArray()
        assertEquals(SesameBleProbeStage.FOUND, SesameBleProbeSnapshot.decode(withExtra)?.stage)
        assertNull(SesameBleProbeSnapshot.decode("not json".toByteArray()))
        assertNull(SesameBleProbeSnapshot.decode("""{"stage":"NO_SUCH_STAGE"}""".toByteArray()))
    }

    @Test
    fun `compact lines show distance only when the radio is seen`() {
        assertEquals("探索中" to null, SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING).compactLines())
        assertEquals(
            "発見" to "約3m",
            SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -71).compactLines(),
        )
    }

    @Test
    fun `short labels fit the watch width`() {
        SesameBleProbeStage.entries.forEach { assertTrue(it.name, it.shortLabel.length <= 5) }
    }

    @Test
    fun `terminal and problem flags are consistent`() {
        SesameBleProbeStage.entries.filter { it.isProblem }.forEach { assertTrue(it.name, it.isTerminal) }
        assertFalse(SesameBleProbeStage.SCANNING.isTerminal)
        assertTrue(SesameBleProbeStage.CONNECTED.isTerminal)
        assertFalse(SesameBleProbeStage.CONNECTED.isProblem)
    }
}

package com.sesamiwear.core.ble

import com.sesamiwear.core.SesameDeviceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesameBleMultiProbeSnapshotTest {
    private val devices = listOf(SesameDeviceSummary("a", "玄関上"), SesameDeviceSummary("b", "玄関下"))

    @Test
    fun `initial puts every device in scanning in registration order`() {
        val snapshot = SesameBleMultiProbeSnapshot.initial(devices)

        assertEquals(listOf("a", "b"), snapshot.entries.map { it.uuid })
        assertEquals(listOf("玄関上", "玄関下"), snapshot.entries.map { it.name })
        snapshot.entries.forEach { assertEquals(SesameBleProbeStage.SCANNING, it.snapshot.stage) }
    }

    @Test
    fun `withSnapshot replaces only the matching device`() {
        val found = SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -71)

        val updated = SesameBleMultiProbeSnapshot.initial(devices).withSnapshot("b", found)

        assertEquals(SesameBleProbeStage.SCANNING, updated.entries[0].snapshot.stage)
        assertEquals(found, updated.entries[1].snapshot)
    }

    @Test
    fun `withSnapshot ignores an unknown uuid`() {
        val initial = SesameBleMultiProbeSnapshot.initial(devices)

        assertEquals(initial, initial.withSnapshot("zzz", SesameBleProbeSnapshot(SesameBleProbeStage.NOT_FOUND)))
    }

    @Test
    fun `status line joins the short label with the second line`() {
        val entry = SesameBleMultiProbeSnapshot.Entry("a", "玄関上", SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING))
        assertEquals("探索中", entry.statusLine())
        assertEquals(
            "発見・約3m",
            entry.copy(snapshot = SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -72)).statusLine(),
        )
        assertEquals(
            "接続OK・電池100%・角度-180",
            entry.copy(
                snapshot =
                    SesameBleProbeSnapshot(
                        SesameBleProbeStage.CONNECTED,
                        batteryPercentage = 100,
                        position = -180,
                    ),
            ).statusLine(),
        )
    }

    @Test
    fun `encode and decode round trip, and broken payloads are null`() {
        val snapshot =
            SesameBleMultiProbeSnapshot.initial(devices)
                .withSnapshot("a", SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -66))

        assertEquals(snapshot, SesameBleMultiProbeSnapshot.decode(snapshot.encode()))
        assertNull(SesameBleMultiProbeSnapshot.decode("not json".toByteArray()))
    }
}

package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「まとめて接続確認」（BL-280）が、全台を登録順に1台ずつ確かめ、1台の失敗で止まらないことを確かめる。 */
class MultiProbeChecksTest {
    private val devices =
        listOf(
            SesameDeviceSummary("a", "玄関上"),
            SesameDeviceSummary("b", "玄関下"),
            SesameDeviceSummary("c", "勝手口"),
        )

    private class FakeProbe(
        private val uuid: String,
        private val order: MutableList<String>,
        private val result: SesameBleProbeSnapshot,
    ) : BleProbeOperations {
        override val usesBluetooth: Boolean = false

        override fun observe(): Flow<SesameBleProbeSnapshot> = emptyFlow()

        override suspend fun checkConnection(
            lastRssiDbm: Int?,
            onProgress: (SesameBleProbeSnapshot) -> Unit,
        ): SesameBleProbeSnapshot {
            order += uuid
            onProgress(SesameBleProbeSnapshot(SesameBleProbeStage.CONNECTING, lastRssiDbm))
            return result
        }
    }

    private val connected = SesameBleProbeSnapshot(SesameBleProbeStage.CONNECTED, batteryPercentage = 80)
    private val failed = SesameBleProbeSnapshot(SesameBleProbeStage.FAILED)

    @Test
    fun `devices are checked one at a time in registration order`() =
        runTest {
            val order = mutableListOf<String>()
            val probes = devices.associate { it.uuid to FakeProbe(it.uuid, order, connected) }

            checkSequentially(SesameBleMultiProbeSnapshot.initial(devices), { probes.getValue(it) }, {})

            assertEquals(listOf("a", "b", "c"), order)
        }

    @Test
    fun `a failure does not stop the remaining devices`() =
        runTest {
            val order = mutableListOf<String>()
            val results = mapOf("a" to connected, "b" to failed, "c" to connected)
            val probes = devices.associate { it.uuid to FakeProbe(it.uuid, order, results.getValue(it.uuid)) }

            val final = checkSequentially(SesameBleMultiProbeSnapshot.initial(devices), { probes.getValue(it) }, {})

            assertEquals(listOf("a", "b", "c"), order)
            assertEquals(
                listOf(SesameBleProbeStage.CONNECTED, SesameBleProbeStage.FAILED, SesameBleProbeStage.CONNECTED),
                final.entries.map { it.snapshot.stage },
            )
            assertEquals(80, final.entries[0].snapshot.batteryPercentage)
        }

    @Test
    fun `progress reflects each step and keeps earlier results`() =
        runTest {
            val order = mutableListOf<String>()
            val probes = devices.associate { it.uuid to FakeProbe(it.uuid, order, connected) }
            val seen = mutableListOf<List<SesameBleProbeStage>>()

            checkSequentially(
                SesameBleMultiProbeSnapshot.initial(devices.take(2)),
                { probes.getValue(it) },
                { seen += it.entries.map { entry -> entry.snapshot.stage } },
            )

            assertEquals(
                listOf(
                    listOf(SesameBleProbeStage.CONNECTING, SesameBleProbeStage.SCANNING),
                    listOf(SesameBleProbeStage.CONNECTED, SesameBleProbeStage.SCANNING),
                    listOf(SesameBleProbeStage.CONNECTED, SesameBleProbeStage.CONNECTING),
                    listOf(SesameBleProbeStage.CONNECTED, SesameBleProbeStage.CONNECTED),
                ),
                seen,
            )
        }

    @Test
    fun `last rssi of each device is handed to its check`() =
        runTest {
            val handed = mutableListOf<Int?>()
            val probe =
                object : BleProbeOperations {
                    override val usesBluetooth = false

                    override fun observe(): Flow<SesameBleProbeSnapshot> = emptyFlow()

                    override suspend fun checkConnection(
                        lastRssiDbm: Int?,
                        onProgress: (SesameBleProbeSnapshot) -> Unit,
                    ): SesameBleProbeSnapshot {
                        handed += lastRssiDbm
                        return connected
                    }
                }
            val current =
                SesameBleMultiProbeSnapshot.initial(devices.take(2))
                    .withSnapshot("a", SesameBleProbeSnapshot(SesameBleProbeStage.FOUND, rssiDbm = -66))

            checkSequentially(current, { probe }, {})

            assertEquals(listOf(-66, null), handed)
        }
}

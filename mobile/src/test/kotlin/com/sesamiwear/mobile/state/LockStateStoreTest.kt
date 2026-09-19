package com.sesamiwear.mobile.state

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.SesameStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LockStateStoreTest {
    private val keyValueStore = InMemoryKeyValueStore()
    private val store = LockStateStore(keyValueStore)

    @Test
    fun `load returns null for a device that has never been saved`() {
        assertNull(store.load("uuid-1"))
    }

    @Test
    fun `saved state is loaded per device`() {
        store.save("uuid-1", isLocked = true, updatedAtEpochMillis = 1_000L)
        store.save("uuid-2", isLocked = false, updatedAtEpochMillis = 2_000L)

        assertEquals(SesameStatusSnapshot(isLocked = true, updatedAtEpochMillis = 1_000L), store.load("uuid-1"))
        assertEquals(SesameStatusSnapshot(isLocked = false, updatedAtEpochMillis = 2_000L), store.load("uuid-2"))
    }

    @Test
    fun `saving again overwrites the previous state of the same device`() {
        store.save("uuid-1", isLocked = true, updatedAtEpochMillis = 1_000L)
        store.save("uuid-1", isLocked = false, updatedAtEpochMillis = 3_000L)

        assertEquals(SesameStatusSnapshot(isLocked = false, updatedAtEpochMillis = 3_000L), store.load("uuid-1"))
    }

    @Test
    fun `state persists across store instances sharing the same backing store`() {
        store.save("uuid-1", isLocked = true, updatedAtEpochMillis = 1_000L)

        assertEquals(true, LockStateStore(keyValueStore).load("uuid-1")?.isLocked)
    }

    @Test
    fun `remove deletes only the given device`() {
        store.save("uuid-1", isLocked = true, updatedAtEpochMillis = 1_000L)
        store.save("uuid-2", isLocked = false, updatedAtEpochMillis = 2_000L)

        store.remove("uuid-1")

        assertNull(store.load("uuid-1"))
        assertEquals(false, store.load("uuid-2")?.isLocked)
    }

    @Test
    fun `corrupted stored value is treated as not yet fetched`() {
        keyValueStore.putString("lock_states", "{not json")

        assertNull(store.load("uuid-1"))
        store.save("uuid-1", isLocked = true, updatedAtEpochMillis = 1_000L)
        assertEquals(true, store.load("uuid-1")?.isLocked)
    }

    @Test
    fun `entries with missing or wrongly typed fields are ignored`() {
        keyValueStore.putString(
            "lock_states",
            """{"uuid-1":{"isLocked":"yes","updatedAtEpochMillis":1},"uuid-2":{"isLocked":true},""" +
                """"uuid-3":{"isLocked":false,"updatedAtEpochMillis":5}}""",
        )

        assertNull(store.load("uuid-1"))
        assertNull(store.load("uuid-2"))
        assertEquals(SesameStatusSnapshot(isLocked = false, updatedAtEpochMillis = 5L), store.load("uuid-3"))
    }

    /**
     * 「全デバイス」の操作では、デバイスごとに別コルーチン・別インスタンスで保存が走る（BL-157）。
     * 排他がインスタンス単位だと単一キーへのread-modify-writeが後勝ちになり、一部の更新が失われる。
     * 読み出しを遅らせて競合の窓を広げ、全デバイスぶんが残ることを確かめる。
     */
    @Test
    fun `concurrent saves from separate instances keep every device state`() {
        val backingStore = SlowKeyValueStore(readDelayMillis = 20L)
        val uuids = (1..8).map { "uuid-$it" }
        val startGate = CountDownLatch(1)
        val finished = CountDownLatch(uuids.size)
        val threads = Executors.newFixedThreadPool(uuids.size)
        uuids.forEachIndexed { index, uuid ->
            threads.execute {
                startGate.await()
                LockStateStore(backingStore).save(
                    uuid,
                    isLocked = false,
                    updatedAtEpochMillis = (index + 1) * 1_000L,
                )
                finished.countDown()
            }
        }
        startGate.countDown()

        assertTrue(finished.await(10L, TimeUnit.SECONDS))
        threads.shutdown()
        val loaded = LockStateStore(backingStore)
        uuids.forEachIndexed { index, uuid ->
            assertEquals(
                SesameStatusSnapshot(isLocked = false, updatedAtEpochMillis = (index + 1) * 1_000L),
                loaded.load(uuid),
            )
        }
    }

    // --- 電池残量・角度・経路（BL-166） ---

    @Test
    fun `saving with a measurement keeps battery, position and route`() {
        store.save(
            DEVICE_UUID,
            isLocked = true,
            updatedAtEpochMillis = 1000L,
            measurement =
                SesameStatusMeasurement(
                    batteryPercentage = 85,
                    position = 42,
                    route = SesameStatusRoute.BLE,
                ),
        )

        val snapshot = store.load(DEVICE_UUID)
        assertEquals(85, snapshot?.batteryPercentage)
        assertEquals(42, snapshot?.position)
        assertEquals(SesameStatusRoute.BLE, snapshot?.lastRoute)
    }

    @Test
    fun `a measurement without battery keeps the previously known value`() {
        store.save(
            DEVICE_UUID,
            isLocked = true,
            updatedAtEpochMillis = 1000L,
            measurement = SesameStatusMeasurement(batteryPercentage = 85, position = 42, route = SesameStatusRoute.BLE),
        )

        // Web API経由の施錠/解錠は状態を返さないため、分かるのは経路だけ。
        store.save(
            DEVICE_UUID,
            isLocked = false,
            updatedAtEpochMillis = 2000L,
            measurement = SesameStatusMeasurement.ofRoute(SesameStatusRoute.WEB_API),
        )

        val snapshot = store.load(DEVICE_UUID)
        assertEquals(false, snapshot?.isLocked)
        assertEquals(85, snapshot?.batteryPercentage)
        assertEquals(42, snapshot?.position)
        assertEquals(SesameStatusRoute.WEB_API, snapshot?.lastRoute)
    }

    @Test
    fun `recording a failure keeps battery, position and route`() {
        store.save(
            DEVICE_UUID,
            isLocked = true,
            updatedAtEpochMillis = 1000L,
            measurement = SesameStatusMeasurement(batteryPercentage = 85, position = 42, route = SesameStatusRoute.BLE),
        )

        store.saveFailure(DEVICE_UUID, SesameStatusFailure.COMMUNICATION)

        val snapshot = store.load(DEVICE_UUID)
        assertEquals(85, snapshot?.batteryPercentage)
        assertEquals(SesameStatusRoute.BLE, snapshot?.lastRoute)
        assertEquals(SesameStatusFailure.COMMUNICATION, snapshot?.lastFailure)
    }

    @Test
    fun `a value saved before battery existed still loads`() {
        // BL-166より前の保存形式（電池・角度・経路のキーが無い）。
        keyValueStore.putString(
            "lock_states",
            """{"$DEVICE_UUID":{"isLocked":true,"updatedAtEpochMillis":1000}}""",
        )

        val snapshot = store.load(DEVICE_UUID)

        assertEquals(true, snapshot?.isLocked)
        assertEquals(null, snapshot?.batteryPercentage)
        assertEquals(null, snapshot?.lastRoute)
    }

    @Test
    fun `an unknown route name is treated as unknown`() {
        keyValueStore.putString(
            "lock_states",
            """{"$DEVICE_UUID":{"isLocked":true,"updatedAtEpochMillis":1000,"lastRoute":"RETIRED"}}""",
        )

        assertEquals(null, store.load(DEVICE_UUID)?.lastRoute)
    }

    private companion object {
        const val DEVICE_UUID = "uuid-battery"
    }
}

/** 読み出しに時間がかかる保存先。read-modify-writeの競合を再現しやすくするために使う（BL-157）。 */
private class SlowKeyValueStore(private val readDelayMillis: Long) : SesameKeyValueStore {
    private val values = ConcurrentHashMap<String, String>()

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun getString(key: String): String? {
        Thread.sleep(readDelayMillis)
        return values[key]
    }

    override fun clear() {
        values.clear()
    }
}

internal class InMemoryKeyValueStore : SesameKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun getString(key: String): String? = values[key]

    override fun clear() {
        values.clear()
    }
}

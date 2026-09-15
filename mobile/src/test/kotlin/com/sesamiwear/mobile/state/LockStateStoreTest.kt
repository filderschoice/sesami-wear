package com.sesamiwear.mobile.state

import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.core.SesameStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

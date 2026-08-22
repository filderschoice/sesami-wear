package com.sesamiwear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SesameCredentialsStoreTest {
    private class InMemoryKeyValueStore : SesameKeyValueStore {
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

    @Test
    fun `round-trips a single credentials entry through saveAll and loadAll`() {
        val store = SesameCredentialsStore(InMemoryKeyValueStore())
        val credentials =
            SesameCredentials(
                uuid = "test-uuid",
                apiKey = "test-api-key",
                secretKeyHex = "0102030405060708090a0b0c0d0e0f10",
                displayName = "玄関",
            )

        store.saveAll(listOf(credentials))
        val loaded = store.loadAll()

        assertEquals(listOf(credentials), loaded)
    }

    @Test
    fun `round-trips multiple credentials entries`() {
        val store = SesameCredentialsStore(InMemoryKeyValueStore())
        val first =
            SesameCredentials(uuid = "uuid-1", apiKey = "key-1", secretKeyHex = "01020304", displayName = "玄関")
        val second =
            SesameCredentials(uuid = "uuid-2", apiKey = "key-2", secretKeyHex = "05060708", displayName = "裏口")

        store.saveAll(listOf(first, second))
        val loaded = store.loadAll()

        assertEquals(listOf(first, second), loaded)
    }

    @Test
    fun `loadAll returns empty list when nothing has been saved`() {
        val store = SesameCredentialsStore(InMemoryKeyValueStore())

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `loadAll returns empty list when saved value is not valid json`() {
        val keyValueStore = InMemoryKeyValueStore()
        keyValueStore.putString("credentials_list", "not-valid-json")
        val store = SesameCredentialsStore(keyValueStore)

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `remove drops only the matching uuid`() {
        val store = SesameCredentialsStore(InMemoryKeyValueStore())
        val first = SesameCredentials(uuid = "uuid-1", apiKey = "key-1", secretKeyHex = "01020304")
        val second = SesameCredentials(uuid = "uuid-2", apiKey = "key-2", secretKeyHex = "05060708")
        store.saveAll(listOf(first, second))

        store.remove("uuid-1")

        assertEquals(listOf(second), store.loadAll())
    }

    @Test
    fun `clear removes saved credentials`() {
        val keyValueStore = InMemoryKeyValueStore()
        val store = SesameCredentialsStore(keyValueStore)
        val credentials = SesameCredentials(uuid = "test-uuid", apiKey = "test-api-key", secretKeyHex = "01020304")
        store.saveAll(listOf(credentials))

        store.clear()

        assertTrue(store.loadAll().isEmpty())
    }
}

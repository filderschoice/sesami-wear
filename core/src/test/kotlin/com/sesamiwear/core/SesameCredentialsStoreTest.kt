package com.sesamiwear.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

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
    fun `round-trips credentials through save and load`() {
        val store = SesameCredentialsStore(InMemoryKeyValueStore())
        val secretKeyBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        val credentials =
            SesameCredentials(uuid = "test-uuid", apiKey = "test-api-key", secretKeyBase64 = secretKeyBase64)

        store.save(credentials)
        val loaded = store.load()

        assertEquals(credentials, loaded)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), loaded?.secretKeyBytes)
    }

    @Test
    fun `load returns null when nothing has been saved`() {
        val store = SesameCredentialsStore(InMemoryKeyValueStore())

        assertNull(store.load())
    }

    @Test
    fun `load returns null when only partially saved`() {
        val keyValueStore = InMemoryKeyValueStore()
        keyValueStore.putString("uuid", "test-uuid")
        val store = SesameCredentialsStore(keyValueStore)

        assertNull(store.load())
    }

    @Test
    fun `clear removes saved credentials`() {
        val keyValueStore = InMemoryKeyValueStore()
        val store = SesameCredentialsStore(keyValueStore)
        store.save(SesameCredentials(uuid = "test-uuid", apiKey = "test-api-key", secretKeyBase64 = "AQIDBA=="))

        store.clear()

        assertNull(store.load())
    }
}

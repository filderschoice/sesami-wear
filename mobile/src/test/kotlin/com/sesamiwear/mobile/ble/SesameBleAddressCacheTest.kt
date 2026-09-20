package com.sesamiwear.mobile.ble

import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 見つけたBLEアドレスの記録の検証（BL-189）。
 * 探索を飛ばして直接接続するための記録なので、保存・読み出し・破棄が素直に効くことを固定する。
 */
class SesameBleAddressCacheTest {
    private val cache = SesameBleAddressCache(InMemoryKeyValueStore())

    @Test
    fun `an unknown device has no address`() {
        assertNull(cache.load(UUID))
    }

    @Test
    fun `a saved address is returned per device`() {
        cache.save(UUID, ADDRESS)
        cache.save(OTHER_UUID, OTHER_ADDRESS)
        assertEquals(ADDRESS, cache.load(UUID))
        assertEquals(OTHER_ADDRESS, cache.load(OTHER_UUID))
    }

    @Test
    fun `saving again overwrites the previous address`() {
        cache.save(UUID, ADDRESS)
        cache.save(UUID, OTHER_ADDRESS)
        assertEquals(OTHER_ADDRESS, cache.load(UUID))
    }

    @Test
    fun `a stale address can be dropped without touching the others`() {
        cache.save(UUID, ADDRESS)
        cache.save(OTHER_UUID, OTHER_ADDRESS)
        cache.remove(UUID)
        assertNull(cache.load(UUID))
        assertEquals(OTHER_ADDRESS, cache.load(OTHER_UUID))
    }

    @Test
    fun `a blank address is not stored`() {
        cache.save(UUID, " ")
        assertNull(cache.load(UUID))
    }

    @Test
    fun `a corrupted stored value is treated as no record`() {
        val store = InMemoryKeyValueStore()
        store.putString("ble_addresses", "{ this is not json")
        assertNull(SesameBleAddressCache(store).load(UUID))
    }

    private companion object {
        const val UUID = "11111111-2222-3333-4444-555555555555"
        const val OTHER_UUID = "99999999-8888-7777-6666-555555555555"
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val OTHER_ADDRESS = "11:22:33:44:55:66"
    }
}

package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 経路の方針の保存と既定値の検証（BL-167）。
 * 「Bluetooth固定」を作らない（圏外で締め出されるため）方針を、選択肢の数で固定する。
 */
class SesameRoutePolicyStoreTest {
    private val keyValueStore = InMemoryKeyValueStore()
    private val store = SesameRoutePolicyStore(keyValueStore)

    @Test
    fun `the default is automatic`() {
        assertEquals(SesameRoutePolicy.AUTO, store.load())
    }

    @Test
    fun `a saved policy is restored`() {
        store.save(SesameRoutePolicy.WEB_API_ONLY)
        assertEquals(SesameRoutePolicy.WEB_API_ONLY, SesameRoutePolicyStore(keyValueStore).load())
    }

    @Test
    fun `an unknown saved value falls back to the default`() {
        keyValueStore.putString("route_policy", "BLE_ONLY")
        assertEquals(SesameRoutePolicy.AUTO, store.load())
    }

    @Test
    fun `only automatic allows bluetooth`() {
        assertTrue(SesameRoutePolicy.AUTO.allowsBle)
        assertFalse(SesameRoutePolicy.WEB_API_ONLY.allowsBle)
    }

    @Test
    fun `there is no bluetooth only policy`() {
        // 圏外で操作できなくなるため用意しない（BL-167）。選択肢が増えていないことを固定する。
        assertEquals(2, SesameRoutePolicy.entries.size)
    }
}

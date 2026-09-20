package com.sesamiwear.mobile.ble

import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 経路が切り替わったときの通知の設定の検証（BL-190）。
 * 既定はオン（切り替わりに気づけないと原因を切り分けられないため）で、明示的にオフへできることを固定する。
 */
class SesameRouteNotificationStoreTest {
    private val store = InMemoryKeyValueStore()
    private val settings = SesameRouteNotificationStore(store)

    @Test
    fun `notifications are on until the user turns them off`() {
        assertTrue(settings.isEnabled())
    }

    @Test
    fun `the choice is remembered in both directions`() {
        settings.setEnabled(false)
        assertFalse(settings.isEnabled())
        settings.setEnabled(true)
        assertTrue(settings.isEnabled())
    }

    @Test
    fun `an unknown stored value falls back to on`() {
        store.putString("route_change_notification", "maybe")
        assertTrue(settings.isEnabled())
    }
}

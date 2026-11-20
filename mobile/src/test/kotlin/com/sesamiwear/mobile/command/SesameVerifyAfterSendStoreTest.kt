package com.sesamiwear.mobile.command

import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「送信後の状態確認」設定（BL-293）の保存の検証。既定はOFF（BL-142の方針の維持）。 */
class SesameVerifyAfterSendStoreTest {
    private val store = InMemoryKeyValueStore()
    private val settings = SesameVerifyAfterSendStore(store)

    @Test
    fun `verification is off until the user turns it on`() {
        assertFalse(settings.isEnabled())
    }

    @Test
    fun `the choice is remembered in both directions`() {
        settings.setEnabled(true)
        assertTrue(settings.isEnabled())
        settings.setEnabled(false)
        assertFalse(settings.isEnabled())
    }

    @Test
    fun `an unknown stored value falls back to off`() {
        store.putString("verify_after_send", "maybe")
        assertFalse(settings.isEnabled())
    }
}

package com.sesamiwear.mobile.alert

import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「お知らせ」の設定（BL-238 / BL-241）。既定値と、APIを消費する機能が分からない値で動かないことを固定する。 */
class SesameAlertSettingsStoreTest {
    private val store = InMemoryKeyValueStore()
    private val settings = SesameAlertSettingsStore(store)

    @Test
    fun `low battery alert is on until turned off`() {
        assertTrue(settings.isLowBatteryEnabled())
        settings.setLowBatteryEnabled(false)
        assertFalse(settings.isLowBatteryEnabled())
        settings.setLowBatteryEnabled(true)
        assertTrue(settings.isLowBatteryEnabled())
    }

    @Test
    fun `unlock reminder is off by default`() {
        assertEquals(0, settings.unlockReminderMinutes())
    }

    @Test
    fun `unlock reminder remembers each choice`() {
        for (minutes in SesameAlertSettingsStore.UNLOCK_REMINDER_CHOICES) {
            settings.setUnlockReminderMinutes(minutes)
            assertEquals(minutes, settings.unlockReminderMinutes())
        }
    }

    @Test
    fun `a value outside the choices is neither saved nor honored`() {
        settings.setUnlockReminderMinutes(10)
        settings.setUnlockReminderMinutes(7)
        assertEquals(10, settings.unlockReminderMinutes())

        store.putString("unlock_reminder_minutes", "abc")
        assertEquals(0, settings.unlockReminderMinutes())
        store.putString("unlock_reminder_minutes", "1")
        assertEquals(0, settings.unlockReminderMinutes())
    }
}

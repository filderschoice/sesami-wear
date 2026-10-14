package com.sesamiwear.mobile.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** アプリアイコン長押しメニューの操作（BL-239）。解錠を受け付けないことと、文言を固定する。 */
class AppShortcutActionTest {
    @Test
    fun `known actions are parsed`() {
        assertEquals(
            AppShortcutAction.LOCK_ALL,
            AppShortcutAction.fromIntentAction("com.sesamiwear.mobile.shortcut.LOCK_ALL"),
        )
        assertEquals(
            AppShortcutAction.REFRESH_ALL,
            AppShortcutAction.fromIntentAction("com.sesamiwear.mobile.shortcut.REFRESH_ALL"),
        )
    }

    @Test
    fun `unlock and unknown actions are rejected`() {
        assertNull(AppShortcutAction.fromIntentAction("com.sesamiwear.mobile.shortcut.UNLOCK_ALL"))
        assertNull(AppShortcutAction.fromIntentAction(null))
        assertNull(AppShortcutAction.fromIntentAction(""))
    }

    @Test
    fun `messages tell how many devices are targeted`() {
        assertEquals("施錠しています", AppShortcutAction.LOCK_ALL.acceptedMessage(1))
        assertEquals("2台を施錠しています", AppShortcutAction.LOCK_ALL.acceptedMessage(2))
        assertEquals("状態を取得しています", AppShortcutAction.REFRESH_ALL.acceptedMessage(1))
        assertEquals("3台の状態を取得しています", AppShortcutAction.REFRESH_ALL.acceptedMessage(3))
    }

    @Test
    fun `no device means nothing is done`() {
        assertEquals(AppShortcutAction.NO_DEVICE_MESSAGE, AppShortcutAction.LOCK_ALL.acceptedMessage(0))
        assertEquals(AppShortcutAction.NO_DEVICE_MESSAGE, AppShortcutAction.REFRESH_ALL.acceptedMessage(0))
    }
}

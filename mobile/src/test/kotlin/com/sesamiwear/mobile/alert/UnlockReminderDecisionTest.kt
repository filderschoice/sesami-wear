package com.sesamiwear.mobile.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 解錠したままの通知（BL-241）の判定。施錠と分かれば通知せず、確かめられなくても解錠中なら知らせる。 */
class UnlockReminderDecisionTest {
    @Test
    fun `still unlocked notifies`() {
        assertEquals(
            "解錠してから10分たちましたが、解錠したままです。",
            UnlockReminderDecision.messageOrNull(fetchedIsLocked = false, lastKnownIsLocked = false, minutes = 10),
        )
    }

    @Test
    fun `locked does not notify`() {
        assertNull(
            UnlockReminderDecision.messageOrNull(fetchedIsLocked = true, lastKnownIsLocked = false, minutes = 10),
        )
        assertNull(UnlockReminderDecision.messageOrNull(fetchedIsLocked = null, lastKnownIsLocked = true, minutes = 10))
    }

    @Test
    fun `an unconfirmed but last known unlocked state still notifies`() {
        assertEquals(
            "解錠してから5分たちました。最新の状態を確かめられませんでしたが、解錠したままの可能性があります。",
            UnlockReminderDecision.messageOrNull(fetchedIsLocked = null, lastKnownIsLocked = false, minutes = 5),
        )
    }

    @Test
    fun `nothing known does not notify`() {
        assertNull(UnlockReminderDecision.messageOrNull(fetchedIsLocked = null, lastKnownIsLocked = null, minutes = 5))
    }

    @Test
    fun `title carries the device name`() {
        assertEquals("玄関：解錠したままです", UnlockReminderDecision.title("玄関"))
    }
}

package com.sesamiwear.wear.action

import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.wear.action.SesameActionLaunchPlanner.Plan
import org.junit.Assert.assertEquals
import org.junit.Test

class SesameActionLaunchPlannerTest {
    private val uuid = "device-uuid"
    private val all = SesameWearProtocol.ALL_DEVICES_TARGET_UUID

    @Test
    fun `direct unlock falls back to the chooser`() {
        assertEquals(Plan.Chooser, SesameActionLaunchPlanner.plan("UNLOCK", choose = false, deviceUuid = uuid))
    }

    @Test
    fun `direct lock executes immediately`() {
        assertEquals(
            Plan.Execute(SesameCommand.LOCK),
            SesameActionLaunchPlanner.plan("LOCK", choose = false, deviceUuid = uuid),
        )
    }

    @Test
    fun `direct lock to all devices executes immediately`() {
        assertEquals(
            Plan.Execute(SesameCommand.LOCK),
            SesameActionLaunchPlanner.plan("LOCK", choose = false, deviceUuid = all),
        )
    }

    @Test
    fun `direct unlock to all devices falls back to the chooser`() {
        assertEquals(
            Plan.Chooser,
            SesameActionLaunchPlanner.plan("UNLOCK", choose = false, deviceUuid = all),
        )
    }

    @Test
    fun `choose request opens the chooser even with a lock command`() {
        assertEquals(Plan.Chooser, SesameActionLaunchPlanner.plan(null, choose = true, deviceUuid = uuid))
        assertEquals(Plan.Chooser, SesameActionLaunchPlanner.plan("LOCK", choose = true, deviceUuid = uuid))
    }

    @Test
    fun `invalid or missing command finishes`() {
        assertEquals(Plan.Finish, SesameActionLaunchPlanner.plan("OPEN", choose = false, deviceUuid = uuid))
        assertEquals(Plan.Finish, SesameActionLaunchPlanner.plan(null, choose = false, deviceUuid = uuid))
    }

    @Test
    fun `missing device uuid finishes for every request`() {
        assertEquals(Plan.Finish, SesameActionLaunchPlanner.plan("LOCK", choose = false, deviceUuid = null))
        assertEquals(Plan.Finish, SesameActionLaunchPlanner.plan("UNLOCK", choose = false, deviceUuid = ""))
        assertEquals(Plan.Finish, SesameActionLaunchPlanner.plan(null, choose = true, deviceUuid = null))
    }
}

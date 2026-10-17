package com.sesamiwear.mobile.alert

import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome
import com.sesamiwear.mobile.command.SesameDeviceCommands
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 解錠したままの通知（BL-241）の予約。解錠の成功で予約し、施錠の成功で取り消すことを固定する。 */
class UnlockReminderCommandsTest {
    private val events = mutableListOf<String>()
    private var outcome = Outcome.SUCCESS
    private var minutes = 10

    private val delegate =
        object : SesameDeviceCommands {
            override suspend fun execute(
                uuid: String,
                command: SesameCommand,
            ): Outcome = outcome

            override suspend fun refreshStatus(uuid: String): Boolean? = false
        }

    private val scheduler =
        object : UnlockReminderScheduler {
            override fun schedule(
                uuid: String,
                delayMinutes: Int,
            ) {
                events += "schedule:$uuid:$delayMinutes"
            }

            override fun cancel(uuid: String) {
                events += "cancel:$uuid"
            }
        }

    private val commands = UnlockReminderCommands(delegate, scheduler) { minutes }

    @Test
    fun `a successful unlock schedules a check`() =
        runTest {
            assertEquals(Outcome.SUCCESS, commands.execute(UUID, SesameCommand.UNLOCK))
            assertEquals(listOf("schedule:$UUID:10"), events)
        }

    @Test
    fun `a successful lock cancels the check`() =
        runTest {
            commands.execute(UUID, SesameCommand.LOCK)
            assertEquals(listOf("cancel:$UUID"), events)
        }

    @Test
    fun `turned off means no check is scheduled`() =
        runTest {
            minutes = 0
            commands.execute(UUID, SesameCommand.UNLOCK)
            assertEquals(listOf("cancel:$UUID"), events)
        }

    @Test
    fun `failed or debounced commands leave the schedule alone`() =
        runTest {
            outcome = Outcome.FAILURE
            commands.execute(UUID, SesameCommand.UNLOCK)
            outcome = Outcome.DEBOUNCED
            commands.execute(UUID, SesameCommand.LOCK)
            assertEquals(emptyList<String>(), events)
        }

    @Test
    fun `refreshing never schedules even when unlocked`() =
        runTest {
            assertEquals(false, commands.refreshStatus(UUID))
            assertEquals(emptyList<String>(), events)
        }

    @Test
    fun `the demo device is ignored`() =
        runTest {
            commands.execute(SesameDemoMode.DEMO_DEVICE_UUID, SesameCommand.UNLOCK)
            assertEquals(emptyList<String>(), events)
        }

    private companion object {
        const val UUID = "uuid-front"
    }
}

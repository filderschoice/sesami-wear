package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCardCommandSenderTest {
    private val sent = mutableListOf<Pair<String, SesameCommand>>()

    private fun sender(
        scope: TestScope,
        gate: CompletableDeferred<Outcome>,
    ) = DeviceCardCommandSender(scope) { uuid, command ->
        sent += uuid to command
        gate.await()
    }

    @Test
    fun `送信中は同じデバイスへの二重押下を無視する`() =
        runTest {
            val gate = CompletableDeferred<Outcome>()
            val sender = sender(this, gate)
            assertTrue(sender.send("u1", "玄関", SesameCommand.LOCK))
            assertTrue("u1" in sender.sending.value)
            assertFalse(sender.send("u1", "玄関", SesameCommand.UNLOCK))
            advanceUntilIdle()
            assertEquals(listOf("u1" to SesameCommand.LOCK), sent)
            gate.complete(Outcome.SUCCESS)
            advanceUntilIdle()
            assertTrue(sender.sending.value.isEmpty())
        }

    @Test
    fun `別のデバイスは送信中でも送れる`() =
        runTest {
            val gate = CompletableDeferred<Outcome>()
            val sender = sender(this, gate)
            assertTrue(sender.send("u1", "玄関", SesameCommand.LOCK))
            assertTrue(sender.send("u2", "裏口", SesameCommand.LOCK))
            assertEquals(setOf("u1", "u2"), sender.sending.value)
            gate.complete(Outcome.SUCCESS)
            advanceUntilIdle()
        }

    @Test
    fun `完了すると結果を保持し送信中を解く`() =
        runTest {
            val gate = CompletableDeferred<Outcome>()
            val sender = sender(this, gate)
            sender.send("u1", "玄関", SesameCommand.UNLOCK)
            advanceUntilIdle()
            assertTrue(sender.results.value.isEmpty())
            gate.complete(Outcome.SUCCESS)
            advanceUntilIdle()
            assertEquals(mapOf("u1" to "玄関を解錠しました"), sender.results.value)
            assertTrue(sender.sending.value.isEmpty())
        }

    @Test
    fun `結果は消費するまで残り消費すると消える`() =
        runTest {
            val gate = CompletableDeferred<Outcome>()
            val sender = sender(this, gate)
            sender.send("u1", "玄関", SesameCommand.LOCK)
            gate.complete(Outcome.FAILURE)
            advanceUntilIdle()
            // 画面が回転などで作り直されても、結果は状態として残っている。
            assertTrue("u1" in sender.results.value)
            sender.consumeResult("u1")
            assertTrue(sender.results.value.isEmpty())
        }

    @Test
    fun `完了後は同じデバイスへ再び送れる`() =
        runTest {
            val gate = CompletableDeferred<Outcome>()
            val sender = sender(this, gate)
            sender.send("u1", "玄関", SesameCommand.LOCK)
            gate.complete(Outcome.SUCCESS)
            advanceUntilIdle()
            assertTrue(sender.send("u1", "玄関", SesameCommand.UNLOCK))
            advanceUntilIdle()
            assertEquals(2, sent.size)
        }
}

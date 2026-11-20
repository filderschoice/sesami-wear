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
            assertEquals("玄関を解錠しました", sender.results.value["u1"]?.message)
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
            sender.consumeResult("u1", sender.results.value.getValue("u1").id)
            assertTrue(sender.results.value.isEmpty())
        }

    @Test
    fun `executeが例外を投げても失敗結果を積み送信中を解く`() =
        runTest {
            val logs = mutableListOf<String>()
            val sender =
                DeviceCardCommandSender(this, log = { logs += it }) { _, _ ->
                    error("secret-detail")
                }
            assertTrue(sender.send("u1", "玄関", SesameCommand.LOCK))
            advanceUntilIdle()
            assertTrue(sender.results.value.getValue("u1").message.startsWith("玄関を施錠できませんでした"))
            assertTrue(sender.sending.value.isEmpty())
            // ログは固定文だけで、例外の本文は含まない。
            assertEquals(1, logs.size)
            assertFalse(logs.single().contains("secret-detail"))
        }

    @Test
    fun `表示前に新しい結果が積まれても古いidの消費では消えない`() =
        runTest {
            val gates = ArrayDeque<CompletableDeferred<Outcome>>()
            val first = CompletableDeferred<Outcome>()
            val second = CompletableDeferred<Outcome>()
            gates += first
            gates += second
            val sender = DeviceCardCommandSender(this) { _, _ -> gates.removeFirst().await() }
            sender.send("u1", "玄関", SesameCommand.LOCK)
            first.complete(Outcome.SUCCESS)
            advanceUntilIdle()
            val oldId = sender.results.value.getValue("u1").id
            sender.send("u1", "玄関", SesameCommand.UNLOCK)
            second.complete(Outcome.SUCCESS)
            advanceUntilIdle()
            val newResult = sender.results.value.getValue("u1")
            assertTrue(newResult.id != oldId)
            sender.consumeResult("u1", oldId)
            assertEquals(newResult, sender.results.value["u1"])
            sender.consumeResult("u1", newResult.id)
            assertTrue(sender.results.value.isEmpty())
        }

    @Test
    fun `idは0始まりの連番と衝突しない（プロセス死後に復元された表示済みidと一致させない）`() =
        runTest {
            val sender = DeviceCardCommandSender(this) { _, _ -> Outcome.SUCCESS }
            sender.send("u1", "玄関", SesameCommand.LOCK)
            advanceUntilIdle()
            // 復元される表示済みidの既定値（-1）や、0始まりの小さな連番の範囲に入らない。
            assertTrue(sender.results.value.getValue("u1").id > 1_000_000L)
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

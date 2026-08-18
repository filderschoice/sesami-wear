package com.sesamiwear.wear.messaging

import com.sesamiwear.core.SesameMessageSender
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SesameCommandSenderTest {
    private class RecordingMessageSender : SesameMessageSender {
        val sentMessages = mutableListOf<Triple<String, String, ByteArray>>()

        override suspend fun send(
            nodeId: String,
            path: String,
            payload: ByteArray,
        ) {
            sentMessages.add(Triple(nodeId, path, payload))
        }
    }

    @Test
    fun `requestLock sends the lock path with an empty payload`() =
        runTest {
            val recorder = RecordingMessageSender()
            val sender = SesameCommandSender(recorder)

            sender.requestLock("node-1")

            assertEquals(1, recorder.sentMessages.size)
            val (nodeId, path, payload) = recorder.sentMessages.first()
            assertEquals("node-1", nodeId)
            assertEquals(SesameWearProtocol.PATH_LOCK_REQUEST, path)
            assertEquals(0, payload.size)
        }

    @Test
    fun `requestUnlock sends the unlock path`() =
        runTest {
            val recorder = RecordingMessageSender()
            val sender = SesameCommandSender(recorder)

            sender.requestUnlock("node-1")

            assertEquals(SesameWearProtocol.PATH_UNLOCK_REQUEST, recorder.sentMessages.first().second)
        }
}

package com.sesamiwear.wear.messaging

import com.sesamiwear.core.SesameMessageSender
import com.sesamiwear.core.SesameWearProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
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
    fun `requestLock sends the lock path with the device uuid as payload`() =
        runTest {
            val recorder = RecordingMessageSender()
            val sender = SesameCommandSender(recorder)

            sender.requestLock("node-1", "device-uuid-1")

            assertEquals(1, recorder.sentMessages.size)
            val (nodeId, path, payload) = recorder.sentMessages.first()
            assertEquals("node-1", nodeId)
            assertEquals(SesameWearProtocol.PATH_LOCK_REQUEST, path)
            assertArrayEquals(SesameWearProtocol.encodeDeviceUuid("device-uuid-1"), payload)
        }

    @Test
    fun `requestUnlock sends the unlock path with the device uuid as payload`() =
        runTest {
            val recorder = RecordingMessageSender()
            val sender = SesameCommandSender(recorder)

            sender.requestUnlock("node-1", "device-uuid-2")

            val (_, path, payload) = recorder.sentMessages.first()
            assertEquals(SesameWearProtocol.PATH_UNLOCK_REQUEST, path)
            assertArrayEquals(SesameWearProtocol.encodeDeviceUuid("device-uuid-2"), payload)
        }

    @Test
    fun `requestStatus sends the status-request path with the device uuid as payload`() =
        runTest {
            val recorder = RecordingMessageSender()
            val sender = SesameCommandSender(recorder)

            sender.requestStatus("node-1", "device-uuid-3")

            val (_, path, payload) = recorder.sentMessages.first()
            assertEquals(SesameWearProtocol.PATH_STATUS_REQUEST, path)
            assertArrayEquals(SesameWearProtocol.encodeDeviceUuid("device-uuid-3"), payload)
        }
}

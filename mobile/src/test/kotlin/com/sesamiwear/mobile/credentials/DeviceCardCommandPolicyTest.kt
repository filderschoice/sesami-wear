package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.command.SesameDeviceCommandExecutor.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCardCommandPolicyTest {
    @Test
    fun `送信中でなければ表示状態によらず押せる`() {
        assertTrue(DeviceCardCommandPolicy.isEnabled(isSending = false))
    }

    @Test
    fun `送信中は無効`() {
        assertFalse(DeviceCardCommandPolicy.isEnabled(isSending = true))
    }

    @Test
    fun `施錠の押下は確認なしでそのまま送る`() {
        assertEquals(
            DeviceCardCommandPolicy.PressAction.Send(SesameCommand.LOCK),
            DeviceCardCommandPolicy.onPressed(SesameCommand.LOCK, isSending = false),
        )
    }

    @Test
    fun `解錠の押下は送らずに確認を求める`() {
        assertEquals(
            DeviceCardCommandPolicy.PressAction.AskConfirm(SesameCommand.UNLOCK),
            DeviceCardCommandPolicy.onPressed(SesameCommand.UNLOCK, isSending = false),
        )
    }

    @Test
    fun `送信中の押下は施錠も解錠も無視する`() {
        SesameCommand.entries.forEach {
            assertEquals(
                DeviceCardCommandPolicy.PressAction.Ignore,
                DeviceCardCommandPolicy.onPressed(it, isSending = true),
            )
        }
    }

    @Test
    fun `確認が要るのは解錠だけ`() {
        assertTrue(DeviceCardCommandPolicy.requiresConfirmation(SesameCommand.UNLOCK))
        assertFalse(DeviceCardCommandPolicy.requiresConfirmation(SesameCommand.LOCK))
    }

    @Test
    fun `ボタンの文言`() {
        assertEquals("施錠", DeviceCardCommandPolicy.buttonLabel(SesameCommand.LOCK))
        assertEquals("解錠", DeviceCardCommandPolicy.buttonLabel(SesameCommand.UNLOCK))
    }

    @Test
    fun `結果メッセージは成功・失敗・重複で変わる`() {
        assertEquals("玄関を施錠しました", DeviceCardCommandPolicy.resultMessage("玄関", SesameCommand.LOCK, Outcome.SUCCESS))
        assertTrue(
            DeviceCardCommandPolicy.resultMessage("玄関", SesameCommand.UNLOCK, Outcome.FAILURE).contains("解錠できませんでした"),
        )
        assertTrue(
            DeviceCardCommandPolicy.resultMessage("玄関", SesameCommand.LOCK, Outcome.DEBOUNCED).contains("送りませんでした"),
        )
    }
}

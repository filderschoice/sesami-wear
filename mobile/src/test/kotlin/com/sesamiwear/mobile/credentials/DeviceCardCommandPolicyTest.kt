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
    fun `鍵を操作の押下は何も送らずに選ぶ画面を開く`() {
        assertEquals(
            DeviceCardCommandPolicy.PressAction.OpenChooser,
            DeviceCardCommandPolicy.onPressed(isSending = false),
        )
    }

    @Test
    fun `送信中の押下は選ぶ画面を開かない`() {
        assertEquals(DeviceCardCommandPolicy.PressAction.Ignore, DeviceCardCommandPolicy.onPressed(isSending = true))
    }

    @Test
    fun `選ぶ画面で選んだ施錠・解錠はそのまま送る`() {
        SesameCommand.entries.forEach {
            assertEquals(
                DeviceCardCommandPolicy.ChooseAction.Send(it),
                DeviceCardCommandPolicy.onChosen(it, isSending = false),
            )
        }
    }

    @Test
    fun `キャンセルは送らない`() {
        assertEquals(
            DeviceCardCommandPolicy.ChooseAction.Ignore,
            DeviceCardCommandPolicy.onChosen(null, isSending = false),
        )
    }

    @Test
    fun `選ぶ画面を開いている間に送信が始まっていたら送らない`() {
        SesameCommand.entries.forEach {
            assertEquals(
                DeviceCardCommandPolicy.ChooseAction.Ignore,
                DeviceCardCommandPolicy.onChosen(it, isSending = true),
            )
        }
    }

    @Test
    fun `ボタンの文言`() {
        assertEquals("鍵を操作", DeviceCardCommandPolicy.MENU_LABEL)
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

package com.sesamiwear.mobile.dial

import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ダイヤル操作画面（BL-243）の文言。インターネット経由へ倒さないことを利用者へ伝える文言を固定する。 */
class DialUiStateTest {
    @Test
    fun `ready state tells what the sweep will do`() {
        val locked = DialUiState.Ready(position = -180, isLocked = true, batteryPercentage = 80)
        assertEquals(SesameCommand.UNLOCK, locked.command)
        assertEquals("ぐるっと半周なぞると解錠", locked.instruction)
        assertEquals("施錠中", locked.stateLabel)

        val unlocked = DialUiState.Ready(position = -270, isLocked = false, batteryPercentage = 80)
        assertEquals(SesameCommand.LOCK, unlocked.command)
        assertEquals("ぐるっと半周なぞると施錠", unlocked.instruction)
    }

    @Test
    fun `an in-between position offers no command`() {
        val middle = DialUiState.Ready(position = -220, isLocked = null, batteryPercentage = null)
        assertNull(middle.command)
        assertEquals("途中の位置", middle.stateLabel)
    }

    @Test
    fun `unreachable never falls back to the internet`() {
        val message = DialUiState.unavailableMessage(SesameBleClient.Result.NOT_FOUND)
        assertTrue(message.contains("インターネット経由では操作しません"))
        assertTrue(DialUiState.unavailableMessage(SesameBleClient.Result.PERMISSION_DENIED).contains("許可"))
        assertTrue(DialUiState.unavailableMessage(SesameBleClient.Result.LOGIN_FAILED).contains("secretKey"))
    }

    @Test
    fun `result messages name the command and the route`() {
        assertEquals("施錠しました（Bluetooth）", DialUiState.resultMessage(SesameCommand.LOCK, true))
        assertEquals(
            "解錠できませんでした。近づいてもう一度お試しください",
            DialUiState.resultMessage(SesameCommand.UNLOCK, false),
        )
    }
}

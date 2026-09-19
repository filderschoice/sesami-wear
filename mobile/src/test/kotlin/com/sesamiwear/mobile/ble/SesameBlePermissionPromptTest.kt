package com.sesamiwear.mobile.ble

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLE権限の案内の状態と文言の検証（BL-153）。
 *
 * 権限が任意であること（拒否されても従来どおり動くこと）が、どの状態の文言でも読み取れることと、
 * 位置情報の補足がAPI 30以下でだけ出ることを固定する。後者はデータセーフティ申告（BL-154）で
 * 「位置情報を収集・共有しない」と説明する前提でもある。
 */
class SesameBlePermissionPromptTest {
    private val missing = listOf(Manifest.permission.BLUETOOTH_SCAN)

    @Test
    fun `no missing permission means granted`() {
        assertEquals(
            SesameBlePermissionPrompt.State.GRANTED,
            SesameBlePermissionPrompt.state(emptyList(), alreadyAsked = false),
        )
        assertEquals(
            SesameBlePermissionPrompt.State.GRANTED,
            SesameBlePermissionPrompt.state(emptyList(), alreadyAsked = true),
        )
    }

    @Test
    fun `a missing permission that was never requested can still be asked for`() {
        val state = SesameBlePermissionPrompt.state(missing, alreadyAsked = false)
        assertEquals(SesameBlePermissionPrompt.State.NOT_REQUESTED, state)
        assertTrue(SesameBlePermissionPrompt.requestsPermission(state))
    }

    @Test
    fun `a missing permission that was already requested sends the user to the settings screen`() {
        val state = SesameBlePermissionPrompt.state(missing, alreadyAsked = true)
        assertEquals(SesameBlePermissionPrompt.State.DENIED, state)
        assertFalse(SesameBlePermissionPrompt.requestsPermission(state))
        assertEquals("端末の設定を開く", SesameBlePermissionPrompt.buttonLabel(state))
    }

    @Test
    fun `no button is shown once the permissions are granted`() {
        assertNull(SesameBlePermissionPrompt.buttonLabel(SesameBlePermissionPrompt.State.GRANTED))
    }

    @Test
    fun `every summary makes clear the app keeps working without the permission`() {
        SesameBlePermissionPrompt.State.entries.forEach { state ->
            val summary = SesameBlePermissionPrompt.summary(state, Build.VERSION_CODES.S)
            assertTrue(state.name, summary.contains("インターネット"))
        }
        assertTrue(
            SesameBlePermissionPrompt
                .summary(SesameBlePermissionPrompt.State.NOT_REQUESTED, Build.VERSION_CODES.S)
                .contains("許可しなくても"),
        )
    }

    @Test
    fun `the location note is only shown on android 11 and earlier`() {
        val legacy =
            SesameBlePermissionPrompt.summary(
                SesameBlePermissionPrompt.State.NOT_REQUESTED,
                Build.VERSION_CODES.R,
            )
        val modern =
            SesameBlePermissionPrompt.summary(
                SesameBlePermissionPrompt.State.NOT_REQUESTED,
                Build.VERSION_CODES.S,
            )
        assertTrue(legacy.contains("位置情報"))
        assertFalse(modern.contains("位置情報"))
    }

    @Test
    fun `the location note states that no location is collected or sent`() {
        val legacy = SesameBlePermissionPrompt.summary(SesameBlePermissionPrompt.State.DENIED, Build.VERSION_CODES.O)
        assertTrue(legacy.contains("収集も送信もしません"))
    }

    @Test
    fun `the granted summary explains that api requests are not consumed`() {
        val summary = SesameBlePermissionPrompt.summary(SesameBlePermissionPrompt.State.GRANTED, Build.VERSION_CODES.S)
        assertTrue(summary.contains("リクエスト回数"))
    }

    @Test
    fun `the compact status line says which route is in use for every state`() {
        SesameBlePermissionPrompt.State.entries.forEach { state ->
            val line = SesameBlePermissionPrompt.shortStatus(state)
            assertTrue(state.name, line.startsWith("Bluetooth："))
            assertTrue(state.name, line.length <= 40)
        }
        assertTrue(
            SesameBlePermissionPrompt
                .shortStatus(SesameBlePermissionPrompt.State.NOT_REQUESTED)
                .contains("インターネット経由"),
        )
    }

    @Test
    fun `the title marks the permission as optional`() {
        assertTrue(SesameBlePermissionPrompt.TITLE.contains("任意"))
    }
}

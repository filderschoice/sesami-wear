package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.mobile.credentials.CredentialsInputValidator
import com.sesamiwear.mobile.state.InMemoryKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 撮影モードの保存先（BL-212）。入力されたAPIキー・秘密鍵を残さないこと、状態を丸ごと置き換えることを確かめる。 */
class ShowcaseStoresTest {
    @Test
    fun `撮影用デバイスのAPIキーと秘密鍵は保存前にダミー値へ置き換わる`() {
        val backing = InMemoryKeyValueStore()
        val store = SesameCredentialsStore(ShowcaseCredentialsKeyValueStore(backing))
        val entered =
            SesameCredentials(
                uuid = "00000000-0000-4000-8000-000000000009",
                apiKey = "entered-api-key",
                secretKeyHex = "0123456789abcdef0123456789abcdef",
                displayName = "書斎",
            )

        store.saveAll(listOf(entered))

        val saved = store.loadAll().single()
        assertEquals(entered.uuid, saved.uuid)
        assertEquals("書斎", saved.displayName)
        assertEquals(ShowcaseCredentialsKeyValueStore.DUMMY_API_KEY, saved.apiKey)
        assertEquals(ShowcaseCredentialsKeyValueStore.DUMMY_SECRET_KEY_HEX, saved.secretKeyHex)
        val raw = backing.getString("credentials_list").orEmpty()
        assertFalse(raw.contains("entered-api-key"))
        assertFalse(raw.contains("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `ダミー値はカード一覧の入力検査を通る`() {
        assertTrue(
            CredentialsInputValidator.isValid(
                uuid = ShowcasePresets.devices.first().first.uuid,
                apiKey = ShowcaseCredentialsKeyValueStore.DUMMY_API_KEY,
                secretKeyHex = ShowcaseCredentialsKeyValueStore.DUMMY_SECRET_KEY_HEX,
            ),
        )
    }

    @Test
    fun `見本のuuidは重複せず、表示名はTileのチップに収まる`() {
        val devices = ShowcasePresets.devices.map { it.first }
        assertEquals(devices.size, devices.map { it.uuid }.distinct().size)
        assertTrue(devices.all { it.displayName.length <= MAX_TILE_NAME_CHARS })
    }

    @Test
    fun `状態の書き込みは前回の値を残さず丸ごと置き換える`() {
        val store = LockStateStore(InMemoryKeyValueStore())
        val uuid = "00000000-0000-4000-8000-000000000001"
        ShowcaseDeviceState(true, 85, position = 10, route = SesameStatusRoute.BLE).writeTo(store, uuid, 100_000)

        ShowcaseDeviceState(false, failure = SesameStatusFailure.AUTH_OR_QUOTA, ageMillis = 60_000)
            .writeTo(store, uuid, 100_000)

        val snapshot = store.load(uuid)
        assertEquals(false, snapshot?.isLocked)
        assertEquals(40_000L, snapshot?.updatedAtEpochMillis)
        assertNull(snapshot?.batteryPercentage)
        assertNull(snapshot?.lastRoute)
        assertEquals(SesameStatusFailure.AUTH_OR_QUOTA, snapshot?.lastFailure)
    }

    @Test
    fun `未取得を書くと保存値が消える`() {
        val store = LockStateStore(InMemoryKeyValueStore())
        val uuid = "00000000-0000-4000-8000-000000000001"
        ShowcaseDeviceState(true).writeTo(store, uuid, 100_000)

        ShowcaseDeviceState(null).writeTo(store, uuid, 100_000)

        assertNull(store.load(uuid))
    }

    private companion object {
        /** `core.SesameDemoMode.MAX_DISPLAY_NAME_CHARS`と同じ、Tileのデバイス名チップに収まる文字数。 */
        const val MAX_TILE_NAME_CHARS = 5
    }
}

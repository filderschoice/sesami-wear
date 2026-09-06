package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.SesameCredentials
import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialsListEditorTest {
    private fun credentials(
        uuid: String,
        displayName: String = "",
    ) = SesameCredentials(
        uuid = uuid,
        apiKey = "api-$uuid",
        secretKeyHex = SECRET_KEY_HEX,
        displayName = displayName,
    )

    @Test
    fun `新規追加は末尾へ追加される`() {
        val list = listOf(credentials("a"), credentials("b"))

        val result = CredentialsListEditor.upsert(list, editingUuid = null, edited = credentials("c"))

        assertEquals(listOf("a", "b", "c"), result.map { it.uuid })
    }

    @Test
    fun `既存項目の編集は同じ位置で更新され件数が増えない`() {
        val list = listOf(credentials("a"), credentials("b"), credentials("c"))

        val result =
            CredentialsListEditor.upsert(
                list,
                editingUuid = "b",
                edited = credentials("b", displayName = "更新後"),
            )

        assertEquals(listOf("a", "b", "c"), result.map { it.uuid })
        assertEquals("更新後", result[1].displayName)
    }

    @Test
    fun `編集でuuidを変更しても元の項目が残らず位置も保たれる`() {
        val list = listOf(credentials("a"), credentials("b"), credentials("c"))

        val result =
            CredentialsListEditor.upsert(
                list,
                editingUuid = "b",
                edited = credentials("b2", displayName = "uuid変更後"),
            )

        assertEquals(listOf("a", "b2", "c"), result.map { it.uuid })
        assertEquals("uuid変更後", result[1].displayName)
    }

    @Test
    fun `編集でuuidを他の既存項目と同じ値に変えた場合は衝突した項目を取り除く`() {
        val list = listOf(credentials("a"), credentials("b"), credentials("c"))

        val result =
            CredentialsListEditor.upsert(
                list,
                editingUuid = "a",
                edited = credentials("c", displayName = "cへ統合"),
            )

        assertEquals(listOf("c", "b"), result.map { it.uuid })
        assertEquals("cへ統合", result[0].displayName)
    }

    @Test
    fun `新規追加でも入力uuidが既存項目と一致する場合は上書きする`() {
        val list = listOf(credentials("a"), credentials("b"))

        val result =
            CredentialsListEditor.upsert(
                list,
                editingUuid = null,
                edited = credentials("a", displayName = "上書き"),
            )

        assertEquals(listOf("a", "b"), result.map { it.uuid })
        assertEquals("上書き", result[0].displayName)
    }

    @Test
    fun `空のリストへの追加は1件のリストになる`() {
        val result = CredentialsListEditor.upsert(emptyList(), editingUuid = null, edited = credentials("a"))

        assertEquals(listOf("a"), result.map { it.uuid })
    }

    private companion object {
        // AES-128鍵長（16バイト）を満たすダミー値。実資格情報は用いない。
        const val SECRET_KEY_HEX = "000102030405060708090a0b0c0d0e0f"
    }
}

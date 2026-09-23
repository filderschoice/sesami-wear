package com.sesamiwear.mobile.showcase

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameKeyValueStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 撮影モード（BL-212）の登録済みデバイスの保存先。`SesameCredentialsStore`へ渡して使う。
 *
 * 撮影用のデバイスもカード一覧から追加・編集できるため、入力欄にはAPIキーと秘密鍵が現れる。
 * 保存先は非暗号化のSharedPreferencesなので、**書き込む前にAPIキーと秘密鍵を固定のダミー値へ置き換え、
 * 入力された値を一切残さない**（実物の値を誤って入力しても保存されない）。uuidと表示名はそのまま残す。
 * 置き換えられない（形式の崩れた）値は保存しない。
 */
class ShowcaseCredentialsKeyValueStore(private val delegate: SesameKeyValueStore) : SesameKeyValueStore {
    override fun putString(
        key: String,
        value: String,
    ) {
        val masked = mask(value) ?: return
        delegate.putString(key, masked)
    }

    override fun getString(key: String): String? = delegate.getString(key)

    override fun clear() {
        delegate.clear()
    }

    private fun mask(json: String): String? =
        try {
            val list = Json.decodeFromString<List<SesameCredentials>>(json)
            Json.encodeToString(list.map { it.copy(apiKey = DUMMY_API_KEY, secretKeyHex = DUMMY_SECRET_KEY_HEX) })
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            null
        } catch (
            @Suppress("SwallowedException") e: IllegalArgumentException,
        ) {
            null
        }

    companion object {
        /** 保存するAPIキー。入力欄の空欄検査（`CredentialsInputValidator`）を通る固定値。 */
        const val DUMMY_API_KEY = "showcase-dummy"

        /** 保存する秘密鍵。AES-128の鍵長（16バイト）を満たす固定値で、実物の鍵ではない。 */
        const val DUMMY_SECRET_KEY_HEX = "00000000000000000000000000000000"
    }
}

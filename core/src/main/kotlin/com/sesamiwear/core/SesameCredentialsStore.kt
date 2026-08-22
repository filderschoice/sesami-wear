package com.sesamiwear.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 複数の[SesameCredentials]（Sesameデバイスごとの資格情報）の保存・読み出し・削除を行う。
 * 実際の永続化先は[SesameKeyValueStore]経由で注入するため、Android非依存でテストできる。
 * uuidをデバイスの一意キーとして扱う（[SesameCredentials]参照）。
 * リスト全体をJSON化して単一キーで保存する（未リリースのアプリのため、単一資格情報時代からの
 * 永続化フォーマット移行は考慮しない）。
 */
class SesameCredentialsStore(private val keyValueStore: SesameKeyValueStore) {
    fun saveAll(credentialsList: List<SesameCredentials>) {
        keyValueStore.putString(KEY_CREDENTIALS_LIST, Json.encodeToString(credentialsList))
    }

    fun loadAll(): List<SesameCredentials> {
        val json = keyValueStore.getString(KEY_CREDENTIALS_LIST) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            emptyList()
        }
    }

    fun remove(uuid: String) {
        saveAll(loadAll().filterNot { it.uuid == uuid })
    }

    fun clear() {
        keyValueStore.clear()
    }

    private companion object {
        const val KEY_CREDENTIALS_LIST = "credentials_list"
    }
}

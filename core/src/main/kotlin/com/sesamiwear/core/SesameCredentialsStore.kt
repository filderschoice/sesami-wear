package com.sesamiwear.core

/**
 * [SesameCredentials] の保存・読み出し・削除を行う。
 * 実際の永続化先は[SesameKeyValueStore]経由で注入するため、Android非依存でテストできる。
 */
class SesameCredentialsStore(private val keyValueStore: SesameKeyValueStore) {
    fun save(credentials: SesameCredentials) {
        keyValueStore.putString(KEY_UUID, credentials.uuid)
        keyValueStore.putString(KEY_API_KEY, credentials.apiKey)
        keyValueStore.putString(KEY_SECRET_KEY_BASE64, credentials.secretKeyBase64)
        keyValueStore.putString(KEY_DISPLAY_NAME, credentials.displayName)
    }

    fun load(): SesameCredentials? {
        val uuid = keyValueStore.getString(KEY_UUID)
        val apiKey = keyValueStore.getString(KEY_API_KEY)
        val secretKeyBase64 = keyValueStore.getString(KEY_SECRET_KEY_BASE64)
        val displayName = keyValueStore.getString(KEY_DISPLAY_NAME).orEmpty()
        return if (uuid != null && apiKey != null && secretKeyBase64 != null) {
            SesameCredentials(uuid, apiKey, secretKeyBase64, displayName)
        } else {
            null
        }
    }

    fun clear() {
        keyValueStore.clear()
    }

    private companion object {
        const val KEY_UUID = "uuid"
        const val KEY_API_KEY = "api_key"
        const val KEY_SECRET_KEY_BASE64 = "secret_key_base64"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}

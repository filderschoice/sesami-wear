package com.sesamiwear.core

/**
 * 資格情報の実際の永続化先（EncryptedSharedPreferences等）を抽象化する。
 * Android依存の実装は mobile 側に置き、[SesameCredentialsStore] のロジックをユニットテスト可能にする。
 */
interface SesameKeyValueStore {
    fun putString(
        key: String,
        value: String,
    )

    fun getString(key: String): String?

    fun clear()
}

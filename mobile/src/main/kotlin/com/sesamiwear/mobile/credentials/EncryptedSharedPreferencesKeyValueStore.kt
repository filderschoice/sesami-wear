package com.sesamiwear.mobile.credentials

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sesamiwear.core.SesameKeyValueStore

/**
 * [SesameKeyValueStore] のEncryptedSharedPreferences実装。
 * SharedPreferences呼び出しのみの薄いアダプタのためユニットテスト対象外
 * （ロジック本体は[com.sesamiwear.core.SesameCredentialsStore]でテスト済み）。
 */
class EncryptedSharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : SesameKeyValueStore {
    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "sesami_wear_credentials"

        fun create(context: Context): EncryptedSharedPreferencesKeyValueStore {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            return EncryptedSharedPreferencesKeyValueStore(prefs)
        }
    }
}

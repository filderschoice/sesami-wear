package com.sesamiwear.mobile.state

import android.content.Context
import android.content.SharedPreferences
import com.sesamiwear.core.SesameKeyValueStore

/**
 * [SesameKeyValueStore] の非暗号化SharedPreferences実装。機密情報を含まない値（ロック状態など）の
 * 保存に使う（資格情報は[com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore]）。
 * SharedPreferences呼び出しのみの薄いアダプタのためユニットテスト対象外。
 */
class SharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : SesameKeyValueStore {
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
        private const val LOCK_STATE_PREFS_FILE_NAME = "sesami_wear_lock_state"
        private const val WIDGET_ASSIGNMENTS_PREFS_FILE_NAME = "sesami_wear_widget_assignments"

        /** ロック状態（[LockStateStore]）用のストア。 */
        fun forLockState(context: Context): SharedPreferencesKeyValueStore = create(context, LOCK_STATE_PREFS_FILE_NAME)

        /** ホーム画面ウィジェットの対象デバイス割り当て（BL-121）用のストア。 */
        fun forWidgetAssignments(context: Context): SharedPreferencesKeyValueStore =
            create(context, WIDGET_ASSIGNMENTS_PREFS_FILE_NAME)

        private fun create(
            context: Context,
            fileName: String,
        ) = SharedPreferencesKeyValueStore(
            context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE),
        )
    }
}

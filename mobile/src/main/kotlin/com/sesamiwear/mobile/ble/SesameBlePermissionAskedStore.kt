package com.sesamiwear.mobile.ble

import com.sesamiwear.core.SesameKeyValueStore

/**
 * BLE直接操作の権限を一度でも要求したかを覚える（BL-153）。
 *
 * Androidでは、いったん拒否された権限を再度要求してもダイアログが出ないことがある。
 * 「まだ要求していない」と「要求したが許可されなかった」を区別して、後者では端末の設定画面へ
 * 案内するために必要になる（[SesameBlePermissionPrompt.state]）。
 *
 * 真偽値1つで機密情報を含まないため、保存先は非暗号化のSharedPreferencesでよい。
 * 到達実績（[SesameBleReachability]）と同じファイルを別のキーで共有する。
 */
class SesameBlePermissionAskedStore(private val store: SesameKeyValueStore) {
    /** 一度でも権限を要求したか。 */
    fun wasAsked(): Boolean = store.getString(KEY_ASKED) == VALUE_TRUE

    /** 権限を要求したことを記録する。 */
    fun markAsked() {
        store.putString(KEY_ASKED, VALUE_TRUE)
    }

    private companion object {
        const val KEY_ASKED = "ble_permission_asked"
        const val VALUE_TRUE = "true"
    }
}

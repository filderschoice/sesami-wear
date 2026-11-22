package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameKeyValueStore

/**
 * 「送信後の状態確認」を行うかどうかの設定（BL-293）。
 *
 * **既定はOFF。** BL-142で自動の状態取得を廃止し、Sesame Web APIの月間リクエスト上限（BL-141）の消費を
 * 利用者のタップ回数と等しくした方針を維持するため。ONにした利用者だけが、施錠/解錠のたびに
 * APIを2回多く使うことを受け入れる。
 *
 * 全デバイス共通の値を1つ持つだけで機密情報を含まないため、BLEの到達実績と同じ非暗号化のファイルを
 * 別のキーで共有する（[com.sesamiwear.mobile.ble.SesameRouteNotificationStore]と同じ構成）。
 * Android非依存の純Kotlinとして切り出し、ユニットテストで検証する。
 */
class SesameVerifyAfterSendStore(private val store: SesameKeyValueStore) {
    /** 確認する設定か。未設定・未知の値なら既定（OFF）。 */
    fun isEnabled(): Boolean = store.getString(KEY_ENABLED) == ENABLED

    /** 設定を保存する。 */
    fun setEnabled(enabled: Boolean) {
        store.putString(KEY_ENABLED, if (enabled) ENABLED else DISABLED)
    }

    private companion object {
        const val KEY_ENABLED = "verify_after_send"
        const val ENABLED = "true"
        const val DISABLED = "false"
    }
}

package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.SesameCredentials

/**
 * 設定画面の入力値を検証する。uuid/apikeyの空欄保存を防ぎ、secretKeyBase64は
 * [SesameCredentials.secretKeyBytesOrNull] を用いてBase64形式かつAES-128鍵長（16バイト）
 * であることまで検証する（不正な鍵を保存すると施錠/解錠コマンド送信時にクラッシュしうるため）。
 */
object CredentialsInputValidator {
    fun isValid(
        uuid: String,
        apiKey: String,
        secretKeyBase64: String,
    ): Boolean {
        if (uuid.isBlank() || apiKey.isBlank()) return false
        val credentials = SesameCredentials(uuid = uuid, apiKey = apiKey, secretKeyBase64 = secretKeyBase64)
        return credentials.secretKeyBytesOrNull != null
    }
}

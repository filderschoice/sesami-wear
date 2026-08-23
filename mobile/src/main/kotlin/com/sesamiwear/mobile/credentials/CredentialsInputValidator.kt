package com.sesamiwear.mobile.credentials

import com.sesamiwear.core.SesameCredentials

/**
 * 設定画面の入力値を検証する。uuid/apikeyの空欄保存を防ぎ、secretKeyHexは
 * [SesameCredentials.secretKeyBytesOrNull] を用いて16進数形式かつAES-128鍵長（16バイト）
 * であることまで検証する（不正な鍵を保存すると施錠/解錠コマンド送信時にクラッシュしうるため）。
 */
object CredentialsInputValidator {
    fun isValid(
        uuid: String,
        apiKey: String,
        secretKeyHex: String,
    ): Boolean {
        if (uuid.isBlank() || apiKey.isBlank()) return false
        val credentials = SesameCredentials(uuid = uuid, apiKey = apiKey, secretKeyHex = secretKeyHex)
        return credentials.secretKeyBytesOrNull != null
    }
}

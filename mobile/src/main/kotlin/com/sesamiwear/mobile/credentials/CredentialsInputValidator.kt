package com.sesamiwear.mobile.credentials

/**
 * 設定画面の入力値を検証する。uuid/apikey/secretKeyのいずれかが空欄のまま保存できてしまうことを防ぐ。
 */
object CredentialsInputValidator {
    fun isValid(
        uuid: String,
        apiKey: String,
        secretKeyBase64: String,
    ): Boolean = uuid.isNotBlank() && apiKey.isNotBlank() && secretKeyBase64.isNotBlank()
}

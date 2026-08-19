package com.sesamiwear.core

import java.util.Base64

/**
 * Sesame APIの認証情報3点セット。
 * secretKeyはBase64文字列として保持し（ByteArrayをdata classへ直接持たせると
 * equals/hashCodeが参照比較になるのを避けるため）、利用側で[secretKeyBytes]経由でデコードする。
 */
data class SesameCredentials(
    val uuid: String,
    val apiKey: String,
    val secretKeyBase64: String,
) {
    val secretKeyBytes: ByteArray
        get() = Base64.getDecoder().decode(secretKeyBase64)
}

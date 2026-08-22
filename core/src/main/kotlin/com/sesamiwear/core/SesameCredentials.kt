package com.sesamiwear.core

import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * Sesame APIの認証情報3点セット。
 * secretKeyはBase64文字列として保持し（ByteArrayをdata classへ直接持たせると
 * equals/hashCodeが参照比較になるのを避けるため）、利用側で[secretKeyBytes]経由でデコードする。
 * uuidはSesame API上で既にデバイスを一意に識別するため、複数デバイス管理（BL-046）における
 * 識別子としてもuuidをそのまま用いる（別途deviceIdは持たない）。displayNameはユーザーが
 * 複数デバイスを区別するために設定する表示名（例:「玄関」）で、未設定時は空文字とする。
 */
@Serializable
data class SesameCredentials(
    val uuid: String,
    val apiKey: String,
    val secretKeyBase64: String,
    val displayName: String = "",
) {
    val secretKeyBytes: ByteArray
        get() = Base64.getDecoder().decode(secretKeyBase64)

    /**
     * secretKeyBase64がBase64として不正、またはデコード後の長さがAES-128鍵長（16バイト）と
     * 一致しない場合はnullを返す（例外を投げない安全なアクセサ）。設定画面での保存時バリデーション
     * （[secretKeyBytesOrNull]を使う側でチェックする）と、コマンド送信時の防御の両方に用いる。
     */
    val secretKeyBytesOrNull: ByteArray?
        get() {
            val decoded =
                try {
                    Base64.getDecoder().decode(secretKeyBase64)
                } catch (
                    @Suppress("SwallowedException") e: IllegalArgumentException,
                ) {
                    return null
                }
            return decoded.takeIf { it.size == AES_128_KEY_LENGTH_BYTES }
        }

    private companion object {
        const val AES_128_KEY_LENGTH_BYTES = 16
    }
}

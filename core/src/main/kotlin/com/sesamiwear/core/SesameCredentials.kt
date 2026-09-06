package com.sesamiwear.core

import kotlinx.serialization.Serializable
import java.util.HexFormat

/**
 * AES-128の鍵長（バイト）。
 *
 * [SesameCredentials]のcompanion objectではなくトップレベルへ置く。kotlinx.serializationは
 * `@Serializable`クラスのcompanion objectへ`serializer()`を生成するため、companionを
 * `private`にすると生成されたCompanionフィールドも`private`になり、他クラスからの
 * シリアライズ時に`IllegalAccessError: tried to access private field
 * SesameCredentials.Companion`が発生する。`@Serializable`クラスに`private companion object`を
 * 持たせてはならない。
 */
private const val AES_128_KEY_LENGTH_BYTES = 16

/**
 * Sesame APIの認証情報3点セット。
 * secretKeyは16進数文字列として保持する（CANDY HOUSE公式ドキュメント
 * `API_document/SesameOS3/webapi.md`のコード例が16進数32文字表現であるため、Base64ではない）。
 * ByteArrayをdata classへ直接持たせるとequals/hashCodeが参照比較になるため文字列で持ち、
 * 利用側で[secretKeyBytes]経由でデコードする。
 * uuidはSesame API上で既にデバイスを一意に識別するため、複数デバイス管理（BL-046）における
 * 識別子としてもuuidをそのまま用いる（別途deviceIdは持たない）。displayNameはユーザーが
 * 複数デバイスを区別するために設定する表示名（例:「玄関」）で、未設定時は空文字とする。
 */
@Serializable
data class SesameCredentials(
    val uuid: String,
    val apiKey: String,
    val secretKeyHex: String,
    val displayName: String = "",
) {
    val secretKeyBytes: ByteArray
        get() = HexFormat.of().parseHex(secretKeyHex)

    /**
     * secretKeyHexが16進数として不正、またはデコード後の長さがAES-128鍵長（16バイト）と
     * 一致しない場合はnullを返す（例外を投げない安全なアクセサ）。設定画面での保存時バリデーション
     * （[secretKeyBytesOrNull]を使う側でチェックする）と、コマンド送信時の防御の両方に用いる。
     */
    val secretKeyBytesOrNull: ByteArray?
        get() {
            val decoded =
                try {
                    HexFormat.of().parseHex(secretKeyHex)
                } catch (
                    @Suppress("SwallowedException") e: IllegalArgumentException,
                ) {
                    return null
                }
            return decoded.takeIf { it.size == AES_128_KEY_LENGTH_BYTES }
        }
}

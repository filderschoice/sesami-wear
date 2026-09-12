package com.sesamiwear.mobile.credentials

/**
 * 資格情報入力欄（uuid / apikey / secretKey）へ入力された文字列を、有効な値として取り得る形へ
 * その場で正規化する（BL-112）。Android非依存のためユニットテスト対象。
 *
 * 3項目の有効な値はいずれもASCII文字のみで構成される（uuidはUUID形式、apikeyは英数字、
 * secretKeyは16進数32文字）。日本語IMEでは全角英数字が入力されやすく、見た目では半角と
 * 区別しにくいまま保存され、署名検証がAPI側で失敗する原因になる。入力の時点で
 *
 * 1. 全角ASCII（U+FF01〜U+FF5E）を対応する半角へ変換し、
 * 2. ダッシュ類（全角ハイフン・長音記号など）を半角ハイフンへ寄せ、
 * 3. 各欄で許容しない文字を取り除く
 *
 * ことで、全角文字が値へ混入する経路そのものを塞ぐ。表示名（任意）は日本語を入力する項目の
 * ため対象外で、正規化しない。
 */
object CredentialsInputSanitizer {
    /** uuid欄。UUID形式（16進数とハイフン）を想定し、英数字とハイフンのみ残す。 */
    fun sanitizeUuid(input: String): String = normalizeAscii(input).filter { it.isAsciiAlphanumeric() || it == '-' }

    /** apikey欄。取り得る文字種が公表されていないため、空白を除くASCII印字可能文字をすべて許容する。 */
    fun sanitizeApiKey(input: String): String = normalizeAscii(input).filter { it.isAsciiPrintableNonSpace() }

    /**
     * secretKey欄。16進数32文字（AES-128鍵の16バイト、BL-058）のため16進数の文字だけを残し、
     * 32文字を超える入力は切り捨てる。貼り付け時に末尾へ紛れ込む改行・空白もここで落ちる。
     */
    fun sanitizeSecretKeyHex(input: String): String =
        normalizeAscii(input).filter { it.isHexDigit() }.take(SECRET_KEY_HEX_LENGTH)

    /** secretKeyの桁数（16進数32文字＝16バイト）。 */
    const val SECRET_KEY_HEX_LENGTH = 32

    /**
     * 全角ASCIIを半角へ、ダッシュ類を半角ハイフンへ寄せる。文字の除去は各欄の許容集合で行うため、
     * ここでは変換のみを担う。
     */
    private fun normalizeAscii(input: String): String =
        buildString(input.length) {
            input.forEach { char ->
                append(
                    when {
                        char in FULL_WIDTH_ASCII_RANGE -> char - FULL_WIDTH_ASCII_OFFSET
                        char in DASH_LIKE_CHARS -> '-'
                        else -> char
                    },
                )
            }
        }

    private fun Char.isAsciiAlphanumeric(): Boolean = this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isAsciiPrintableNonSpace(): Boolean = this.code in ASCII_PRINTABLE_FIRST..ASCII_PRINTABLE_LAST

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** 全角ASCII（！〜～）。同じ並びの半角ASCII（!〜~）とは[FULL_WIDTH_ASCII_OFFSET]だけ離れている。 */
    private val FULL_WIDTH_ASCII_RANGE = '\uFF01'..'\uFF5E'
    private const val FULL_WIDTH_ASCII_OFFSET = 0xFEE0

    /** 半角ハイフンへ寄せるダッシュ類（全角ハイフンは[FULL_WIDTH_ASCII_RANGE]側で変換される）。 */
    private val DASH_LIKE_CHARS = setOf('\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212', '\u30FC')

    /** 空白を除くASCII印字可能文字の範囲（`!`〜`~`）。 */
    private const val ASCII_PRINTABLE_FIRST = 0x21
    private const val ASCII_PRINTABLE_LAST = 0x7E
}

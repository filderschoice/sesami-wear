package com.sesamiwear.mobile.ble

/**
 * Sesame OS3（Sesame 5系）のBLE通信で使う定数とコード定義（BL-151）。
 *
 * 値はMITライセンスの非公式実装を参照して確定した（DESIGN.md「参照する外部実装」）。
 * 主に`meronepy/gomalock`（Python）の`_const.py` / `_protocol_types.py`、
 * 補助的に`homy-newfs8/libsesame3bt-core`（C++）のサービス・キャラクタリスティックUUID。
 * 公式SDKの取り込みは断念したため（DESIGN.md「公式SDK取り込みの実測」）、本アプリが必要とする
 * 施錠・解錠・状態取得の範囲だけを自前で定義する。
 *
 * Android非依存の純Kotlinとして書き、ユニットテストで検証する（`mobile`のテストはJVM上で動く）。
 */
object SesameBleProtocol {
    /** CANDY HOUSEのGATTサービスUUID。スキャンのフィルタにも使う。 */
    const val SERVICE_UUID = "0000fd81-0000-1000-8000-00805f9b34fb"

    /** コマンド送信用のキャラクタリスティック（Write Without Response）。 */
    const val WRITE_CHARACTERISTIC_UUID = "16860002-a5ae-9856-b6d3-dbb4c676993e"

    /** 通知受信用のキャラクタリスティック（Notify）。 */
    const val NOTIFY_CHARACTERISTIC_UUID = "16860003-a5ae-9856-b6d3-dbb4c676993e"

    /** Bluetooth SIGがCANDY HOUSE, Inc.へ割り当てた企業識別子。アドバタイズの製造者データの鍵。 */
    const val COMPANY_ID = 0x055A

    /**
     * 1パケットあたりの最大バイト数。MTUを広げずに既定の23バイト（ATTヘッダ3バイトを除いて20バイト）で
     * 送る前提の値で、先頭1バイトはヘッダに使うためペイロードは19バイトになる。
     */
    const val MTU_SIZE = 20

    /** 認証タグ長（バイト）。AES-CCMのmac_lenに相当する。 */
    const val TAG_LENGTH = 4

    /** セッショントークンの長さ（バイト）。AES-CCMのnonceの末尾に入る。 */
    const val SESSION_TOKEN_LENGTH = 4

    /** 履歴タグ（操作者名として端末の履歴に残る文字列）の最大バイト数。 */
    const val HISTORY_TAG_MAX_LENGTH = 20

    /** パケットヘッダ1バイトのビットフラグ。 */
    object PacketFlag {
        const val BEGINNING = 0b001
        const val PLAINTEXT_END = 0b010
        const val ENCRYPTED_END = 0b100
    }

    /** メッセージ種別。本アプリが扱うのは応答（RESPONSE）と通知（PUBLISH）の2つ。 */
    enum class OpCode(val code: Int) {
        RESPONSE(0x07),
        PUBLISH(0x08),
        ;

        companion object {
            fun fromCode(code: Int): OpCode? = entries.find { it.code == code }
        }
    }

    /**
     * 本アプリが送受信する項目コード。
     * gomalockの`ItemCode`は全項目を定義しているが、ここでは施錠・解錠・状態取得に必要なものだけに絞る
     * （未定義のコードを受け取っても無視できるよう、パース側はnullを許容する）。
     */
    enum class ItemCode(val code: Int) {
        LOGIN(2),
        INITIAL(14),
        MECH_SETTING(80),
        MECH_STATUS(81),
        LOCK(82),
        UNLOCK(83),
        ;

        companion object {
            fun fromCode(code: Int): ItemCode? = entries.find { it.code == code }
        }
    }

    /** 応答の結果コード。SUCCESS以外はすべて失敗として扱う。 */
    enum class ResultCode(val code: Int) {
        SUCCESS(0),
        INVALID_FORMAT(1),
        NOT_SUPPORTED(2),
        STORAGE_FAIL(3),
        INVALID_SIG(4),
        NOT_FOUND(5),
        UNKNOWN(6),
        BUSY(7),
        INVALID_PARAM(8),
        INVALID_ACTION(9),
        ;

        companion object {
            fun fromCode(code: Int): ResultCode? = entries.find { it.code == code }
        }
    }
}

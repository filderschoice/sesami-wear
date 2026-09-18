package com.sesamiwear.core

/**
 * 直近の状態取得・施錠/解錠が失敗した理由の分類（BL-140）。
 *
 * 利用者が自分で対処できるかどうかで2つに分ける。資格情報の失効とSesame Web APIの月間リクエスト
 * 上限の超過（BL-141）は利用者自身が対処できる唯一の失敗要因のため、他の失敗と同列に扱わない。
 *
 * [AUTH_OR_QUOTA]をさらに「資格情報の誤り」と「上限到達」へ分けることはできない。Sesame APIは
 * どちらもHTTP 403（本文も同一）で返すためで、429が返るかどうかもCANDY HOUSE側の実装次第で未確認
 * （BL-141）。そのため文言も両方を含む案内にする。
 *
 * @property shortLabel Tile・Complicationのような表示領域が狭い面で使う文言。過去に7文字の文言が
 * タイルの幅に収まらず末尾省略された（BL-102 / BL-104）ため5文字に収める。
 * @property detailedLabel ホーム画面ウィジェットのような表示領域に余裕がある面で使う文言。
 * 次に何をすればよいかを併記する。
 */
enum class SesameStatusFailure(
    val shortLabel: String,
    val detailedLabel: String,
) {
    /** HTTP 401 / 403 / 429。資格情報が拒否された、またはAPIの月間リクエスト上限に達した。 */
    AUTH_OR_QUOTA("認証エラー", "認証エラー（設定を確認）"),

    /** 圏外・タイムアウト・名前解決失敗・想定外の応答など、上記以外のすべて。 */
    COMMUNICATION("通信エラー", "通信エラー（電波状況を確認）"),
    ;

    companion object {
        /**
         * HTTPステータスコード（`core.api.SesameApiException.httpStatusCode`。通信失敗・解析失敗では
         * null）から分類する。401 / 403 / 429 を[AUTH_OR_QUOTA]、それ以外を[COMMUNICATION]とする。
         */
        fun of(httpStatusCode: Int?): SesameStatusFailure =
            if (httpStatusCode in AUTH_OR_QUOTA_STATUS_CODES) AUTH_OR_QUOTA else COMMUNICATION

        /** 保存・同期した値（[name]）から復元する。未知の値・nullは失敗なしとして扱う。 */
        fun ofNameOrNull(name: String?): SesameStatusFailure? = entries.find { it.name == name }

        /**
         * 「全デバイス」対象（BL-071）で複数台ぶんの失敗を1つへ集約する。
         * 1台でも[AUTH_OR_QUOTA]があればそれを優先し、次に[COMMUNICATION]、どれも無ければnull。
         * 利用者が対処できる失敗を、対処できない失敗に埋もれさせないため。
         */
        fun worstOf(failures: List<SesameStatusFailure?>): SesameStatusFailure? =
            when {
                failures.any { it == AUTH_OR_QUOTA } -> AUTH_OR_QUOTA
                failures.any { it == COMMUNICATION } -> COMMUNICATION
                else -> null
            }

        private val AUTH_OR_QUOTA_STATUS_CODES = setOf(401, 403, 429)
    }
}

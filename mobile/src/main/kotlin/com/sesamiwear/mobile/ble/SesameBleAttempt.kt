package com.sesamiwear.mobile.ble

/**
 * BLEを試した結果のうち、**次の経路選択に必要な情報だけ**を表す（BL-191）。
 *
 * [SesameBleClient.Result]は失敗の段階を6種類に分けるが、経路選択が知りたいのは
 * 「デバイスが圏内にいた証拠があるか」の1点だけなので、ここで2値へ落とす。
 * [com.sesamiwear.mobile.command.SesameBleAccess]はAndroid非依存のユニットテスト対象のため、
 * Android依存の[SesameBleClient]へ直接は依存させない。
 */
enum class SesameBleFailure {
    /**
     * デバイスを見つけられなかった、またはBLEそのものが使えなかった。
     * 圏外とみなし、到達実績を消してインターネット経由へ切り替える。
     */
    NOT_REACHED,

    /**
     * デバイスは見つかったが、接続・ログイン・コマンドのいずれかで失敗した。
     * 圏内にいる証拠があるため到達実績を残し、次の操作でもBLEを試す
     * （2026-09-20のユーザー指示「BLEでの接続をある程度諦めない形で」）。
     */
    REACHED_BUT_FAILED,
}

/**
 * BLE経路の実行結果（BL-191）。
 *
 * [value]が非nullなら成功で、そのときだけ[failure]がnullになる（逆も同じ）。
 * 失敗した場合に何を返せるかは操作ごとに違う（施錠/解錠は実測値、状態取得は施錠状態）ため、
 * 型引数で包む。
 */
data class SesameBleAttempt<out T>(
    /** 成功したときの結果。失敗したときはnull。 */
    val value: T?,
    /** 失敗した理由。成功したときはnull。 */
    val failure: SesameBleFailure?,
) {
    /** 圏内にいる証拠があるか。成功した場合と、見つかったが失敗した場合にtrue。 */
    val foundInRange: Boolean
        get() = value != null || failure == SesameBleFailure.REACHED_BUT_FAILED

    companion object {
        /** 成功した結果を包む。 */
        fun <T> succeeded(value: T): SesameBleAttempt<T> = SesameBleAttempt(value, null)

        /** 失敗した結果を包む。 */
        fun <T> failed(failure: SesameBleFailure): SesameBleAttempt<T> = SesameBleAttempt(null, failure)

        /** BLEを使わない既定の実装が返す「圏外」。 */
        fun <T> notReached(): SesameBleAttempt<T> = failed(SesameBleFailure.NOT_REACHED)
    }
}

/**
 * BLEの実行結果を[SesameBleAttempt]へ包む（BL-191）。
 *
 * [value]は成功したときに取り出せた値で、取り出せなかった場合はnull。
 * 失敗の段階を、経路選択が使う2値へ落とす。
 *
 * - [SesameBleClient.Result.NOT_FOUND]（探索で見つからない）と
 *   [SesameBleClient.Result.PERMISSION_DENIED]（権限が無くBLEを使えない）は、
 *   試し続けても成功しないため[SesameBleFailure.NOT_REACHED]とする。
 * - 接続以降で失敗した場合は、探索で見つかった直後か、保存済みアドレスへ直接つなげた途中なので、
 *   圏内にいる可能性が高い。2026-09-20の実機検証では、Sesameの目の前でも
 *   `CONNECTION_FAILED` / `LOGIN_FAILED`が出た。
 *
 * ただし全体の上限（[SesameBleClient.Timeouts.totalMillis]）で打ち切られた場合も
 * [SesameBleClient.Result.CONNECTION_FAILED]になるため、探索の途中で打ち切られたときは
 * 圏外なのに「圏内」と判定しうる。外れた場合の損は
 * [SesameBleReachability.MAX_CONSECUTIVE_FAILURES]回のBLE試行で頭打ちになる。
 */
fun <T> SesameBleClient.Result.toAttempt(value: T?): SesameBleAttempt<T> =
    when {
        this == SesameBleClient.Result.SUCCESS && value != null -> SesameBleAttempt.succeeded(value)
        this == SesameBleClient.Result.NOT_FOUND || this == SesameBleClient.Result.PERMISSION_DENIED ->
            SesameBleAttempt.failed(SesameBleFailure.NOT_REACHED)

        else -> SesameBleAttempt.failed(SesameBleFailure.REACHED_BUT_FAILED)
    }

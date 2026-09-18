package com.sesamiwear.core.api

/**
 * Sesame APIの呼び出しに失敗したことを表す。HTTPのエラー応答だけでなく、通信そのものの失敗
 * （圏外・タイムアウト・名前解決失敗）や応答本文の解析失敗も、この型へ正規化して送出する（BL-133）。
 *
 * 呼び出し側がこの型だけを捕捉すれば取りこぼしが無くなるようにするための取り決めで、
 * 以前は`java.io.IOException`等が素通りし、コルーチンから漏れてプロセスが落ちる経路があった。
 * [message]には資格情報（uuid・apikey・secretKey）も応答本文も含めない。原因例外は[cause]として保持する。
 *
 * @property httpStatusCode HTTPのエラー応答に由来する場合のステータスコード。通信失敗・解析失敗ではnull。
 * 失敗の種類（認証エラーなのか通信失敗なのか）を呼び出し側が区別するために保持する（BL-139）。
 */
class SesameApiException(
    message: String,
    cause: Throwable? = null,
    val httpStatusCode: Int? = null,
) : Exception(message, cause)

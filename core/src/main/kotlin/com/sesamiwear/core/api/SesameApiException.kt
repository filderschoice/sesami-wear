package com.sesamiwear.core.api

/**
 * Sesame APIの呼び出しに失敗したことを表す。HTTPのエラー応答だけでなく、通信そのものの失敗
 * （圏外・タイムアウト・名前解決失敗）や応答本文の解析失敗も、この型へ正規化して送出する（BL-133）。
 *
 * 呼び出し側がこの型だけを捕捉すれば取りこぼしが無くなるようにするための取り決めで、
 * 以前は`java.io.IOException`等が素通りし、コルーチンから漏れてプロセスが落ちる経路があった。
 * [message]には資格情報（uuid・apikey・secretKey）を含めない。原因例外は[cause]として保持する。
 */
class SesameApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

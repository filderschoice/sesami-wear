package com.sesamiwear.core.api

/**
 * Sesame 5施錠/解錠コマンドのコード。
 * 値はpysesame3（https://github.com/mochipon/pysesame3）のpysesame3/const.py `CHSesame2CMD`を参照。
 * TOGGLE/CLICKはPLAN.mdのUX要件で使用しないため未定義（LOCK/UNLOCKのみ扱う）。
 */
enum class SesameCommand(val code: Int) {
    LOCK(82),
    UNLOCK(83),
}

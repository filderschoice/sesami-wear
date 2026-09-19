package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleReachability
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * BLE経路の呼び出し口と、Web APIとの使い分け（BL-152）。
 *
 * [SesameDeviceCommandExecutor]はAndroid非依存のユニットテスト対象のため、BLEの実行そのものは
 * ラムダで受け取る。既定値はすべて「BLEを使わない」実装で、配線していない呼び出し元
 * （テストや、BLEを使わない構成）では従来どおりWeb APIだけが動く。
 *
 * 経路の決め方（DESIGN.md「経路の優先順位と切り替え条件」）:
 *
 * 1. [SesameBleReachability]が「直近にBLEで到達できた」と覚えているデバイスは、BLEを先に試す。
 *    成功すればSesame Web APIを呼ばないため、月間リクエスト上限（BL-141）を消費しない。
 * 2. 到達実績が無い、またはBLEが失敗した場合はWeb APIで実行する。このときWeb APIの通信と
 *    **並行して**短いスキャン（[probeReachable]）を行い、次回に備えて到達実績を取り直す。
 *    並行して行うため、利用者から見た所要時間はほぼ変わらない。
 *
 * **利用者は経路を意識しない。** 表示・操作・結果の見え方は経路によらず同じで、どちらで実行したかは
 * [logRoute]（診断ログ、BL-139）にのみ残す。
 */
class SesameBleAccess(
    val reachability: SesameBleReachability = SesameBleReachability(),
    private val executeOverBle: suspend (SesameCredentials, SesameCommand) -> Boolean = { _, _ -> false },
    private val fetchStatusOverBle: suspend (SesameCredentials) -> Boolean? = { null },
    private val probeReachable: suspend (SesameCredentials) -> Boolean = { false },
    private val logRoute: (String) -> Unit = {},
) {
    /**
     * BLEで[command]を実行できたらtrueを返す。到達実績が無い場合・失敗した場合はfalseで、
     * 呼び出し側はWeb APIへ倒す。試した結果は到達実績へ反映する。
     */
    suspend fun tryCommand(
        credentials: SesameCredentials,
        command: SesameCommand,
        nowMillis: Long,
    ): Boolean {
        if (!reachability.preferBle(credentials.uuid, nowMillis)) return false
        val succeeded = executeOverBle(credentials, command)
        reachability.record(credentials.uuid, nowMillis, succeeded)
        logRoute(describe(command.name, credentials.uuid, succeeded))
        return succeeded
    }

    /**
     * BLEで状態を取得できたら施錠中かどうかを返す。到達実績が無い場合・失敗した場合はnullで、
     * 呼び出し側はWeb APIへ倒す。
     */
    suspend fun tryStatus(
        credentials: SesameCredentials,
        nowMillis: Long,
    ): Boolean? {
        if (!reachability.preferBle(credentials.uuid, nowMillis)) return null
        val isLocked = fetchStatusOverBle(credentials)
        reachability.record(credentials.uuid, nowMillis, isLocked != null)
        logRoute(describe("STATUS", credentials.uuid, isLocked != null))
        return isLocked
    }

    /**
     * Web APIを呼ぶ[block]を実行しつつ、必要なら並行して到達確認のスキャンを行う。
     * スキャンの成否は次回の経路選択にだけ影響し、[block]の結果には影響しない。
     */
    suspend fun <T> withReachabilityProbe(
        credentials: SesameCredentials,
        nowMillis: Long,
        block: suspend () -> T,
    ): T =
        if (!reachability.shouldProbe(credentials.uuid, nowMillis)) {
            block()
        } else {
            coroutineScope {
                val probe = async { probeReachable(credentials) }
                try {
                    block()
                } finally {
                    reachability.recordProbe(credentials.uuid, nowMillis, probe.await())
                }
            }
        }

    /**
     * 診断ログの1行。資格情報・BLEアドレスは含めず、uuidは先頭8文字だけを残す
     * （`core.api.SesameApiFailureLog`と同じ方針、BL-139）。
     */
    private fun describe(
        operation: String,
        uuid: String,
        succeeded: Boolean,
    ): String {
        val device = uuid.take(UUID_LOG_PREFIX_LENGTH)
        val result = if (succeeded) "OK" else "NG"
        return "route=BLE op=$operation device=$device result=$result"
    }

    private companion object {
        const val UUID_LOG_PREFIX_LENGTH = 8
    }
}

package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.SesameStatusMeasurement
import com.sesamiwear.core.SesameStatusReading
import com.sesamiwear.core.SesameStatusRoute
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleAttempt
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
 * 利用者が設定画面で「常にインターネット経由」（[SesameRoutePolicy.WEB_API_ONLY]）を選んでいる場合は、
 * BLEの探索・接続・到達確認をいずれも行わない（BL-167）。
 *
 * どの経路で実行したかは[logRoute]（診断ログ、BL-139）へ残すほか、状態のスナップショットへ載せて
 * 利用者にも見せる（BL-166 / BL-168）。
 *
 * **BLEを試したのに届かずWeb APIへ倒れた場合は[onFallbackToWebApi]を呼ぶ**（BL-168）。
 * 利用者が切り替えに気づけるようにするためで、そもそもBLEを試していない場合
 * （到達実績が無い・権限が無い・方針が「常にインターネット経由」）は呼ばない。
 *
 * これとは別に、**実際に使った経路を[onRouteUsed]へ毎回渡す**（BL-190）。呼び出し側は前回と
 * 比べて変わったときだけ利用者へ知らせる（`SesameRouteChangeTracker`）。[onFallbackToWebApi]が
 * 「BLEを試して駄目だった瞬間」だけを表すのに対し、こちらは試していない場合も含めた結果を表す。
 */
class SesameBleAccess(
    val reachability: SesameBleReachability = SesameBleReachability(),
    private val routePolicy: () -> SesameRoutePolicy = { SesameRoutePolicy.DEFAULT },
    private val operations: SesameBleOperations = SesameBleOperations(),
    private val logRoute: (String) -> Unit = {},
    private val onFallbackToWebApi: () -> Unit = {},
    private val onRouteUsed: (SesameCredentials, SesameStatusRoute) -> Unit = { _, _ -> },
) {
    /**
     * BLEで[command]を実行できたら、そのとき分かった実測値（電池残量と経路、BL-166）を返す。
     * 到達実績が無い場合・失敗した場合はnullで、呼び出し側はWeb APIへ倒す。
     * 試した結果は到達実績へ反映する。
     */
    suspend fun tryCommand(
        credentials: SesameCredentials,
        command: SesameCommand,
        nowMillis: Long,
    ): SesameStatusMeasurement? {
        if (!allowsBle(credentials.uuid, nowMillis)) return null
        val attempt = operations.execute(credentials, command)
        recordAttempt(credentials, nowMillis, command.name, attempt)
        return attempt.value
    }

    /**
     * BLEで状態を取得できたら、施錠状態と実測値（電池残量・角度・経路）を返す。
     * 到達実績が無い場合・失敗した場合はnullで、呼び出し側はWeb APIへ倒す。
     */
    suspend fun tryStatus(
        credentials: SesameCredentials,
        nowMillis: Long,
    ): SesameStatusReading? {
        if (!allowsBle(credentials.uuid, nowMillis)) return null
        val attempt = operations.fetchStatus(credentials)
        recordAttempt(credentials, nowMillis, "STATUS", attempt)
        return attempt.value
    }

    /**
     * 利用者が状態の「更新」を操作したときの取得（BL-204）。
     *
     * [tryStatus]と違い、**到達実績の有無にかかわらずBLEを試す。** 到達実績は操作の契機でしか
     * 更新されないため、実績が切れた後は利用者がSesameの近くにいてもインターネット経由のままになり、
     * BLEへ戻る手段が無かった（2026-09-23のユーザー指示「BLE接続をユーザ任意のタイミングで」）。
     * 状態取得は利用者の操作でしか行わない（BL-142で自動取得を廃止）ため、自動で電力を消費することはない。
     *
     * BLEで届かなければ[api]（Web API）へ倒す。圏外と判定した場合（探索で見つからない）は、
     * Web APIと並行する到達確認（[withReachabilityProbe]）を**前回の確認からの間隔によらず**行う。
     * 利用者を待たせる経路の探索は短い（`SesameBleClient.Timeouts.scanMillis`）ため、BLEアドレスを
     * まだ覚えていないと近くにいても外れうる。長めの探索と最後に成功したアドレスへの接続（BL-193）で
     * アドレスを覚え直し、次の「更新」や施錠/解錠でBLEへ戻れるようにする。
     * 所要時間はBLEの上限3秒＋Web API最大6秒（到達確認4秒はWeb APIと並行）で、従来の設計値と同じ。
     * 方針が「常にインターネット経由」、またはBLEを使えない（権限が無い・Bluetoothが無効、
     * [SesameBleOperations.isAvailable]）場合は試さず、従来の[tryStatus]と同じ経路選択に任せる。
     */
    suspend fun checkStatus(
        credentials: SesameCredentials,
        nowMillis: Long,
        api: suspend () -> SesameStatusReading?,
    ): SesameStatusReading? {
        if (!routePolicy().allowsBle || !operations.isAvailable()) {
            return tryStatus(credentials, nowMillis) ?: withReachabilityProbe(credentials, nowMillis, block = api)
        }
        val attempt = operations.fetchStatus(credentials)
        recordAttempt(credentials, nowMillis, "STATUS", attempt)
        return attempt.value
            ?: withReachabilityProbe(credentials, nowMillis, forceProbe = !attempt.foundInRange, block = api)
    }

    /**
     * Web APIを呼ぶ[block]を実行しつつ、必要なら並行して到達確認のスキャンを行う。
     * スキャンの成否は次回の経路選択にだけ影響し、[block]の結果には影響しない。
     * [forceProbe]がtrueなら、前回の確認からの間隔によらず到達確認を行う（「更新」、BL-204）。
     */
    suspend fun <T> withReachabilityProbe(
        credentials: SesameCredentials,
        nowMillis: Long,
        forceProbe: Boolean = false,
        block: suspend () -> T,
    ): T {
        // ここへ来た時点でWeb APIを使うことが決まっている（BLEを試していない、または失敗した後）。
        onRouteUsed(credentials, SesameStatusRoute.WEB_API)
        val probes = forceProbe || reachability.shouldProbe(credentials.uuid, nowMillis)
        return if (!routePolicy().allowsBle || !probes) {
            block()
        } else {
            coroutineScope {
                val probe = async { operations.probeReachable(credentials) }
                try {
                    block()
                } finally {
                    reachability.recordProbe(credentials.uuid, nowMillis, probe.await())
                }
            }
        }
    }

    /**
     * BLEを試した結果を、到達実績・診断ログ・利用者への通知へ反映する。
     * 通知は**実際に試して失敗したときだけ**行う（試していない場合は呼ばれない）。
     *
     * 失敗しても、デバイスが圏内にいた証拠があるうちは到達実績を残して次の操作で再試行する
     * （[SesameBleAttempt.foundInRange]、BL-191）。打ち切りの判断は
     * [SesameBleReachability.record]が持つ。
     */
    private fun <T> recordAttempt(
        credentials: SesameCredentials,
        nowMillis: Long,
        operation: String,
        attempt: SesameBleAttempt<T>,
    ) {
        val succeeded = attempt.value != null
        reachability.record(credentials.uuid, nowMillis, succeeded, foundInRange = attempt.foundInRange)
        logRoute(describe(operation, credentials.uuid, succeeded))
        if (succeeded) {
            onRouteUsed(credentials, SesameStatusRoute.BLE)
        } else {
            onFallbackToWebApi()
        }
    }

    /** 方針が許し、かつ直近に到達できた実績がある場合だけBLEを試す。 */
    private fun allowsBle(
        uuid: String,
        nowMillis: Long,
    ): Boolean = routePolicy().allowsBle && reachability.preferBle(uuid, nowMillis)

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

/**
 * BLE経路の実行そのもの（BL-152 / BL-166）。
 *
 * [SesameBleAccess]はAndroid非依存のユニットテスト対象のため、実際のBLE通信はここへラムダとして
 * 切り出して注入する。既定値はすべて「BLEを使わない」実装で、配線していない呼び出し元
 * （テストや、BLEを使わない構成）では従来どおりWeb APIだけが動く。
 * 3つをまとめて1つの引数にしているのは、[SesameBleAccess]の引数の数を抑えるためでもある。
 *
 * 失敗した場合は理由（圏外か、見つかったが失敗したか）を[SesameBleAttempt]へ載せて返す。
 * 経路選択が次にBLEを試すかどうかの判断に使う（BL-191）。
 *
 * @property execute 施錠/解錠。成功したらそのとき分かった実測値。
 * @property fetchStatus 状態取得。成功したら施錠状態と実測値。
 * @property probeReachable スキャンだけの到達確認。接続もログインもしない。
 * @property isAvailable BLEをいま使えるか（権限があり、Bluetoothが有効か）。到達実績によらずBLEを試す
 * 「更新」（[SesameBleAccess.checkStatus]、BL-204）が、試しても必ず失敗する場面で待ち時間と
 * フォールバックの通知を出さないために使う。
 */
class SesameBleOperations(
    val execute: suspend (SesameCredentials, SesameCommand) -> SesameBleAttempt<SesameStatusMeasurement> =
        { _, _ -> SesameBleAttempt.notReached() },
    val fetchStatus: suspend (SesameCredentials) -> SesameBleAttempt<SesameStatusReading> =
        { SesameBleAttempt.notReached() },
    val probeReachable: suspend (SesameCredentials) -> Boolean = { false },
    val isAvailable: () -> Boolean = { false },
)

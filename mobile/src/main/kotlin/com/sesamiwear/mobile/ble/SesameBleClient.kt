package com.sesamiwear.mobile.ble

import android.content.Context
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.ble.SesameBleProtocol.ItemCode
import com.sesamiwear.mobile.ble.SesameBleProtocol.ResultCode
import kotlinx.coroutines.withTimeoutOrNull

/**
 * BLE経由でSesame 5を操作するクライアント（BL-151）。
 *
 * 1回の操作につき「スキャン → 接続 → ログイン → コマンド送信 → 切断」を完結させる（接続を持ち続けない）。
 * 常時接続はバックグラウンド実行の制約と電力消費の両面で割に合わず、本アプリの操作は
 * ウィジェット・Tileのタップ起点で散発的なため、都度接続の方が実装も状態管理も単純になる。
 *
 * 本段階（BL-151）では経路の自動切り替えを行わない。Web APIとの使い分けはBL-152で
 * [com.sesamiwear.mobile.command.SesameDeviceCommandExecutor]へ入れる。
 *
 * secretKeyは`mobile`のみが保持する方針を維持し、`wear`へは渡さない
 * （DESIGN.md「アーキテクチャ方針」）。鍵・セッション鍵・トークンはログへ出さない。
 */
class SesameBleClient(
    private val context: Context,
    private val scanner: SesameBleScanner = SesameBleScanner(context),
    private val timeouts: Timeouts = Timeouts(),
) {
    /**
     * 各段階の上限時間（ミリ秒）。BL-152の経路選択では合計2秒程度へ絞る必要があるため、
     * 呼び出し側が用途に応じて差し替えられるようにする（既定値は単体検証向けの緩い値）。
     */
    data class Timeouts(
        val scanMillis: Long = 3_000,
        val connectMillis: Long = 5_000,
        val loginMillis: Long = 3_000,
        val commandMillis: Long = 3_000,
    )

    /** BLE操作の結果。失敗の理由は診断ログ（BL-139）と呼び出し側の分岐に使う。 */
    enum class Result {
        SUCCESS,
        PERMISSION_DENIED,
        NOT_FOUND,
        CONNECTION_FAILED,
        LOGIN_FAILED,
        COMMAND_REJECTED,
    }

    /** 状態取得の結果。[status]は[result]が[Result.SUCCESS]のときだけ非nullになる。 */
    data class StatusResult(
        val result: Result,
        val status: SesameBleMechStatus?,
    )

    /**
     * [credentials]のデバイスへ[command]（施錠/解錠）をBLEで送る。
     * [historyName]はデバイスの操作履歴に残る名前で、空文字なら既定値を使う。
     */
    suspend fun execute(
        credentials: SesameCredentials,
        command: SesameCommand,
        historyName: String = DEFAULT_HISTORY_NAME,
    ): Result =
        withSession(credentials) { connection, session, _ ->
            val itemCode = if (command == SesameCommand.LOCK) ItemCode.LOCK else ItemCode.UNLOCK
            val payload = SesameBleMessage.encodeHistoryTag(historyName.ifBlank { DEFAULT_HISTORY_NAME })
            sendEncrypted(connection, session, itemCode, payload)
        }.first

    /** [credentials]のデバイスの機構状態をBLEで取得する。ログイン直後に届く通知をそのまま使う。 */
    suspend fun fetchStatus(credentials: SesameCredentials): StatusResult {
        val (result, status) = withSession(credentials) { _, _, initialStatus -> initialStatus?.let { Result.SUCCESS } }
        return StatusResult(result, status)
    }

    /**
     * スキャンからログインまでを行い、[block]を実行してから必ず切断する。
     * [block]がnullを返した場合は[Result.COMMAND_REJECTED]として扱う。
     * 戻り値の2つめは、ログイン時に受け取った機構状態（受け取れていなければnull）。
     */
    private suspend fun withSession(
        credentials: SesameCredentials,
        block: suspend (SesameBleConnection, SesameBleSession, SesameBleMechStatus?) -> Result?,
    ): Pair<Result, SesameBleMechStatus?> {
        val secretKey = credentials.secretKeyBytesOrNull
        val blocked = preflight(secretKey)
        if (blocked != null) return blocked to null
        val device = scanner.findDevice(credentials.uuid, timeouts.scanMillis)
        val connection =
            device?.let { withTimeoutOrNull(timeouts.connectMillis) { SesameBleConnection.open(context, it) } }
        return when {
            device == null -> Result.NOT_FOUND to null
            connection == null -> Result.CONNECTION_FAILED to null
            else -> runSession(connection, secretKey!!, block)
        }
    }

    /** BLEを試す前に分かる失敗（権限が無い、保存済みの鍵が不正）。問題なければnullを返す。 */
    private fun preflight(secretKey: ByteArray?): Result? =
        when {
            !SesameBlePermissions.hasAll(context) -> Result.PERMISSION_DENIED
            secretKey == null -> Result.LOGIN_FAILED
            else -> null
        }

    /** ログインして[block]を実行し、成否によらず必ず切断する。 */
    private suspend fun runSession(
        connection: SesameBleConnection,
        secretKey: ByteArray,
        block: suspend (SesameBleConnection, SesameBleSession, SesameBleMechStatus?) -> Result?,
    ): Pair<Result, SesameBleMechStatus?> =
        try {
            val session = login(connection, secretKey)
            if (session == null) {
                Result.LOGIN_FAILED to null
            } else {
                val status = awaitMechStatus(connection, session)
                (block(connection, session, status) ?: Result.COMMAND_REJECTED) to status
            }
        } finally {
            connection.close()
        }

    /**
     * 接続直後に届く`INITIAL`通知（4バイトのセッショントークン）を受け、セッション鍵を導出して
     * `LOGIN`コマンドを平文で送る。応答が成功なら確立したセッションを返す。
     */
    private suspend fun login(
        connection: SesameBleConnection,
        secretKey: ByteArray,
    ): SesameBleSession? {
        val token =
            SesameBleMessageReader.awaitPublish(connection, ItemCode.INITIAL, timeouts.loginMillis)
                ?.takeIf { it.size == SesameBleProtocol.SESSION_TOKEN_LENGTH }
                ?: return null
        val session = SesameBleSession(secretKey, token)
        val sent = connection.write(SesameBleMessage.encodeCommand(ItemCode.LOGIN, session.loginPayload), false)
        val response =
            if (sent) {
                SesameBleMessageReader.awaitResponse(connection, ItemCode.LOGIN, timeouts.loginMillis, session)
            } else {
                null
            }
        return session.takeIf { response?.resultCode == ResultCode.SUCCESS }
    }

    /**
     * ログイン後にデバイスが送ってくる`MECH_STATUS`通知を待つ。
     * 状態取得はこの通知だけで完結し、追加のコマンドを送る必要がない。
     */
    private suspend fun awaitMechStatus(
        connection: SesameBleConnection,
        session: SesameBleSession,
    ): SesameBleMechStatus? =
        SesameBleMessageReader.awaitPublish(connection, ItemCode.MECH_STATUS, timeouts.loginMillis, session)
            ?.let(SesameBleMechStatus::parse)

    private suspend fun sendEncrypted(
        connection: SesameBleConnection,
        session: SesameBleSession,
        itemCode: ItemCode,
        payload: ByteArray,
    ): Result? {
        val command = session.encrypt(SesameBleMessage.encodeCommand(itemCode, payload))
        if (!connection.write(command, true)) return Result.CONNECTION_FAILED
        val response = SesameBleMessageReader.awaitResponse(connection, itemCode, timeouts.commandMillis, session)
        return if (response?.resultCode == ResultCode.SUCCESS) Result.SUCCESS else Result.COMMAND_REJECTED
    }

    companion object {
        /** デバイスの操作履歴へ残す既定の名前。 */
        const val DEFAULT_HISTORY_NAME = "Sesami Wear"
    }
}

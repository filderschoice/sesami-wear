package com.sesamiwear.mobile.ble

import android.content.Context
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.core.ble.SesameBleProbeStage
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
    /**
     * 見つけたBLEアドレスの保存先（BL-189）。nullなら毎回探索する（既定。単体検証向け）。
     */
    private val addressCache: SesameBleAddressCache? = null,
) {
    private val connector = SesameBleConnector(context, scanner, addressCache)

    /**
     * 各段階の上限時間（ミリ秒）。BL-152の経路選択では合計2秒程度へ絞る必要があるため、
     * 呼び出し側が用途に応じて差し替えられるようにする（既定値は単体検証向けの緩い値）。
     *
     * 拘束力を持つのは[totalMillis]で、各段階の上限はその内訳の目安にすぎない
     * （段階の合計が[totalMillis]を超えていても、全体はこの値で打ち切られる）。
     */
    data class Timeouts(
        val totalMillis: Long = 12_000,
        val scanMillis: Long = 3_000,
        val connectMillis: Long = 5_000,
        val loginMillis: Long = 3_000,
        val commandMillis: Long = 3_000,
        val probeMillis: Long = 2_000,
        val probeConnectMillis: Long = 1_000,
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
     * 施錠/解錠の結果（BL-166）。[status]はログイン直後に届いた機構状態で、
     * **コマンドを送る前の値**である点に注意する。電池残量は数秒で変わらないため利用できるが、
     * サムターンの角度は操作前のものになるため、呼び出し側は使わない。
     */
    data class CommandOutcome(
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
    ): CommandOutcome {
        val (result, status) =
            withBudget(credentials, NO_STAGE, retain = false) { connection, session, _ ->
                val itemCode = if (command == SesameCommand.LOCK) ItemCode.LOCK else ItemCode.UNLOCK
                val payload = SesameBleMessage.encodeHistoryTag(historyName.ifBlank { DEFAULT_HISTORY_NAME })
                sendEncrypted(connection, session, itemCode, payload)
            }
        return CommandOutcome(result, status)
    }

    /**
     * [credentials]のデバイスの機構状態をBLEで取得する。ログイン直後に届く通知をそのまま使う。
     *
     * @param onStage 接続・ログインに進むたびに呼ぶ（「Bluetooth接続を確認」の進捗表示用、BL-272）。
     * 既定では何もしない。
     * @param retain 成功したら、ログイン済みの接続を[SesameBleHeldSessions.TTL_MILLIS]だけ保持する（BL-279）。
     * 保持中の次の操作は、探索・接続・ログインを省いて再利用する。「Bluetooth接続を確認」だけがtrueで呼ぶ。
     */
    suspend fun fetchStatus(
        credentials: SesameCredentials,
        onStage: (SesameBleProbeStage) -> Unit = {},
        retain: Boolean = false,
    ): StatusResult {
        val (result, status) =
            withBudget(credentials, onStage, retain) { _, _, initialStatus -> initialStatus?.let { Result.SUCCESS } }
        return StatusResult(result, status)
    }

    /**
     * [deviceUuid]のデバイスが電波圏内にいるかだけを確かめる（BL-152）。ログインまでは行わないため
     * 短時間で済み、Web APIの通信と並行して呼ぶ用途を想定している。
     *
     * 見つけたアドレスはここで覚える。利用者を待たせない経路（Web APIと並行）で探索を済ませ、
     * 実際の操作では直接接続だけを行うための仕込み（BL-189）。探索が外れたときに
     * 最後に成功したアドレスへ直接つないで確かめる手順は[SesameBleConnector.probeReachable]が持つ
     * （BL-193）。
     */
    suspend fun probeReachable(deviceUuid: String): Boolean {
        if (!SesameBlePermissions.hasAll(context)) return false
        return connector.probeReachable(deviceUuid, timeouts.probeMillis, timeouts.probeConnectMillis)
    }

    /**
     * [withSession]を全体の上限[Timeouts.totalMillis]で囲む。打ち切られた場合は
     * [Result.CONNECTION_FAILED]として扱い、呼び出し側（BL-152の経路選択）がWeb APIへ倒せるようにする。
     */
    private suspend fun withBudget(
        credentials: SesameCredentials,
        onStage: (SesameBleProbeStage) -> Unit,
        retain: Boolean,
        block: suspend (SesameBleConnection, SesameBleSession, SesameBleMechStatus?) -> Result?,
    ): Pair<Result, SesameBleMechStatus?> =
        withTimeoutOrNull(timeouts.totalMillis) { withSession(credentials, onStage, retain, block) }
            ?: (Result.CONNECTION_FAILED to null)

    /**
     * 保持している接続（BL-279）があればそれを再利用し、無ければスキャンからログインまでを行って、[block]を実行する。
     * 保持を使えず**送る前に**失敗した場合（相手側から切られていた）は通常の接続へ戻る。送ったあとの失敗は、
     * 二重に送らないよう戻らず、そのまま返す。保持を使い終わって成功したら保持を続け（時間切れを数え直す）、
     * 失敗したら閉じる。通常の接続は、[retain]がtrueで成功したときだけ保持し、そうでなければ必ず切断する。
     * [block]がnullを返した場合は[Result.COMMAND_REJECTED]として扱う。
     * 戻り値の2つめは、ログイン時（保持の再利用なら最新）に受け取った機構状態（受け取れていなければnull）。
     */
    private suspend fun withSession(
        credentials: SesameCredentials,
        onStage: (SesameBleProbeStage) -> Unit,
        retain: Boolean,
        block: suspend (SesameBleConnection, SesameBleSession, SesameBleMechStatus?) -> Result?,
    ): Pair<Result, SesameBleMechStatus?> {
        val secretKey = credentials.secretKeyBytesOrNull
        val blocked = preflight(secretKey)
        if (blocked == null) {
            val held = SesameBleHeldSessions.take(credentials.uuid)
            if (held != null) {
                val outcome = held.run(block)
                if (outcome.first == Result.SUCCESS) {
                    SesameBleHeldSessions.retain(
                        credentials.uuid,
                        held,
                    )
                } else {
                    held.close()
                }
                if (outcome.first != Result.CONNECTION_FAILED) return outcome
            }
        }
        return if (blocked != null) {
            blocked to null
        } else {
            onStage(SesameBleProbeStage.CONNECTING)
            val attempt = connector.connect(credentials.uuid, timeouts.scanMillis, timeouts.connectMillis)
            val connection = attempt.connection
            if (connection == null) {
                (attempt.failure ?: Result.CONNECTION_FAILED) to null
            } else {
                runSession(connection, credentials, onStage, retain, block)
            }
        }
    }

    /** BLEを試す前に分かる失敗（権限が無い、保存済みの鍵が不正）。問題なければnullを返す。 */
    private fun preflight(secretKey: ByteArray?): Result? =
        when {
            !SesameBlePermissions.hasAll(context) -> Result.PERMISSION_DENIED
            secretKey == null -> Result.LOGIN_FAILED
            else -> null
        }

    /**
     * ログインして[block]を実行する。[retain]がtrueで成功したら接続を保持し（BL-279）、そうでなければ成否によらず必ず切断する。
     */
    private suspend fun runSession(
        connection: SesameBleConnection,
        credentials: SesameCredentials,
        onStage: (SesameBleProbeStage) -> Unit,
        retain: Boolean,
        block: suspend (SesameBleConnection, SesameBleSession, SesameBleMechStatus?) -> Result?,
    ): Pair<Result, SesameBleMechStatus?> {
        var held: SesameBleHeldSession? = null
        return try {
            onStage(SesameBleProbeStage.LOGGING_IN)
            val session = login(connection, checkNotNull(credentials.secretKeyBytesOrNull))
            if (session == null) {
                Result.LOGIN_FAILED to null
            } else {
                val status = awaitMechStatus(connection, session)
                val outcome = (block(connection, session, status) ?: Result.COMMAND_REJECTED) to status
                if (retain && outcome.first == Result.SUCCESS) held = SesameBleHeldSession(connection, session, status)
                outcome
            }
        } finally {
            val kept = held
            if (kept != null) SesameBleHeldSessions.retain(credentials.uuid, kept) else connection.close()
        }
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

        private val NO_STAGE: (SesameBleProbeStage) -> Unit = {}
    }
}

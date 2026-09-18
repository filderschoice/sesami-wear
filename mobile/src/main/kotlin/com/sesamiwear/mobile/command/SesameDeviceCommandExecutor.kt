package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameStatusFailure
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameApiException
import com.sesamiwear.core.api.SesameApiFailureLog
import com.sesamiwear.core.api.SesameApiOperation
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.BuildConfig
import com.sesamiwear.mobile.messaging.CommandDebouncer
import com.sesamiwear.mobile.messaging.SesameCommandHandler
import com.sesamiwear.mobile.state.LockStateStore

/**
 * mobile端末内でSesameデバイスへ施錠・解錠・状態取得を行う実行口（BL-120）。
 *
 * もとはウォッチからのメッセージを受ける`SesameMessageListenerService`のprivateメソッドに閉じていたが、
 * mobileのホーム画面ウィジェット（BL-122）もData Layerを経由せず同じ処理を呼ぶため、Android非依存の
 * クラスとして切り出した。資格情報の検索、[CommandDebouncer]による重複の抑止、[SesameCommandHandler]での
 * コマンド送信、成功時のロック状態の保存（[LockStateStore]）と変更通知（[LockStateNotifier]）を担う。
 *
 * [debouncer]はウォッチ経由とウィジェット経由で同じインスタンス（[sharedDebouncer]）を渡し、
 * 同一uuidへの2秒以内の重複を経路をまたいで無視する（BL-062の多重送信・多重ハプティクス防止を維持）。
 *
 * デモ用デバイス（`SesameDemoMode.DEMO_DEVICE_UUID`、BL-123）はmobile端末内だけで状態を持ち、
 * Sesame APIへもウォッチへも一切送らない（ウォッチ側のデモ状態とは同期せず、端末ごとに独立して体験する）。
 *
 * Sesame APIの呼び出し口と失敗ログの出力先は[SesameApiAccess]としてまとめて受け取る（BL-139）。
 */
class SesameDeviceCommandExecutor(
    private val loadCredentials: () -> List<SesameCredentials>,
    private val lockStateStore: LockStateStore,
    private val notifier: LockStateNotifier,
    private val apiAccess: SesameApiAccess = SesameApiAccess(),
    private val debouncer: CommandDebouncer = sharedDebouncer,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** 施錠/解錠の実行結果。[DEBOUNCED]は重複として無視した（APIを呼んでいない）ことを表す。 */
    enum class Outcome {
        SUCCESS,
        FAILURE,
        DEBOUNCED,
    }

    /**
     * [uuid]のデバイスへ[command]を送る。処理順は、重複判定 → 資格情報の検索 → API呼び出し →
     * 成功時のみ状態保存と通知（移設前の`SesameMessageListenerService`と同じ）。
     * 資格情報が無い、または保存済みの鍵が不正な場合はAPIを呼ばず[Outcome.FAILURE]を返す（BL-026）。
     * デモ用デバイスはAPIを呼ばず常に成功とし、端末内の状態だけを書き換える（BL-123）。
     */
    suspend fun execute(
        uuid: String,
        command: SesameCommand,
    ): Outcome {
        if (!debouncer.shouldProcess(uuid)) return Outcome.DEBOUNCED
        val succeeded = SesameDemoMode.isDemoDevice(uuid) || sendCommand(uuid, command)
        if (succeeded) {
            // 送信したコマンドが意図した状態をそのまま保存する（BL-015の簡略化ロジックを維持）。
            updateLockState(uuid, isLocked = command == SesameCommand.LOCK)
        }
        return if (succeeded) Outcome.SUCCESS else Outcome.FAILURE
    }

    private suspend fun sendCommand(
        uuid: String,
        command: SesameCommand,
    ): Boolean {
        val credentials = findCredentials(uuid)
        val secretKey = credentials?.secretKeyBytesOrNull ?: return false
        // 失敗の分類はコールバックで受け取る（コールバックはsuspendでないためその場では保存できない）。
        var failure: SesameStatusFailure? = null
        apiAccess.recordApiCall()
        val handler =
            SesameCommandHandler(
                apiClient = apiAccess.clientFactory(credentials),
                secretKey = secretKey,
                onFailure = { e ->
                    apiAccess.logFailure(SesameApiFailureLog.describe(operationOf(command), e))
                    failure = SesameStatusFailure.of(e.httpStatusCode)
                },
            )
        val succeeded = handler.execute(command) == SesameCommandResult.SUCCESS
        failure?.let { recordFailure(uuid, it) }
        return succeeded
    }

    /**
     * [uuid]のデバイスの状態をSesame APIのGETで取得し、保存と通知を行う。
     * 取得できた場合はその施錠状態、資格情報が無い・APIエラーの場合はnullを返す（保存・通知はしない）。
     * 状態取得は施錠/解錠ではないため[debouncer]の対象外（移設前と同じ）。
     */
    suspend fun refreshStatus(uuid: String): Boolean? {
        if (SesameDemoMode.isDemoDevice(uuid)) {
            // デモは取得先が無いため、保存済み（無ければ初期状態）をそのまま返し、保存・通知もしない。
            return lockStateStore.load(uuid)?.isLocked ?: SesameDemoMode.INITIAL_IS_LOCKED
        }
        val isLocked = fetchIsLocked(uuid)
        if (isLocked != null) updateLockState(uuid, isLocked)
        return isLocked
    }

    private suspend fun fetchIsLocked(uuid: String): Boolean? {
        val credentials = findCredentials(uuid) ?: return null
        apiAccess.recordApiCall()
        return try {
            apiAccess.clientFactory(credentials).getStatus().isInLockRange
        } catch (e: SesameApiException) {
            // 呼び出し元（ウォッチ・ウィジェット）へは「取得できなかった」ことだけを伝える。
            // 切り分けに必要な情報はlogcatへ残し（BL-139）、失敗の分類は表示のため保存する（BL-140）。
            apiAccess.logFailure(SesameApiFailureLog.describe(SesameApiOperation.STATUS, e))
            recordFailure(uuid, SesameStatusFailure.of(e.httpStatusCode))
            null
        }
    }

    /**
     * 直近の取得・操作が失敗したことを保存し、ウォッチ・ウィジェットへ知らせる（BL-140）。
     * 最後に分かった施錠状態は残したまま、失敗の分類だけを上書きする。
     * デモ用デバイスはAPIを呼ばないため失敗も起こらない。
     */
    private suspend fun recordFailure(
        uuid: String,
        failure: SesameStatusFailure,
    ) {
        lockStateStore.saveFailure(uuid, failure)
        notifySnapshot(uuid)
    }

    private fun operationOf(command: SesameCommand): SesameApiOperation =
        when (command) {
            SesameCommand.LOCK -> SesameApiOperation.LOCK
            SesameCommand.UNLOCK -> SesameApiOperation.UNLOCK
        }

    private fun findCredentials(uuid: String): SesameCredentials? = loadCredentials().find { it.uuid == uuid }

    private suspend fun updateLockState(
        uuid: String,
        isLocked: Boolean,
    ) {
        lockStateStore.save(uuid, isLocked, nowMillis())
        notifySnapshot(uuid)
    }

    private suspend fun notifySnapshot(uuid: String) {
        val snapshot = lockStateStore.load(uuid) ?: SesameStatusSnapshot(isLocked = null, updatedAtEpochMillis = null)
        if (!SesameDemoMode.isDemoDevice(uuid)) notifier.watch.onStatusChanged(uuid, snapshot)
        notifier.local.onStatusChanged(uuid, snapshot)
    }

    companion object {
        /**
         * プロセス内で共有する[CommandDebouncer]。Serviceインスタンスをまたいで連打を検知でき、
         * ウォッチ経由とウィジェット経由の重複も1つにまとめる（BL-062 / BL-120）。
         */
        val sharedDebouncer = CommandDebouncer()
    }
}

/**
 * Sesame APIの呼び出し口（[clientFactory]）と、その失敗を1行残す出力先（[logFailure]）をまとめたもの。
 *
 * [SesameDeviceCommandExecutor]はAndroid非依存のユニットテスト対象のため`android.util.Log`を
 * 直接呼べない。[logFailure]には[SesameApiFailureLog]が組み立てた、資格情報も応答本文も含まない
 * 文字列だけを渡す（BL-139）。既定では何もしないため、ログが不要な呼び出し元は省略できる。
 *
 * [recordApiCall]は実際にSesame APIを1回呼ぶ直前に呼ばれる（BL-147）。月間リクエスト上限
 * （BL-141）に対する消費量を利用者へ示すためのカウンタで、成否によらず数える（上限は成功・失敗を
 * 問わず消費されるため）。デモ用デバイス・重複として無視した操作・資格情報が無い場合はAPIを
 * 呼ばないため数えない。既定では何もしない。
 */
class SesameApiAccess(
    val clientFactory: (SesameCredentials) -> SesameApiClient = ::defaultApiClient,
    val logFailure: (String) -> Unit = {},
    val recordApiCall: () -> Unit = {},
) {
    companion object {
        /**
         * 既定のAPIクライアント。デバッグビルドで`-PsesameApiBaseUrl`が指定されたときだけ接続先を
         * 差し替える（BL-132）。実資格情報・実デバイスを使わずに、施錠/解錠の成功を起点とする
         * 状態同期（ウィジェット⇔ウォッチのTile）を検証するためのモック接続用で、
         * リリースビルドでは値が空のため常に本番URLになる。
         */
        private fun defaultApiClient(credentials: SesameCredentials): SesameApiClient =
            if (BuildConfig.DEBUG && BuildConfig.SESAME_API_BASE_URL.isNotEmpty()) {
                SesameApiClient(
                    uuid = credentials.uuid,
                    apiKey = credentials.apiKey,
                    baseUrl = BuildConfig.SESAME_API_BASE_URL,
                )
            } else {
                SesameApiClient(uuid = credentials.uuid, apiKey = credentials.apiKey)
            }
    }
}

/**
 * ロック状態が変わった（施錠/解錠の成功、状態取得の成功）ことの通知先（BL-120 / BL-123）。
 * [local]はすべての変化（ホーム画面ウィジェットの再描画など端末内で完結するもの）、
 * [watch]は実デバイスの変化だけ（ウォッチへのDataItem同期。デモ用デバイスでは呼ばない）を受ける。
 * 実デバイスの変化では[watch]→[local]の順に呼ぶ。
 */
class LockStateNotifier(
    val local: LockStateListener,
    val watch: LockStateListener,
)

/** [LockStateNotifier]の通知先1つ分。 */
fun interface LockStateListener {
    suspend fun onStatusChanged(
        uuid: String,
        snapshot: SesameStatusSnapshot,
    )
}

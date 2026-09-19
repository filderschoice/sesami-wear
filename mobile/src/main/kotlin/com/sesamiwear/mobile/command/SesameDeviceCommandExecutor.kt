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
 * 施錠/解錠と状態取得は別々のキーで数えるため、施錠した直後に状態を取り直すことはできる（BL-148）。
 *
 * デモ用デバイス（`SesameDemoMode.DEMO_DEVICE_UUID`、BL-123）はmobile端末内だけで状態を持ち、
 * Sesame APIへもウォッチへも一切送らない（ウォッチ側のデモ状態とは同期せず、端末ごとに独立して体験する）。
 *
 * Sesame APIの呼び出し口と失敗ログの出力先は[SesameApiAccess]としてまとめて受け取る（BL-139）。
 *
 * 施錠/解錠と状態取得は、BLEで到達できるデバイスならBLE、それ以外はWeb APIで実行する（[SesameBleAccess]、
 * BL-152）。BLEで成功した場合はWeb APIを呼ばないため、月間リクエスト上限（BL-141）を消費しない。
 * 経路の違いは利用者からは見えず、診断ログにのみ残る。
 */
class SesameDeviceCommandExecutor(
    private val loadCredentials: () -> List<SesameCredentials>,
    private val lockStateStore: LockStateStore,
    private val notifier: LockStateNotifier,
    private val routes: SesameRouteAccess = SesameRouteAccess(),
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
        if (!debouncer.shouldProcess(COMMAND_KEY_PREFIX + uuid)) return Outcome.DEBOUNCED
        val succeeded = SesameDemoMode.isDemoDevice(uuid) || sendCommand(uuid, command)
        if (succeeded) {
            // 送信したコマンドが意図した状態をそのまま保存する（BL-015の簡略化ロジックを維持）。
            updateLockState(uuid, isLocked = command == SesameCommand.LOCK)
        }
        return if (succeeded) Outcome.SUCCESS else Outcome.FAILURE
    }

    /**
     * BLEで到達できるデバイスはBLEで実行し、それ以外はSesame Web APIで実行する（BL-152）。
     * BLEで成功した場合はWeb APIを呼ばないため、月間リクエスト上限（BL-141）を消費しない。
     */
    private suspend fun sendCommand(
        uuid: String,
        command: SesameCommand,
    ): Boolean {
        val credentials = findCredentials(uuid)
        val secretKey = credentials?.secretKeyBytesOrNull ?: return false
        return routes.ble.tryCommand(credentials, command, nowMillis()) ||
            routes.ble.withReachabilityProbe(credentials, nowMillis()) {
                sendCommandOverApi(credentials, secretKey, command)
            }
    }

    private suspend fun sendCommandOverApi(
        credentials: SesameCredentials,
        secretKey: ByteArray,
        command: SesameCommand,
    ): Boolean {
        // 失敗の分類はコールバックで受け取る（コールバックはsuspendでないためその場では保存できない）。
        var failure: SesameStatusFailure? = null
        routes.api.recordApiCall()
        val handler =
            SesameCommandHandler(
                apiClient = routes.api.clientFactory(credentials),
                secretKey = secretKey,
                onFailure = { e ->
                    routes.api.logFailure(SesameApiFailureLog.describe(operationOf(command), e))
                    failure = SesameStatusFailure.of(e.httpStatusCode)
                },
            )
        val succeeded = handler.execute(command) == SesameCommandResult.SUCCESS
        failure?.let { recordFailure(credentials.uuid, it) }
        return succeeded
    }

    /**
     * [uuid]のデバイスの状態をSesame APIのGETで取得し、保存と通知を行う。
     * 取得できた場合はその施錠状態、資格情報が無い・APIエラーの場合はnullを返す（保存・通知はしない）。
     *
     * 同一uuidへの連打は[debouncer]で抑止し、抑止した場合はAPIを呼ばずに保存済みの状態を返す
     * （BL-148）。BL-142で自動状態取得を廃止し、Sesame Web APIの消費が利用者のタップ回数と
     * 等しくなったため、誤タップ・二度押しがそのまま月間リクエスト上限（BL-141）へ効く。
     * 施錠/解錠とは別のキーで数えるため、施錠/解錠の直後でも状態取得は抑止されない（移設前と同じ）。
     * 「全デバイス」対象のタップで登録台数ぶん飛ぶのは意図した動作のため対象外（uuidが異なる）。
     */
    suspend fun refreshStatus(uuid: String): Boolean? {
        if (SesameDemoMode.isDemoDevice(uuid)) {
            // デモは取得先が無いため、保存済み（無ければ初期状態）をそのまま返し、保存・通知もしない。
            return lockStateStore.load(uuid)?.isLocked ?: SesameDemoMode.INITIAL_IS_LOCKED
        }
        // 連打として無視した場合はAPIを呼ばず保存済みの状態を返す。失敗ではないため、
        // 失敗の記録（BL-140）も残さない。
        return if (debouncer.shouldProcess(STATUS_KEY_PREFIX + uuid)) {
            fetchIsLocked(uuid)?.also { updateLockState(uuid, it) }
        } else {
            lockStateStore.load(uuid)?.isLocked
        }
    }

    private suspend fun fetchIsLocked(uuid: String): Boolean? {
        val credentials = findCredentials(uuid) ?: return null
        // BLEで取得できた場合はWeb APIを呼ばない（上限を消費しない、BL-152）。
        return routes.ble.tryStatus(credentials, nowMillis())
            ?: routes.ble.withReachabilityProbe(credentials, nowMillis()) { fetchIsLockedOverApi(credentials) }
    }

    private suspend fun fetchIsLockedOverApi(credentials: SesameCredentials): Boolean? {
        routes.api.recordApiCall()
        return try {
            routes.api.clientFactory(credentials).getStatus().isInLockRange
        } catch (e: SesameApiException) {
            // 呼び出し元（ウォッチ・ウィジェット）へは「取得できなかった」ことだけを伝える。
            // 切り分けに必要な情報はlogcatへ残し（BL-139）、失敗の分類は表示のため保存する（BL-140）。
            routes.api.logFailure(SesameApiFailureLog.describe(SesameApiOperation.STATUS, e))
            recordFailure(credentials.uuid, SesameStatusFailure.of(e.httpStatusCode))
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

        /**
         * 連打判定のキーの接頭辞（BL-148）。施錠/解錠と状態取得を別々に数えるために分ける。
         * 分けないと、施錠した直後に状態を取り直せなくなる（BL-061の巻き戻り防止と衝突する）。
         */
        private const val COMMAND_KEY_PREFIX = "cmd:"
        private const val STATUS_KEY_PREFIX = "status:"
    }
}

/**
 * 施錠/解錠・状態取得に使える経路の一式（BL-152）。
 *
 * [api]はSesame Web API、[ble]はBLE直接操作。既定値では[ble]が常に「使えない」を返すため、
 * 配線していない呼び出し元（ユニットテストや、BLEを使わない構成）では従来どおりWeb APIだけが動く。
 * 2つをまとめて1つの引数にしているのは、[SesameDeviceCommandExecutor]の引数の数を抑えるためでもある。
 */
class SesameRouteAccess(
    val api: SesameApiAccess = SesameApiAccess(),
    val ble: SesameBleAccess = SesameBleAccess(),
)

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

/**
 * 診断ログ（BL-139）で使う操作種別へ変換する。
 * [SesameDeviceCommandExecutor]の関数数を増やさないようトップレベルへ置く。
 */
private fun operationOf(command: SesameCommand): SesameApiOperation =
    when (command) {
        SesameCommand.LOCK -> SesameApiOperation.LOCK
        SesameCommand.UNLOCK -> SesameApiOperation.UNLOCK
    }

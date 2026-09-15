package com.sesamiwear.mobile.command

import com.sesamiwear.core.SesameCommandResult
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.api.SesameApiClient
import com.sesamiwear.core.api.SesameApiException
import com.sesamiwear.core.api.SesameCommand
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
 */
class SesameDeviceCommandExecutor(
    private val loadCredentials: () -> List<SesameCredentials>,
    private val lockStateStore: LockStateStore,
    private val notifier: LockStateNotifier,
    private val debouncer: CommandDebouncer = sharedDebouncer,
    private val apiClientFactory: (SesameCredentials) -> SesameApiClient = ::defaultApiClient,
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
        val handler = SesameCommandHandler(apiClientFactory(credentials), secretKey)
        return handler.execute(command) == SesameCommandResult.SUCCESS
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
        return try {
            apiClientFactory(credentials).getStatus().isInLockRange
        } catch (
            @Suppress("SwallowedException") e: SesameApiException,
        ) {
            // 呼び出し元（ウォッチ・ウィジェット）へは「取得できなかった」ことだけを伝える。
            null
        }
    }

    private fun findCredentials(uuid: String): SesameCredentials? = loadCredentials().find { it.uuid == uuid }

    private suspend fun updateLockState(
        uuid: String,
        isLocked: Boolean,
    ) {
        lockStateStore.save(uuid, isLocked, nowMillis())
        if (!SesameDemoMode.isDemoDevice(uuid)) notifier.watch.onLockStateChanged(uuid, isLocked)
        notifier.local.onLockStateChanged(uuid, isLocked)
    }

    companion object {
        /**
         * プロセス内で共有する[CommandDebouncer]。Serviceインスタンスをまたいで連打を検知でき、
         * ウォッチ経由とウィジェット経由の重複も1つにまとめる（BL-062 / BL-120）。
         */
        val sharedDebouncer = CommandDebouncer()

        private fun defaultApiClient(credentials: SesameCredentials): SesameApiClient =
            SesameApiClient(uuid = credentials.uuid, apiKey = credentials.apiKey)
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
    suspend fun onLockStateChanged(
        uuid: String,
        isLocked: Boolean,
    )
}

package com.sesamiwear.mobile.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.ble.SesameBleProtocol.ItemCode
import com.sesamiwear.mobile.ble.SesameBleProtocol.ResultCode
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 接続の保持（BL-279）の前提を測るための、**デバッグビルドにだけ存在する**隠し入口（BL-282、BL-278の計測用）。
 *
 * adbから、対象のSesameへ接続・ログインして**そのまま保持**し、切断したときの保持時間をログへ出す。
 * Sesameがアイドルで切断するまでの時間と、保持中の他の接続（公式アプリ・Hub 3）への影響を測るため。
 * **何も送らない**（施錠・解錠はもちろん、維持用の通信も送らない。本アプリが実装しているコマンドに、
 * 安全に送れる維持用のものが無いため）。受け取る通知（MECH_STATUSなど）の到着時刻だけを残す。
 *
 * 本ファイルは`src/debug`にあるためリリースビルドへは含まれない（マニフェストの宣言も同様）。
 *
 * ```
 * adb shell am broadcast -a com.sesamiwear.mobile.debug.BLE_HOLD \
 *   -n com.sesamiwear.mobile.debug/com.sesamiwear.mobile.ble.SesameBleHoldDebugReceiver \
 *   --es uuid <uuid> --es op start [--ei max_sec 1800]
 * adb shell am broadcast ... --es op stop
 * ```
 *
 * 結果は`adb logcat -s SesameBleHold`で読む（`HOLD login ...` / `HOLD message ...` / `HOLD closed after=...s reason=...`）。
 * 資格情報・セッション鍵・BLEアドレス・uuidはログへ出さない（rules/guardrails-unified.v1.md 3.3 / 12.5）。
 * 同時に保持できるのは1本だけで、新しい開始は前の保持を止めて置き換える。
 */
class SesameBleHoldDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getStringExtra(EXTRA_OPERATION) ?: OPERATION_START) {
            OPERATION_STOP -> SesameBleHolder.stop()
            OPERATION_START -> {
                val uuid = intent.getStringExtra(EXTRA_UUID)
                if (uuid.isNullOrBlank()) {
                    Log.w(TAG, "uuid extra is required")
                    return
                }
                val maxSeconds = intent.getIntExtra(EXTRA_MAX_SECONDS, DEFAULT_MAX_SECONDS).toLong()
                SesameBleHolder.start(context.applicationContext, uuid, maxSeconds)
            }
            else -> Log.w(TAG, "unknown op (expected start/stop)")
        }
    }

    private companion object {
        const val TAG = "SesameBleHold"
        const val EXTRA_UUID = "uuid"
        const val EXTRA_OPERATION = "op"
        const val EXTRA_MAX_SECONDS = "max_sec"
        const val OPERATION_START = "start"
        const val OPERATION_STOP = "stop"
        const val DEFAULT_MAX_SECONDS = 1_800
    }
}

/** 保持の実体。プロセス単位のスコープで動かす（受信口のインスタンスは短命なため）。 */
internal object SesameBleHolder {
    private const val TAG = "SesameBleHold"
    private const val MILLIS_PER_SECOND = 1_000L
    private const val LOGIN_TIMEOUT_MILLIS = 5_000L
    private const val SCAN_MILLIS = 6_000L
    private const val CONNECT_MILLIS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var connection: SesameBleConnection? = null

    /** 利用者が止めたか。切断の理由を「止めた」と「Sesame側から切れた」で区別するために使う。 */
    @Volatile
    private var stopRequested = false

    @Synchronized
    fun start(
        context: Context,
        uuid: String,
        maxSeconds: Long,
    ) {
        stop()
        stopRequested = false
        job = scope.launch { hold(context, uuid, maxSeconds) }
    }

    @Synchronized
    fun stop() {
        stopRequested = true
        job?.cancel()
        connection?.close()
        job = null
        connection = null
    }

    private suspend fun hold(
        context: Context,
        uuid: String,
        maxSeconds: Long,
    ) {
        val credentials =
            SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(context))
                .loadAll().find { it.uuid == uuid }
        if (credentials == null) {
            Log.w(TAG, "no credentials registered for the requested device")
            return
        }
        val opened = open(context, credentials)
        if (opened == null) {
            Log.w(TAG, "HOLD connect failed")
            return
        }
        synchronized(this) { connection = opened }
        val startedAt = SystemClock.elapsedRealtime()
        val listened = withTimeoutOrNull(maxSeconds * MILLIS_PER_SECOND) { listen(opened, startedAt) } ?: "max_sec"
        val reason = if (stopRequested) "stopped" else listened
        opened.close()
        Log.w(
            TAG,
            "HOLD closed after=${(SystemClock.elapsedRealtime() - startedAt) / MILLIS_PER_SECOND}s reason=$reason",
        )
    }

    /** 接続してログインする。成功すれば開いたままの接続を返す。 */
    private suspend fun open(
        context: Context,
        credentials: SesameCredentials,
    ): SesameBleConnection? {
        val secretKey = credentials.secretKeyBytesOrNull ?: return null
        val cache = SesameBleAddressCache(SharedPreferencesKeyValueStore.forBleReachability(context))
        val attempt =
            SesameBleConnector(
                context,
                SesameBleScanner(context),
                cache,
            ).connect(credentials.uuid, SCAN_MILLIS, CONNECT_MILLIS)
        val connection = attempt.connection ?: return null
        val token =
            SesameBleMessageReader.awaitPublish(connection, ItemCode.INITIAL, LOGIN_TIMEOUT_MILLIS)
                ?.takeIf { it.size == SesameBleProtocol.SESSION_TOKEN_LENGTH }
        val session = token?.let { SesameBleSession(secretKey, it) }
        val loginCommand = session?.let { SesameBleMessage.encodeCommand(ItemCode.LOGIN, it.loginPayload) }
        val sent = loginCommand != null && connection.write(loginCommand, false)
        val response =
            if (sent) {
                SesameBleMessageReader.awaitResponse(
                    connection,
                    ItemCode.LOGIN,
                    LOGIN_TIMEOUT_MILLIS,
                    session!!,
                )
            } else {
                null
            }
        if (response?.resultCode != ResultCode.SUCCESS) {
            connection.close()
            return null
        }
        Log.w(TAG, "HOLD login ok")
        return connection
    }

    /** 接続が切れるまで待つ。届いた通知の時刻だけをログへ出し、切れたら理由を返す。 */
    private suspend fun listen(
        connection: SesameBleConnection,
        startedAt: Long,
    ): String {
        var count = 0
        while (true) {
            val received = connection.incoming.receiveCatching()
            if (received.isClosed) return "remote_closed"
            count++
            Log.w(TAG, "HOLD message n=$count at=${(SystemClock.elapsedRealtime() - startedAt) / MILLIS_PER_SECOND}s")
        }
    }
}

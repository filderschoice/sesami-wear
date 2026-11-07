package com.sesamiwear.mobile.ble

import com.sesamiwear.mobile.ble.SesameBleProtocol.ItemCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ログインまで済ませて保持している接続（BL-279）。次の施錠・解錠・状態取得で、探索・接続・ログインを省いて
 * 再利用する。保持の寿命は[SesameBleHeldSessions]が持つ。
 *
 * **保持している間も、届く通知を読んで復号し続ける**（[startDraining]）。通知は暗号カウンタを進めながら届くため、
 * 読まずに溜めると、受信バッファ（16件）があふれて取りこぼした時点でカウンタがずれ、以降の復号がすべて失敗する。
 * 読み捨てながら、最新の機構状態（`MECH_STATUS`）だけを覚える（状態取得で再利用するため）。
 * 使う直前に[run]が読み取りを止める（読み取りと使用が同じ通知を取り合わない）。
 *
 * 秘密情報（鍵・セッション鍵）はログへ出さない。
 */
internal class SesameBleHeldSession(
    private val connection: SesameBleConnection,
    private val session: SesameBleSession,
    initialStatus: SesameBleMechStatus?,
) : AutoCloseable {
    @Volatile
    var latestStatus: SesameBleMechStatus? = initialStatus
        private set

    /** 読み取りの最中に、相手側から切られたか。 */
    @Volatile
    private var remoteClosed = false

    private var drain: Job? = null

    /** 届く通知を読んで復号し、最新の機構状態を覚える。接続が切れたら[remoteClosed]を立てて終わる。 */
    fun startDraining(scope: CoroutineScope) {
        drain =
            scope.launch {
                for (message in connection.incoming) {
                    val body = if (message.isEncrypted) session.decrypt(message.payload) else message.payload
                    val publish = (body?.let(SesameBleMessage::parse) as? SesameBleMessage.Parsed.AsPublish)?.publish
                    if (publish?.itemCode == ItemCode.MECH_STATUS) {
                        SesameBleMechStatus.parse(publish.payload)?.let { latestStatus = it }
                    }
                }
                remoteClosed = true
            }
    }

    /**
     * 読み取りを止めて[block]を実行する。保持が使えなければ（相手側から切られていた）[SesameBleClient.Result.CONNECTION_FAILED]を
     * 返し、呼び出し側が通常の接続へ戻れるようにする。[block]がnullを返せば[SesameBleClient.Result.COMMAND_REJECTED]。
     * 戻り値の2つめは最新の機構状態。
     */
    suspend fun run(
        block: suspend (SesameBleConnection, SesameBleSession, SesameBleMechStatus?) -> SesameBleClient.Result?,
    ): Pair<SesameBleClient.Result, SesameBleMechStatus?> {
        drain?.cancelAndJoin()
        drain = null
        if (remoteClosed) return SesameBleClient.Result.CONNECTION_FAILED to null
        val status = latestStatus
        return (block(connection, session, status) ?: SesameBleClient.Result.COMMAND_REJECTED) to status
    }

    override fun close() {
        drain?.cancel()
        connection.close()
    }
}

/**
 * 保持している接続の置き場（BL-279、プロセス単位）。「Bluetooth接続を確認」に成功したあと[TTL_MILLIS]だけ保持し、
 * その間の施錠・解錠・状態取得は[SesameBleClient]が取り出して再利用する。使うたびに時間切れを数え直し、
 * 失敗した接続は残さない。保持中のSesameの接続は他のクライアント（公式アプリ・Hub 3）を妨げにくいことを
 * 実機で確認している（BL-278）。
 */
internal object SesameBleHeldSessions {
    /** 保持の寿命（ミリ秒）。最後に使ってからこの時間で閉じる。 */
    const val TTL_MILLIS = 180_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = HeldSessionStore<SesameBleHeldSession>(scope, TTL_MILLIS)

    /** [deviceUuid]の接続を保持する（読み取りを始め、時間切れを数える）。 */
    fun retain(
        deviceUuid: String,
        session: SesameBleHeldSession,
    ) {
        session.startDraining(scope)
        store.retain(key(deviceUuid), session)
    }

    /** [deviceUuid]の保持を取り出す。無ければnull。使い終わって成功したら[retain]で戻し、失敗したら閉じる。 */
    fun take(deviceUuid: String): SesameBleHeldSession? = store.take(key(deviceUuid))

    /** 保持している接続をすべて閉じる。 */
    fun releaseAll() = store.releaseAll()

    private fun key(deviceUuid: String): String = deviceUuid.lowercase(Locale.ROOT)
}

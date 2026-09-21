package com.sesamiwear.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.sesamiwear.core.api.SesameApiFailureLog
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sesameへの接続を確立する手順（BL-189）。
 *
 * **保存済みのBLEアドレスへ直接つなぐことを先に試し、駄目なときだけ探索する。**
 * 2026-09-20の実機検証（BL-165）で、BLE1往復の実測2.0〜3.5秒のうち支配的なのが探索（スキャン）で、
 * 経路選択に与えていた上限（合計1,800ms）では毎回打ち切られていたことが判明したための構成。
 * 探索そのものは、利用者を待たせないところ（Web APIの通信と並行して走る到達確認、
 * [SesameBleClient.probeReachable]）で済ませ、アドレスを[SesameBleAddressCache]へ残す。
 *
 * 直接接続に失敗した保存値は、古い（アドレスが変わった）か圏外かのどちらかなので捨てる。
 * [SesameBleClient]から切り出しているのは、1クラスあたりの関数数を抑えるためでもある。
 * Android依存のためユニットテスト対象外。
 */
@SuppressLint("MissingPermission")
internal class SesameBleConnector(
    private val context: Context,
    private val scanner: SesameBleScanner,
    private val addressCache: SesameBleAddressCache?,
) {
    /**
     * [uuid]のデバイスへ接続する。
     *
     * @param scanMillis 探索に与える上限。保存済みアドレスが無い・使えないときだけ使う。
     * @param connectMillis 1回の接続に与える上限。
     */
    suspend fun connect(
        uuid: String,
        scanMillis: Long,
        connectMillis: Long,
    ): Attempt {
        val cached =
            addressCache?.load(uuid)?.let { address ->
                scanner.remoteDevice(address)?.let { open(it, connectMillis) }
            }
        if (cached != null) return Attempt(cached, null)
        // 保存値が使えなかった（または無かった）。次の到達確認で取り直させる。
        addressCache?.remove(uuid)
        val device = scanner.findDevice(uuid, scanMillis)
        val connection = device?.let { open(it, connectMillis) }
        if (connection != null) addressCache?.save(uuid, device.address)
        return Attempt(
            connection = connection,
            failure =
                when {
                    connection != null -> null
                    device == null -> SesameBleClient.Result.NOT_FOUND
                    else -> SesameBleClient.Result.CONNECTION_FAILED
                },
        )
    }

    /**
     * [uuid]のデバイスが圏内にいるかだけを確かめる（BL-152 / BL-193）。接続もログインもする前の
     * 判断材料にするだけなので、つながった接続はすぐ閉じる。Web APIの通信と並行して呼ばれる。
     *
     * **探索が外れたときは、最後に成功したアドレス（[SesameBleAddressCache.loadLastKnown]）へ
     * 直接つないで確かめる。** 2026-09-21のBL-191の実機検証で、同じ端末・同じ位置で直接接続は
     * 成功するのに探索は3回とも当たらなかったため、探索だけに頼ると保存済みアドレスを一度失った
     * 時点でBLE経路へ復帰できなくなっていた。
     *
     * 予備のアドレスがある場合は、その接続ぶん（[connectMillis]）だけ探索を短くし、
     * **到達確認全体に掛かる時間（[probeMillis]）は変えない**。並行して待つWeb APIの上限（6秒）を
     * 超えると、利用者から見た所要時間がそのぶん伸びるため。
     *
     * @param probeMillis 到達確認全体に与える上限。
     * @param connectMillis 予備のアドレスへの直接接続に与える上限。
     */
    suspend fun probeReachable(
        uuid: String,
        probeMillis: Long,
        connectMillis: Long,
    ): Boolean {
        val lastKnown = addressCache?.loadLastKnown(uuid)
        val scanMillis = if (lastKnown == null) probeMillis else (probeMillis - connectMillis).coerceAtLeast(0)
        // 探索で見つかればそのアドレス、外れたら予備のアドレスへ直接つないで確かめる。
        // つながった以上は圏内なので、次の操作で探索を飛ばせるよう現用の記録として残す。
        val scanned = scanner.findDevice(uuid, scanMillis)?.address
        val reachedAddress = scanned ?: lastKnown?.takeIf { connects(it, connectMillis) }
        // 探索で当たったのか予備のアドレスで拾えたのかは、保存値の差分からは区別できない
        // （どちらも[SesameBleAddressCache.save]で同じ記録になる）。実機検証でBL-193の効果を
        // 切り分けられるよう、到達をどちらの手段で確かめたかだけを1行残す（アドレスは出さない）。
        Log.w(
            SesameApiFailureLog.TAG,
            "route=BLE op=PROBE detail=" +
                when {
                    scanned != null -> "SCAN"
                    reachedAddress != null -> "LAST_KNOWN"
                    else -> "MISS"
                },
        )
        reachedAddress?.let { addressCache?.save(uuid, it) }
        return reachedAddress != null
    }

    /** [address]へ直接つないで、圏内にいることだけを確かめる。つないだ接続はすぐ閉じる。 */
    private suspend fun connects(
        address: String,
        connectMillis: Long,
    ): Boolean {
        val connection = scanner.remoteDevice(address)?.let { open(it, connectMillis) } ?: return false
        connection.close()
        return true
    }

    private suspend fun open(
        device: BluetoothDevice,
        connectMillis: Long,
    ): SesameBleConnection? = withTimeoutOrNull(connectMillis) { SesameBleConnection.open(context, device) }

    /** 接続の試行結果。[connection]がnullのときだけ[failure]が入る。 */
    data class Attempt(
        val connection: SesameBleConnection?,
        val failure: SesameBleClient.Result?,
    )
}

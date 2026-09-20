package com.sesamiwear.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
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

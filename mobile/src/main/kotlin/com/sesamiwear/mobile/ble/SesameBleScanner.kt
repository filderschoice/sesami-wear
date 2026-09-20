package com.sesamiwear.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * 目的のuuidを持つSesameデバイスをBLEスキャンで探す（BL-151）。
 *
 * サービスUUIDでフィルタしたうえで、製造者データ（[SesameBleAdvertisement]）のuuidが
 * 資格情報のuuidと一致するものを1台見つけた時点でスキャンを止める。
 * 見つけたアドレスは[SesameBleAddressCache]が覚え、次回は[remoteDevice]で探索を飛ばす（BL-189）。
 * アドレスが変わっていれば直接接続が失敗するため、そのときはこのスキャンで解決し直す。
 *
 * スキャンは電力を使い、BL-152の経路選択では実行時間の上限（合計2秒程度）が厳しいため、
 * [timeoutMillis]は呼び出し側が用途に応じて短く与える。
 * Android依存のためユニットテスト対象外。
 */
@SuppressLint("MissingPermission")
class SesameBleScanner(
    private val context: Context,
) {
    /**
     * [deviceUuid]のデバイスを最大[timeoutMillis]ミリ秒探す。
     * 見つからない、Bluetoothが無効、スキャナを取得できない場合はnullを返す。
     */
    suspend fun findDevice(
        deviceUuid: String,
        timeoutMillis: Long,
    ): BluetoothDevice? {
        val scanner = bluetoothLeScanner() ?: return null
        val found = CompletableDeferred<BluetoothDevice>()
        val target = deviceUuid.lowercase(Locale.ROOT)
        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    if (matches(result, target)) found.complete(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    found.completeExceptionally(IllegalStateException("BLE scan failed: $errorCode"))
                }
            }

        scanner.startScan(listOf(serviceFilter()), lowLatencySettings(), callback)
        return try {
            withTimeoutOrNull(timeoutMillis) { found.await() }
        } catch (
            @Suppress("SwallowedException") e: IllegalStateException,
        ) {
            // スキャン開始自体が拒否された場合（アプリごとの回数制限など）。経路の選択に使うだけで、
            // 失敗すればWeb APIへ倒れるため、ここでは見つからなかったものとして扱う。
            null
        } finally {
            scanner.stopScan(callback)
        }
    }

    /**
     * [address]のデバイスを、探索せずに直接指す（BL-189）。
     *
     * 実際に圏内にいるかどうかは接続を試みるまで分からない。呼び出し側は接続に上限を与え、
     * 失敗したら保存済みアドレスを捨てて探索からやり直す（[SesameBleAddressCache]）。
     * Bluetoothが無効な場合とアドレスの書式が不正な場合はnullを返す。
     */
    fun remoteDevice(address: String): BluetoothDevice? {
        val adapter = adapter()?.takeIf { BluetoothAdapter.checkBluetoothAddress(address) } ?: return null
        // アドレス種別を指定できるのはAPI 33以上。それ未満では探索へ倒す（誤った種別で繋ぐより確実）。
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            adapter.getRemoteLeDevice(address, addressTypeOf(address))
        } else {
            null
        }
    }

    /**
     * アドレス種別の判定。**Sesame 5はstatic random address（例 `E9:...`）を使う**ため、
     * `getRemoteDevice`（公開アドレス扱い）では接続できない（2026-09-20の実機検証で判明。
     * 保存済みアドレスへの直接接続が毎回失敗していた）。
     *
     * 先頭オクテットの上位2ビットがrandom address の種別を表す（11＝static、01＝resolvable、
     * 00＝non-resolvable）。11なら random として扱い、それ以外は公開アドレスとみなす。
     * resolvableなアドレスは接続のたびに変わるため、保存しても直接接続は失敗し探索へ戻るだけで、
     * 実害は無い。
     */
    private fun addressTypeOf(address: String): Int {
        val firstOctet = address.substringBefore(':').toIntOrNull(HEX_RADIX) ?: 0
        return if (firstOctet and RANDOM_ADDRESS_MASK == RANDOM_ADDRESS_MASK) {
            BluetoothDevice.ADDRESS_TYPE_RANDOM
        } else {
            BluetoothDevice.ADDRESS_TYPE_PUBLIC
        }
    }

    /** Bluetoothが有効なときだけスキャナを返す。無効・非対応の端末ではnullになる。 */
    private fun bluetoothLeScanner(): BluetoothLeScanner? = adapter()?.bluetoothLeScanner

    /** Bluetoothが有効なときだけアダプタを返す。 */
    private fun adapter(): BluetoothAdapter? =
        context
            .getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.takeIf { it.isEnabled }

    private fun matches(
        result: ScanResult,
        targetUuid: String,
    ): Boolean {
        val advertisement =
            result.scanRecord
                ?.getManufacturerSpecificData(SesameBleProtocol.COMPANY_ID)
                ?.let(SesameBleAdvertisement::parse)
        return advertisement != null &&
            advertisement.isSesame5Family &&
            advertisement.isRegistered &&
            advertisement.deviceUuid == targetUuid
    }

    private fun serviceFilter(): ScanFilter =
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(SesameBleProtocol.SERVICE_UUID))
            .build()

    private fun lowLatencySettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

    private companion object {
        const val HEX_RADIX = 16

        /** static random address を表す、先頭オクテットの上位2ビット。 */
        const val RANDOM_ADDRESS_MASK = 0xC0
    }
}

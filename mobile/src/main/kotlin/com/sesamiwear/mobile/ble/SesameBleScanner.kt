package com.sesamiwear.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * 目的のuuidを持つSesameデバイスをBLEスキャンで探す（BL-151）。
 *
 * サービスUUIDでフィルタしたうえで、製造者データ（[SesameBleAdvertisement]）のuuidが
 * 資格情報のuuidと一致するものを1台見つけた時点でスキャンを止める。
 * BLEアドレスは端末・起動ごとに変わりうるため保存せず、毎回このスキャンで解決する。
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

    /** Bluetoothが有効なときだけスキャナを返す。無効・非対応の端末ではnullになる。 */
    private fun bluetoothLeScanner(): BluetoothLeScanner? =
        context
            .getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.takeIf { it.isEnabled }
            ?.bluetoothLeScanner

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
}

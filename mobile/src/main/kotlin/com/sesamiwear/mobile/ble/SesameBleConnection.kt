package com.sesamiwear.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.util.UUID

/**
 * 1台のSesameデバイスとのGATT接続（BL-151）。
 *
 * 接続・サービス探索・通知の有効化までを[open]で済ませ、以降は[write]で送信、[incoming]で受信する。
 * Android依存のためユニットテスト対象外で、プロトコルの解釈は`mobile.ble`の純Kotlinクラスが担う
 * （DESIGN.md「実装制約 > 技術制約」の方針に合わせ、テストできる部分を最大化する）。
 *
 * 権限（API 31以上は`BLUETOOTH_CONNECT`、30以下は`ACCESS_FINE_LOCATION`）の確認は
 * 呼び出し側（[SesameBlePermissions]）の責任で、ここでは確認済みである前提で扱う。
 */
@SuppressLint("MissingPermission")
class SesameBleConnection private constructor(
    private val assembler: SesameBlePacketAssembler,
) {
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val connected = CompletableDeferred<Boolean>()
    private val ready = CompletableDeferred<Boolean>()
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    /**
     * 組み上がった受信メッセージ。容量を持つバッファ付きチャネルで、こちらが待つ前に届いた通知
     * （ログイン前の`INITIAL`など）も取りこぼさない。
     */
    val incoming: Channel<SesameBlePacketAssembler.Message> = Channel(INCOMING_BUFFER_SIZE)

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> failAll()
                    newState == BluetoothProfile.STATE_CONNECTED -> {
                        if (!gatt.discoverServices()) failAll()
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED -> failAll()
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failAll()
                    return
                }
                connected.complete(true)
                if (!enableNotification(gatt)) failAll()
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                ready.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Deprecated("API 33未満向けのオーバーロード。33以上では値付きの方が呼ばれる。")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                dispatch(characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                dispatch(value)
            }
        }

    private fun dispatch(value: ByteArray?) {
        val packet = value ?: return
        assembler.append(packet)?.let { incoming.trySend(it) }
    }

    private fun failAll() {
        connected.complete(false)
        ready.complete(false)
        pendingWrite?.complete(false)
        incoming.close()
    }

    private fun enableNotification(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(UUID.fromString(SesameBleProtocol.SERVICE_UUID))
        writeCharacteristic = service?.getCharacteristic(UUID.fromString(SesameBleProtocol.WRITE_CHARACTERISTIC_UUID))
        val notifyCharacteristic =
            service?.getCharacteristic(UUID.fromString(SesameBleProtocol.NOTIFY_CHARACTERISTIC_UUID))
        val descriptor =
            notifyCharacteristic
                ?.takeIf { gatt.setCharacteristicNotification(it, true) }
                ?.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
        return writeCharacteristic != null && descriptor != null && writeDescriptor(gatt, descriptor)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
    ): Boolean {
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }
    }

    /**
     * [data]を分割して送信する。分割した各パケットの書き込み完了を待ってから次を送る
     * （Write Without Responseでも完了通知は届くため、送信キューの溢れを避けられる）。
     * いずれかの書き込みが失敗した時点でfalseを返す。
     */
    suspend fun write(
        data: ByteArray,
        isEncrypted: Boolean,
    ): Boolean {
        val gatt = this.gatt
        val characteristic = writeCharacteristic
        if (gatt == null || characteristic == null) return false
        var succeeded = true
        for (packet in SesameBlePacketCodec.split(data, isEncrypted)) {
            val completion = CompletableDeferred<Boolean>()
            pendingWrite = completion
            succeeded = writePacket(gatt, characteristic, packet) && completion.await()
            if (!succeeded) break
        }
        return succeeded
    }

    @Suppress("DEPRECATION")
    private fun writePacket(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        packet: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                packet,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = packet
            gatt.writeCharacteristic(characteristic)
        }

    /** 接続を閉じる。二重に呼んでも安全。 */
    fun close() {
        incoming.close()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        writeCharacteristic = null
        assembler.reset()
    }

    companion object {
        private val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * 受信チャネルのバッファ数。ログイン直後に`MECH_STATUS`と`MECH_SETTING`が続けて届くなど、
         * こちらが読む前に数件たまることがあるため余裕を持たせる。
         */
        private const val INCOMING_BUFFER_SIZE = 16

        /**
         * [device]へ接続し、サービス探索と通知の有効化まで終えた接続を返す。
         * 途中で失敗した場合はnullを返し、GATTリソースを閉じる。
         * 呼び出し側は全体をタイムアウトで囲むこと（`withTimeoutOrNull`）。
         */
        suspend fun open(
            context: Context,
            device: BluetoothDevice,
        ): SesameBleConnection? {
            val connection = SesameBleConnection(SesameBlePacketAssembler())
            connection.gatt =
                device.connectGatt(
                    context.applicationContext,
                    false,
                    connection.callback,
                    BluetoothDevice.TRANSPORT_LE,
                )
            val established =
                connection.gatt != null && connection.connected.await() && connection.ready.await()
            if (!established) connection.close()
            return connection.takeIf { established }
        }
    }
}

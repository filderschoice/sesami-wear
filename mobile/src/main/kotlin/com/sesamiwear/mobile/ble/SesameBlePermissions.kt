package com.sesamiwear.mobile.ble

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * BLE直接操作に必要な実行時権限の判定（BL-151 / BL-153）。
 *
 * 必要な権限はAndroidのバージョンで変わる。API 31以上は`BLUETOOTH_SCAN`（近くの機器の検出のみに
 * 使うことを`neverForLocation`で宣言する）と`BLUETOOTH_CONNECT`だけで足り、位置情報は不要。
 * API 30以下はBLEスキャンに`ACCESS_FINE_LOCATION`が必要なため、旧端末限定で要求する
 * （マニフェスト側は`android:maxSdkVersion="30"`で宣言する。DESIGN.md「BLE直接操作の併用方針」論点4）。
 *
 * 判定ロジックはAndroid非依存の[requiredPermissions]へ切り出してユニットテストで検証し、
 * 実際の許可状況の問い合わせだけがAndroid依存になる。
 *
 * **権限が無くてもアプリは従来どおりWeb API経由で動く。** BLEは到達できるときの追加経路にすぎず、
 * 権限を必須にしてはならない（BL-153の完了条件）。
 */
object SesameBlePermissions {
    /**
     * API 31（Android 12）以上で使う権限。
     *
     * 定数は文字列のためコンパイル時にインライン化され、API 30以下の端末でも値としては安全に扱える
     * （[requiredPermissions]がAPI 31以上でしか返さないため、古い端末で要求されることもない）。
     */
    @SuppressLint("InlinedApi")
    val MODERN_PERMISSIONS: List<String> =
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    /** API 30（Android 11）以下で使う権限。 */
    val LEGACY_PERMISSIONS: List<String> = listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * [sdkInt]のAndroidでBLE直接操作に必要な権限の一覧。
     * Android非依存（引数のバージョン番号だけで決まる）のため、ユニットテストで検証する。
     */
    fun requiredPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) MODERN_PERMISSIONS else LEGACY_PERMISSIONS

    /** 実行中の端末で必要な権限がすべて許可されているか。 */
    fun hasAll(context: Context): Boolean =
        requiredPermissions(Build.VERSION.SDK_INT).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** 実行中の端末でまだ許可されていない権限。空なら要求は不要。 */
    fun missing(context: Context): List<String> =
        requiredPermissions(Build.VERSION.SDK_INT).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
}

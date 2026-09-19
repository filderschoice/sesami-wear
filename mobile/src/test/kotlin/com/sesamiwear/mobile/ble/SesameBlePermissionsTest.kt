package com.sesamiwear.mobile.ble

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLE直接操作に必要な権限の判定の検証（BL-151 / BL-153）。
 *
 * 判定はAndroidのバージョン番号だけで決まるため、実機もモックも使わずに検証できる。
 * 位置情報権限がAPI 31以上で要求されないこと（DESIGN.md「BLE直接操作の併用方針」論点4）を、
 * データセーフティ申告（BL-154）の前提としてここで固定する。
 */
class SesameBlePermissionsTest {
    @Test
    fun `android 12 and later need only the bluetooth permissions`() {
        val permissions = SesameBlePermissions.requiredPermissions(Build.VERSION_CODES.S)
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            permissions,
        )
        assertFalse(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun `newer android versions keep using the bluetooth permissions`() {
        assertEquals(
            SesameBlePermissions.MODERN_PERMISSIONS,
            SesameBlePermissions.requiredPermissions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
    }

    @Test
    fun `android 11 and earlier need fine location for scanning`() {
        val permissions = SesameBlePermissions.requiredPermissions(Build.VERSION_CODES.R)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), permissions)
    }

    @Test
    fun `the minimum supported sdk also uses the legacy permission`() {
        // 本アプリのminSdkは26（Android 8.0）。
        assertEquals(
            SesameBlePermissions.LEGACY_PERMISSIONS,
            SesameBlePermissions.requiredPermissions(Build.VERSION_CODES.O),
        )
    }

    @Test
    fun `the two permission sets never overlap`() {
        assertTrue(SesameBlePermissions.MODERN_PERMISSIONS.none { it in SesameBlePermissions.LEGACY_PERMISSIONS })
    }
}

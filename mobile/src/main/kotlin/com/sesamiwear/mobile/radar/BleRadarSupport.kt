package com.sesamiwear.mobile.radar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.mobile.ble.SesameBlePermissions

/** レーダーへ置く1台ぶんのブリップ。[label]がnullなら名前を出さない（1台だけのとき）。 */
internal data class RadarBlip(
    val deviceUuid: String,
    val label: String?,
    val snapshot: SesameBleProbeSnapshot,
)

/** Bluetoothの権限の状態と、許可を求める操作（レーダー画面で共通）。 */
internal class RadarPermission(
    val granted: Boolean,
    val request: () -> Unit,
)

/**
 * Bluetoothの権限の状態を覚え、最初に足りなければ許可を求める。[usesBluetooth]がfalse
 * （撮影モードの撮影用の動き）なら、権限が無くても許可済みとして扱う。
 */
@Composable
internal fun rememberRadarPermission(usesBluetooth: Boolean): RadarPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(!usesBluetooth || SesameBlePermissions.hasAll(context)) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = !usesBluetooth || SesameBlePermissions.hasAll(context)
        }
    val request = { launcher.launch(SesameBlePermissions.missing(context).toTypedArray()) }
    LaunchedEffect(Unit) { if (!granted) request() }
    return RadarPermission(granted, request)
}

/** 画面が前面にいるか。前面にいる間だけスキャンするために使う（`ON_PAUSE`で止める）。 */
@Composable
internal fun rememberResumed(): Boolean {
    var resumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    return resumed
}

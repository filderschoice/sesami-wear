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
import com.sesamiwear.mobile.ble.SesameBleHeldSessions
import com.sesamiwear.mobile.ble.SesameBlePermissions

/** レーダーへ置く1台ぶんのブリップ。[label]がnullなら名前を出さない（1台だけのとき）。 */
internal data class RadarBlip(
    val deviceUuid: String,
    val label: String?,
    val snapshot: SesameBleProbeSnapshot,
    /** タップしてダイヤル操作へ移れるか（BL-283）。 */
    val tappable: Boolean = false,
    /** レーダー上の向き（ラジアン）。既定はデバイスごとに固定した値。複数台は均等に分ける（[RadarHitTest.evenBearing]）。 */
    val bearing: Float = bearingOf(deviceUuid),
)

/**
 * 接続確認に成功したあと接続を保持していることの説明（BL-279）。保持の寿命は[SesameBleHeldSessions.TTL_MILLIS]。
 * 保持中の施錠・解錠は、探索・接続・ログインを省いて再利用するため速い。
 */
internal val HOLD_HINT: String =
    "接続を約${SesameBleHeldSessions.TTL_MILLIS / MILLIS_PER_MINUTE}分保持します。" +
        "この間の施錠・解錠は、接続し直さずに実行されます。"

private const val MILLIS_PER_MINUTE = 60_000L

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

package com.sesamiwear.mobile.radar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.mobile.ble.BleProbeOperations
import com.sesamiwear.mobile.ble.SesameBleMultiProber
import com.sesamiwear.mobile.ble.SesameBlePermissions
import com.sesamiwear.mobile.ble.SesameBleProber
import com.sesamiwear.mobile.showcase.ShowcaseMode
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.ui.SesameTheme
import kotlinx.coroutines.launch

/**
 * 「Bluetooth接続を確認」画面（BL-272、スマートフォン）。デバイスカードから開く。
 *
 * SesameへのBluetooth接続は施錠/解錠・更新の背景で行われ、利用者から見えない。この画面で
 * 対象Sesameの電波をレーダーのように追い（距離は電波強度からの**目安**）、「接続を試す」で
 * 接続・ログインまで確かめる。画面を開いている間（前面にいる間）だけスキャンし、離れたら止める。
 * 実行は[SesameBleProber]、判定と文言はcoreにある。画面の組み立てだけのAndroid依存クラスのため
 * ユニットテスト対象外。
 */
class BleRadarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uuid = intent.getStringExtra(EXTRA_DEVICE_UUID)
        val registered = SesameDeviceStores.credentials(this).loadAll()
        if (uuid == SesameWearProtocol.ALL_DEVICES_TARGET_UUID) {
            showAll(registered)
            return
        }
        val credentials = uuid?.let { registered.find { it.uuid == uuid } }
        if (credentials == null) {
            finish()
            return
        }
        // デバッグ版の撮影モード中は、Bluetoothを使わず撮影用の状態で動かす（ダイヤル操作と同じ差し替え）。
        val prober = ShowcaseMode.probeOperationsOrNull(this, credentials) ?: SesameBleProber(this, credentials)
        setContent {
            SesameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BleRadarScreen(credentials = credentials, prober = prober, onClose = ::finish)
                }
            }
        }
    }

    /** 「まとめて接続確認」（BL-280）。登録が無ければ何も出さず閉じる。 */
    private fun showAll(devices: List<SesameCredentials>) {
        if (devices.isEmpty()) {
            finish()
            return
        }
        val probe = ShowcaseMode.multiProbeOperationsOrNull(this, devices) ?: SesameBleMultiProber(this, devices)
        setContent {
            SesameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BleRadarAllScreen(devices = devices, probe = probe, onClose = ::finish)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_DEVICE_UUID = "device_uuid"

        /** 登録済み全デバイスの「まとめて接続確認」を開く（BL-280）。 */
        fun createAllIntent(context: Context): Intent =
            createIntent(context, SesameWearProtocol.ALL_DEVICES_TARGET_UUID)

        fun createIntent(
            context: Context,
            deviceUuid: String,
        ): Intent = Intent(context, BleRadarActivity::class.java).putExtra(EXTRA_DEVICE_UUID, deviceUuid)
    }
}

@Composable
private fun BleRadarScreen(
    credentials: SesameCredentials,
    prober: BleProbeOperations,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(!prober.usesBluetooth || SesameBlePermissions.hasAll(context)) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionGranted = !prober.usesBluetooth || SesameBlePermissions.hasAll(context)
        }
    var resumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }

    var observed by remember { mutableStateOf(SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING)) }
    var checking by remember { mutableStateOf<SesameBleProbeSnapshot?>(null) }
    var checkResult by remember { mutableStateOf<SesameBleProbeSnapshot?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(SesameBlePermissions.missing(context).toTypedArray())
    }
    // 前面にいて、権限があり、接続確認中でないときだけスキャンする（接続とスキャンを同時に走らせない）。
    LaunchedEffect(permissionGranted, resumed, checking == null) {
        if (permissionGranted && resumed && checking == null) {
            observed = SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING)
            prober.observe().collect { observed = it }
        }
    }

    val shown = checking ?: observed
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = credentials.displayName.ifBlank { DEFAULT_NAME }, style = MaterialTheme.typography.headlineSmall)
        BleRadar(snapshot = shown, deviceUuid = credentials.uuid, modifier = Modifier.fillMaxWidth())
        StatusTexts(shown = shown, checkResult = checkResult)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text(text = "閉じる") }
            Button(
                enabled = permissionGranted && checking == null,
                onClick = {
                    scope.launch {
                        val rssi = observed.rssiDbm
                        checking = SesameBleProbeSnapshot(SesameBleProbeStage.CONNECTING, rssi)
                        checkResult = null
                        checkResult = prober.checkConnection(rssi) { checking = it }
                        checking = null
                    }
                },
            ) { Text(text = if (checking == null) "接続を試す" else "確認中…") }
        }
        if (!permissionGranted) {
            OutlinedButton(
                onClick = { permissionLauncher.launch(SesameBlePermissions.missing(context).toTypedArray()) },
            ) { Text(text = "Bluetoothの権限を許可") }
        }
    }
}

@Composable
private fun StatusTexts(
    shown: SesameBleProbeSnapshot,
    checkResult: SesameBleProbeSnapshot?,
) {
    val problem = MaterialTheme.colorScheme.error
    Text(
        text = shown.summaryLine(),
        style = MaterialTheme.typography.titleLarge,
        color = if (shown.stage.isProblem) problem else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    shown.rssiDbm?.let { Text(text = "電波強度 ${it}dBm", style = MaterialTheme.typography.bodyMedium) }
    checkResult?.let { result ->
        Text(
            text = "接続確認：${result.stage.label}" + (result.connectionDetail()?.let { "（$it）" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            color = if (result.stage.isProblem) problem else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        if (result.stage == SesameBleProbeStage.CONNECTED) {
            Text(
                text = HOLD_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (shown.stage == SesameBleProbeStage.NOT_FOUND || checkResult?.stage == SesameBleProbeStage.NOT_FOUND) {
        Text(
            text = "電波が見つかりません（探索は続けています）。" + SesameRouteLabel.OFFICIAL_APP_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "距離は電波の強さからの目安です。壁・人・向きで大きく変わります。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private const val DEFAULT_NAME = "Sesame"

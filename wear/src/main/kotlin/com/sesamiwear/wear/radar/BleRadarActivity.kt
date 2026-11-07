package com.sesamiwear.wear.radar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameDemoMode
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.ble.SesameBleMultiProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider
import com.sesamiwear.wear.messaging.SesameDeviceListReader
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「Bluetooth接続を確認」画面（BL-273、ウォッチ）。アプリ本体の一覧の「接続確認」から開く。
 *
 * ウォッチ自身はsecretKeyもBLEも持たないため、**スマートフォンへ確認を依頼し**、スマートフォンから
 * 返る進捗（段階・距離）をレーダーと2行の文字で表示する。画面を開いて前面にいる間だけ確認を続け、
 * 離れたら停止を依頼する（届かなくてもスマートフォン側が一定時間で止める）。
 * 画面をタップすると、スマートフォンが接続・ログインまで試す（[SesameWearProtocol.PATH_BLE_PROBE_CONNECT]）。
 * 登録が2台以上で対象を指定しないで開いたときは、最初に対象を選ぶ。
 * Compose画面のためユニットテスト対象外（段階の文言・距離はcoreでテスト済み）。
 */
class BleRadarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uuid = intent.getStringExtra(EXTRA_DEVICE_UUID)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) { BleRadarScreen(initialUuid = uuid) }
            }
        }
    }

    companion object {
        private const val EXTRA_DEVICE_UUID = "device_uuid"

        /** [deviceUuid]がnullなら、登録デバイスから選ぶ（1台だけなら選ばずに始める）。 */
        fun createIntent(
            context: Context,
            deviceUuid: String? = null,
        ): Intent =
            Intent(context, BleRadarActivity::class.java).apply {
                deviceUuid?.let { putExtra(EXTRA_DEVICE_UUID, it) }
            }
    }
}

@Composable
private fun BleRadarScreen(initialUuid: String?) {
    val context = LocalContext.current
    var uuid by remember { mutableStateOf(initialUuid) }
    var devices by remember { mutableStateOf<List<SesameDeviceSummary>?>(null) }
    LaunchedEffect(initialUuid) {
        if (initialUuid == null) {
            devices = SesameDeviceListReader.readLatest(context)
            devices?.singleOrNull()?.let { uuid = it.uuid }
        }
    }
    val target = uuid
    when {
        target != null && SesameDemoMode.isDemoDevice(target) -> Message(DEMO_MESSAGE)
        target == SesameWearProtocol.ALL_DEVICES_TARGET_UUID -> RadarAllContent(devices = devices.orEmpty())
        target != null -> RadarContent(deviceUuid = target)
        devices == null -> Message(LOADING_MESSAGE)
        devices.isNullOrEmpty() -> Message(NO_DEVICE_MESSAGE)
        else -> DevicePicker(devices = devices.orEmpty(), onSelected = { uuid = it })
    }
}

@Composable
private fun RadarContent(deviceUuid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING)) }
    var phoneMissing by remember { mutableStateOf(false) }
    val resumed = rememberResumed()

    ProbeLink(
        deviceUuid = deviceUuid,
        resumed = resumed,
        onProgress = { snapshot = it },
        onPhoneMissing = { phoneMissing = it },
    )

    val (first, second) = snapshot.compactLines()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(enabled = !phoneMissing) {
                    scope.launch {
                        SesameConnectedNodeProvider.firstConnectedNodeId(context)?.let {
                            SesameCommandSenderProvider.create(context)
                                .requestBleProbe(it, SesameWearProtocol.PATH_BLE_PROBE_CONNECT, deviceUuid)
                        }
                    }
                }
                .padding(top = RADAR_TOP_PADDING_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BleRadar(snapshot = snapshot, deviceUuid = deviceUuid, modifier = Modifier.fillMaxWidth(RADAR_WIDTH_FRACTION))
        Text(
            text = if (phoneMissing) PHONE_MISSING_LINE else first,
            style = MaterialTheme.typography.title3,
            color = if (!phoneMissing && snapshot.stage.isProblem) MaterialTheme.colors.error else Color.Unspecified,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = if (phoneMissing) "" else second ?: HINT_LINE,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** 画面が前面にいるか。前面にいる間だけ確認を依頼するために使う（`ON_PAUSE`で停止を依頼する）。 */
@Composable
internal fun rememberResumed(): Boolean {
    var resumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    return resumed
}

/**
 * スマートフォンとの確認のやり取り（BL-273 / BL-280）。進捗のメッセージ（単体は[onProgress]、全デバイスは
 * [onProgressAll]）を受け、前面にいる間だけ確認を依頼し、
 * 離れたら停止を依頼する。スマートフォンへつながらないときは[onPhoneMissing]へ知らせる。
 */
@Composable
internal fun ProbeLink(
    deviceUuid: String,
    resumed: Boolean,
    onProgress: (SesameBleProbeSnapshot) -> Unit,
    onPhoneMissing: (Boolean) -> Unit,
    onProgressAll: (SesameBleMultiProbeSnapshot) -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnProgressAll by rememberUpdatedState(onProgressAll)
    DisposableEffect(deviceUuid) {
        val client = Wearable.getMessageClient(context)
        val listener =
            MessageClient.OnMessageReceivedListener { event ->
                when (event.path) {
                    SesameWearProtocol.PATH_BLE_PROBE_PROGRESS ->
                        SesameBleProbeSnapshot.decode(event.data)?.let(currentOnProgress)
                    SesameWearProtocol.PATH_BLE_PROBE_PROGRESS_ALL ->
                        SesameBleMultiProbeSnapshot.decode(event.data)?.let(currentOnProgressAll)
                }
            }
        client.addListener(listener)
        onDispose { client.removeListener(listener) }
    }
    LaunchedEffect(deviceUuid, resumed) {
        if (!resumed) return@LaunchedEffect
        val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(context)
        onPhoneMissing(nodeId == null)
        if (nodeId == null) return@LaunchedEffect
        onProgress(SesameBleProbeSnapshot(SesameBleProbeStage.SCANNING))
        val sender = SesameCommandSenderProvider.create(context)
        sender.requestBleProbe(nodeId, SesameWearProtocol.PATH_BLE_PROBE_START, deviceUuid)
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                sender.requestBleProbe(nodeId, SesameWearProtocol.PATH_BLE_PROBE_STOP, deviceUuid)
            }
        }
    }
}

@Composable
private fun DevicePicker(
    devices: List<SesameDeviceSummary>,
    onSelected: (String) -> Unit,
) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text(text = PICKER_TITLE, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center) }
        // 2台以上あるときは、全デバイスをまとめて確かめる選択肢を先頭に出す（BL-280）。
        if (devices.size >= 2) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PICKER_PADDING_DP.dp),
                    colors = ChipDefaults.primaryChipColors(),
                    onClick = { onSelected(SesameWearProtocol.ALL_DEVICES_TARGET_UUID) },
                    label = { Text(text = ALL_DEVICES_LABEL, maxLines = 1) },
                )
            }
        }
        items(devices) { device ->
            Chip(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PICKER_PADDING_DP.dp),
                colors = ChipDefaults.secondaryChipColors(),
                onClick = { onSelected(device.uuid) },
                label = { Text(text = device.displayName.ifBlank { device.uuid }, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = MESSAGE_PADDING_DP.dp),
        )
    }
}

private const val PICKER_TITLE = "接続確認"
private const val ALL_DEVICES_LABEL = "全デバイス"
private const val HINT_LINE = "タップで接続"
private const val PHONE_MISSING_LINE = "スマホ未接続"
private const val LOADING_MESSAGE = "読み込み中"
private const val NO_DEVICE_MESSAGE = "スマホでセサミを\n登録してください"
private const val DEMO_MESSAGE = "デモでは\n使えません"
private const val RADAR_TOP_PADDING_DP = 18
private const val RADAR_WIDTH_FRACTION = 0.62f
private const val PICKER_PADDING_DP = 24
private const val MESSAGE_PADDING_DP = 24

package com.sesamiwear.mobile.showcase

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.mobile.ui.SesameTheme
import kotlinx.coroutines.launch

/**
 * 撮影モードの操作画面（BL-213）。**デバッグ版にだけ含まれる**（`src/debug/AndroidManifest.xml`で宣言）。
 *
 * 設定メニューの「撮影モード」から開く。モードのオン/オフ、見本の3台への置き換え、撮影用デバイスごとの
 * 施錠状態・電池残量・経路・直近の失敗・最終取得時刻の切り替えを行う。切り替えると、撮影モード中なら
 * ウォッチとホーム画面ウィジェットへすぐ反映される（[ShowcaseSync]）。撮影用デバイスの追加・編集・削除は
 * 撮影モード中のカード一覧で行う。
 */
class ShowcaseModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SesameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShowcaseModeScreen(onBack = ::finish)
                }
            }
        }
    }
}

/** 画面が持つ状態。ボタンを押すたびに保存先から読み直す。 */
private class ShowcaseScreenState(private val context: Context) {
    var active by mutableStateOf(ShowcaseMode.isActive(context))
    var devices by mutableStateOf(load())
    var busy by mutableStateOf(false)

    fun reload() {
        active = ShowcaseMode.isActive(context)
        devices = load()
    }

    private fun load(): List<Pair<SesameCredentials, ShowcaseDeviceState>> {
        val store = ShowcaseMode.showcaseLockStateStore(context)
        val now = System.currentTimeMillis()
        val devices = ShowcaseMode.showcaseDevices(context).loadAll()
        return devices.map { it to ShowcaseDeviceState.of(store.load(it.uuid), now) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowcaseModeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { ShowcaseScreenState(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.reload() }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            state.busy = true
            try {
                block()
            } finally {
                state.busy = false
                state.reload()
            }
        }
    }

    fun applyAll(transform: (ShowcaseDeviceState) -> ShowcaseDeviceState) =
        run {
            for ((device, current) in state.devices) ShowcaseSync.applyState(context, device.uuid, transform(current))
        }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { Text("撮影モード") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ModeSection(
                    active = state.active,
                    enabled = !state.busy,
                    actions =
                        ModeActions(
                            onToggle = { run { ShowcaseSync.setActive(context, it) } },
                            onLoadPresets = { run { ShowcaseSync.loadPresets(context) } },
                            onLockAll = { applyAll { it.copy(isLocked = true, ageMillis = 0) } },
                            onUnlockAll = { applyAll { it.copy(isLocked = false, ageMillis = 0) } },
                        ),
                )
            }
            items(state.devices, key = { it.first.uuid }) { (device, current) ->
                DeviceStateCard(
                    name = device.displayName.ifBlank { device.uuid },
                    state = current,
                    enabled = !state.busy,
                    onChange = { run { ShowcaseSync.applyState(context, device.uuid, it) } },
                )
            }
        }
    }
}

/** モードの操作ボタンが呼ぶ処理。 */
private class ModeActions(
    val onToggle: (Boolean) -> Unit,
    val onLoadPresets: () -> Unit,
    val onLockAll: () -> Unit,
    val onUnlockAll: () -> Unit,
)

@Composable
private fun ModeSection(
    active: Boolean,
    enabled: Boolean,
    actions: ModeActions,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "撮影モード", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = actions.onToggle, enabled = enabled)
        }
        Text(
            text =
                "オンの間は、ウォッチ・ウィジェット・カード一覧に下の撮影用デバイスが出ます。" +
                    "施錠/解錠しても実物のセサミは操作されず、登録済みの資格情報も変わりません。" +
                    "撮影用デバイスはオンの間にカード一覧から追加・編集・削除でき、" +
                    "APIキー・秘密鍵はダミー値に置き換えて保存されます。" +
                    "Tileは撮影用デバイスへ設定し直してください（「全デバイス」はそのまま使えます）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRowOf(spacing = 8) {
            OutlinedButton(onClick = actions.onLoadPresets, enabled = enabled) { Text("見本の3台に置き換える") }
            OutlinedButton(onClick = actions.onLockAll, enabled = enabled) { Text("全台を施錠中") }
            OutlinedButton(onClick = actions.onUnlockAll, enabled = enabled) { Text("全台を解錠中") }
        }
    }
}

@Composable
private fun DeviceStateCard(
    name: String,
    state: ShowcaseDeviceState,
    enabled: Boolean,
    onChange: (ShowcaseDeviceState) -> Unit,
) {
    // 未取得のときは電池・経路・取得時刻を保存できない（施錠状態と一緒にしか残らない）ため押せなくする。
    val known = enabled && state.isLocked != null
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            OptionRow("施錠", ShowcaseStateOptions.lock, state.isLocked, enabled) { onChange(state.copy(isLocked = it)) }
            OptionRow("電池", ShowcaseStateOptions.battery, state.batteryPercentage, known) {
                onChange(state.copy(batteryPercentage = it))
            }
            OptionRow("経路", ShowcaseStateOptions.route, state.route, known) { onChange(state.copy(route = it)) }
            OptionRow("失敗", ShowcaseStateOptions.failure, state.failure, enabled) { onChange(state.copy(failure = it)) }
            OptionRow("取得", ShowcaseStateOptions.age, ShowcaseStateOptions.ageOptionOf(state.ageMillis).value, known) {
                onChange(state.copy(ageMillis = it))
            }
        }
    }
}

@Composable
private fun <T> OptionRow(
    label: String,
    options: List<ShowcaseStateOptions.Option<T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        FlowRowOf(spacing = 6) {
            options.forEach { option ->
                FilterChip(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOf(
    spacing: Int,
    content: @Composable () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.dp)) { content() }
}

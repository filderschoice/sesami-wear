package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.display.SesameRouteLabel
import com.sesamiwear.mobile.ble.SesameRoutePolicyStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * 通信まわりの設定をまとめたセクション（BL-153 / BL-167）。
 *
 * 経路の方針（[RoutePolicySection]）と、BLE直接操作の権限（[BlePermissionSection]）は
 * どちらも「どうやってセサミへつなぐか」の設定のため、画面上でも隣り合わせに置く。
 * 呼び出し側（資格情報設定画面）の行数を増やさないために1つにまとめている。
 */
@Composable
internal fun ConnectionSettingsSection() {
    RoutePolicySection()
    BlePermissionSection()
}

/**
 * 経路の方針（[SesameRoutePolicy]）を選ぶセクション（BL-167）。
 *
 * 常時出すのは現在の方針の1行と「変更」だけ。資格情報設定画面は縦スクロールしないため、
 * 選択肢とその説明はダイアログへ回す（`BlePermissionSection`と同じ考え方）。
 *
 * 選んだ値はその場で保存し、次の操作から効く（[SesameDeviceCommandExecutorFactory]は
 * 操作のたびに読み直す）。
 *
 * Compose画面のためユニットテスト対象外（文言は`SesameRouteLabelTest`で検証済み）。
 */
@Composable
internal fun RoutePolicySection() {
    val context = LocalContext.current
    val store = remember { SesameRoutePolicyStore(SharedPreferencesKeyValueStore.forBleReachability(context)) }
    var policy by remember { mutableStateOf(store.load()) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        RoutePolicyPickerDialog(
            selected = policy,
            onSelect = {
                store.save(it)
                policy = it
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "操作の経路：${SesameRouteLabel.policyLabel(policy)}", modifier = Modifier.weight(1f))
        TextButton(onClick = { showPicker = true }) {
            Text(text = "変更")
        }
    }
}

/** 方針の選択肢と、それぞれを選ぶと何が変わるかを並べるダイアログ。 */
@Composable
private fun RoutePolicyPickerDialog(
    selected: SesameRoutePolicy,
    onSelect: (SesameRoutePolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "操作の経路") },
        text = {
            Column {
                SesameRoutePolicy.entries.forEach { candidate ->
                    val mark = if (candidate == selected) "● " else "○ "
                    TextButton(
                        onClick = { onSelect(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(text = mark + SesameRouteLabel.policyLabel(candidate))
                            Text(text = SesameRouteLabel.policyDescription(candidate))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "閉じる") }
        },
    )
}

package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sesamiwear.core.display.SesameRouteLabel

/**
 * 上部バーの設定メニュー（BL-180）。
 *
 * もとはヘルプが見出し横のボタン、経路の方針とBluetooth権限が画面本体の2行に常時置かれており、
 * 主画面の情報量を押し上げていた。3つとも「たまに開く設定」なので、⋮のメニューへまとめ、
 * 実際の操作はそれぞれのダイアログで行う（2026-09-20、ユーザー指示）。
 *
 * **現在の状態はメニュー項目の副題に出す。** 設定を開かなくても、メニューを開いた時点で
 * 経路の方針とBluetoothの許可状況が分かるようにするため。
 *
 * Bluetoothの項目は、許可済みで求めるものが無いとき（`buttonLabel`がnull）は押せない。
 * 状態表示としてだけ残す。
 */
@Composable
internal fun SettingsMenu() {
    var expanded by remember { mutableStateOf(false) }
    var showRoutePicker by remember { mutableStateOf(false) }
    var showBleRationale by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val routePolicy = rememberRoutePolicyState()
    val blePermission = rememberBlePermissionState()

    if (showRoutePicker) {
        RoutePolicyPickerDialog(
            selected = routePolicy.policy,
            onSelect = {
                routePolicy.select(it)
                showRoutePicker = false
            },
            onDismiss = { showRoutePicker = false },
        )
    }
    if (showBleRationale) {
        BlePermissionRationaleDialog(
            permission = blePermission,
            onConfirm = {
                showBleRationale = false
                blePermission.confirm()
            },
            onDismiss = { showBleRationale = false },
        )
    }
    if (showHelp) HelpDialog(onDismiss = { showHelp = false })

    IconButton(onClick = { expanded = true }) {
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = MENU_DESCRIPTION)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SettingsMenuItem(
            title = ROUTE_POLICY_TITLE,
            subtitle = SesameRouteLabel.policyLabel(routePolicy.policy),
            onClick = {
                expanded = false
                showRoutePicker = true
            },
        )
        SettingsMenuItem(
            title = BLE_PERMISSION_MENU_TITLE,
            subtitle = blePermission.shortStatus,
            enabled = blePermission.buttonLabel != null,
            onClick = {
                expanded = false
                showBleRationale = true
            },
        )
        SettingsMenuItem(
            title = HELP_MENU_TITLE,
            subtitle = null,
            onClick = {
                expanded = false
                showHelp = true
            },
        )
    }
}

/** メニュー1項目。副題を持てるようにするため、`text`スロットへ2行を置く。 */
@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        text = {
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private const val MENU_DESCRIPTION = "設定"
private const val HELP_MENU_TITLE = "ヘルプ"

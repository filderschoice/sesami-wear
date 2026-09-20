package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Column
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
 * 経路の方針（[SesameRoutePolicy]）の現在値と、選び直す手段（BL-167 / BL-180）。
 *
 * もとは画面本体へ1行（現在の方針と「変更」）を常時置いていたが、主画面の情報量を抑えるため
 * 上部バーの設定メニュー（[SettingsMenu]）へ移した。現在の方針はメニュー項目の副題に出るため、
 * メニューを開くだけで分かる。
 *
 * 選んだ値はその場で保存し、次の操作から効く（`SesameDeviceCommandExecutorFactory`は
 * 操作のたびに読み直す）。
 *
 * Compose画面のためユニットテスト対象外（文言は`SesameRouteLabelTest`で検証済み）。
 */
internal class RoutePolicyState(
    val policy: SesameRoutePolicy,
    val select: (SesameRoutePolicy) -> Unit,
)

@Composable
internal fun rememberRoutePolicyState(): RoutePolicyState {
    val context = LocalContext.current
    val store = remember { SesameRoutePolicyStore(SharedPreferencesKeyValueStore.forBleReachability(context)) }
    var policy by remember { mutableStateOf(store.load()) }
    return RoutePolicyState(policy) { selected ->
        store.save(selected)
        policy = selected
    }
}

/** 方針の選択肢と、それぞれを選ぶと何が変わるかを並べるダイアログ。 */
@Composable
internal fun RoutePolicyPickerDialog(
    selected: SesameRoutePolicy,
    onSelect: (SesameRoutePolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = ROUTE_POLICY_TITLE) },
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

/** 設定メニューの項目名。ダイアログの見出しと揃える。 */
internal const val ROUTE_POLICY_TITLE = "操作の経路"

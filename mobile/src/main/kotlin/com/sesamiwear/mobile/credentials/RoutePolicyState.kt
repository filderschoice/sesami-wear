package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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

/**
 * 方針の選択肢と、それぞれを選ぶと何が変わるかを並べるダイアログ。
 *
 * 選択の表現はMaterial標準の[RadioButton]で、行全体をタップできるようにする（BL-185）。
 * もとは`TextButton`の中へ`Column(fillMaxWidth)`を入れ、先頭へ●／○の文字を付けていたが、
 * ボタンの内容が幅に収まらず**行頭の文字が左端で欠けていた**（2026-09-20のエミュレータ検証で観測。
 * ●が細い弧にしか見えず、折り返し最終行の「消費しません。」の「消」も半分欠けていた）。
 */
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SesameRoutePolicy.entries.forEach { candidate ->
                    RoutePolicyOption(
                        candidate = candidate,
                        isSelected = candidate == selected,
                        onSelect = { onSelect(candidate) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "閉じる") }
        },
    )
}

/** 選択肢1つ分。行全体がタップ対象で、選択状態は[RadioButton]で示す（BL-185）。 */
@Composable
private fun RoutePolicyOption(
    candidate: SesameRoutePolicy,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
                .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = SesameRouteLabel.policyLabel(candidate),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = SesameRouteLabel.policyDescription(candidate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 設定メニューの項目名。ダイアログの見出しと揃える。 */
internal const val ROUTE_POLICY_TITLE = "操作の経路"

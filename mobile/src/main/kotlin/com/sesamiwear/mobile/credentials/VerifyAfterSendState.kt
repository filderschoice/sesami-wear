package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.display.SesameVerifyLabel
import com.sesamiwear.mobile.command.SesameVerifyAfterSendStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * 「送信後の状態確認」（BL-293）の現在値と切り替える手段。既定はOFF。
 * 選んだ値はその場で保存し、次の操作から効く（`SesameDeviceCommandExecutorFactory`は操作のたびに読む）。
 *
 * Compose画面のためユニットテスト対象外（保存の可否は`SesameVerifyAfterSendStoreTest`、
 * 文言は`SesameVerifyLabelTest`で検証済み）。
 */
internal class VerifyAfterSendState(
    val isEnabled: Boolean,
    val setEnabled: (Boolean) -> Unit,
)

@Composable
internal fun rememberVerifyAfterSendState(): VerifyAfterSendState {
    val context = LocalContext.current
    val store = remember { SesameVerifyAfterSendStore(SharedPreferencesKeyValueStore.forBleReachability(context)) }
    var enabled by remember { mutableStateOf(store.isEnabled()) }
    return VerifyAfterSendState(enabled) { next ->
        store.setEnabled(next)
        enabled = next
    }
}

/** オン・オフの切り替えと、オンにすると何が変わるか（API消費の増加と結果の遅れ）を示すダイアログ。 */
@Composable
internal fun VerifyAfterSendDialog(
    state: VerifyAfterSendState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = SesameVerifyLabel.TITLE) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = SesameVerifyLabel.statusOf(state.isEnabled), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.isEnabled, onCheckedChange = state.setEnabled)
                }
                Text(
                    text = SesameVerifyLabel.DESCRIPTION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "閉じる") }
        },
    )
}

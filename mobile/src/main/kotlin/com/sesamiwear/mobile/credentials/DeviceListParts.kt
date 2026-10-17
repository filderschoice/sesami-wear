package com.sesamiwear.mobile.credentials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sesamiwear.mobile.R

/**
 * 主画面の一覧の部品（BL-245）。
 *
 * - [ApiUsageCard]: 今月のAPI呼び出し回数（BL-147）。もとは一覧の上の1行の小さな文字で、上限に
 *   近づいているかを読み取りにくかった。回数を大きく出し、「このアプリからの分のみ・目安」の注記は残す。
 * - [EmptyDeviceState]: Sesameが未登録のときの表示。もとは1行の文字だけで、次に何をすればよいか
 *   （登録する／デモで試す）が分かりにくかった。
 */
@Composable
internal fun ApiUsageCard(count: Int) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = API_USAGE_TITLE, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = API_USAGE_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$count 回",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun EmptyDeviceState(onAdd: () -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) HelpDialog(onDismiss = { showHelp = false })
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(EMPTY_BADGE_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shortcut_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(EMPTY_ICON_SIZE_DP.dp),
            )
        }
        Text(text = EMPTY_TITLE, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = EMPTY_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAdd) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(text = EMPTY_ADD_LABEL, modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = { showHelp = true }) { Text(text = EMPTY_HELP_LABEL) }
    }
}

private const val API_USAGE_TITLE = "今月のAPI呼び出し"
private const val API_USAGE_NOTE = "このアプリからの分のみ・目安です。上限は契約内容で変わります"
private const val EMPTY_TITLE = "Sesameを登録しましょう"
private const val EMPTY_BODY =
    "SESAME Bizで取得したuuid・apikey・secretKeyを登録すると、ウォッチのタイルやホーム画面の" +
        "ウィジェットから施錠・解錠できます。"
private const val EMPTY_ADD_LABEL = "Sesameを追加"
private const val EMPTY_HELP_LABEL = "値の取得方法・デモで試す方法を見る"
private const val EMPTY_BADGE_SIZE_DP = 88
private const val EMPTY_ICON_SIZE_DP = 44

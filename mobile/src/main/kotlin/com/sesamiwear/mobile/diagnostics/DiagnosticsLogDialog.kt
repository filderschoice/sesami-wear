package com.sesamiwear.mobile.diagnostics

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sesamiwear.core.diagnostics.SesameDiagnosticsLog

/**
 * 診断ログの一覧（BL-188）。上部バーの設定メニューから開く全画面ダイアログ。
 *
 * 通信エラーなどが起きたときに、利用者から内容を連携してもらって解析するためのもの。
 * 施錠・解錠・状態取得の成功と失敗を新しい順に並べ、「コピー」と「共有」で持ち出せるようにする
 * （2026-09-20のユーザー判断）。
 *
 * **表示にも連携用の全文にもuuid・apikey・secretKeyは含まれない**
 * （`core.diagnostics.SesameDiagnosticsEntry`のKDoc）。そのまま貼って送っても資格情報は漏れない。
 *
 * Compose画面のためユニットテスト対象外（整形と保持件数は`SesameDiagnosticsLogTest`で検証済み）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val log = remember { DiagnosticsLogFactory.create(context) }
    // 開いている間は更新しない（読んでいる最中に行が増えて位置がずれるのを避ける）。
    val lines = remember { log.load().map { log.format(it) } }
    val shareText = remember { log.formatAll(DiagnosticsEnvironment.headerLines()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(TITLE) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = CLOSE_LABEL)
                        }
                    },
                    actions = {
                        TextButton(onClick = { copy(clipboard, shareText) }) { Text(COPY_LABEL) }
                        TextButton(onClick = { share(context, shareText) }) { Text(SHARE_LABEL) }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = DESCRIPTION, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = PRIVACY_NOTE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (lines.isEmpty()) {
                    item {
                        Text(
                            text = SesameDiagnosticsLog.EMPTY_MESSAGE,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(lines) { line -> Text(text = line, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

private fun copy(
    clipboard: ClipboardManager,
    text: String,
) {
    clipboard.setText(AnnotatedString(text))
}

/**
 * Androidの共有シートへ渡す。宛先は利用者が選ぶため、アプリからどこかへ自動送信することはない。
 */
private fun share(
    context: Context,
    text: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, TITLE)
            .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, SHARE_LABEL))
}

private const val TITLE = "診断ログ"
private const val CLOSE_LABEL = "閉じる"
private const val COPY_LABEL = "コピー"
private const val SHARE_LABEL = "共有"
private const val DESCRIPTION =
    "施錠・解錠・状態取得の直近50件です。うまく動かないときは、" +
        "この内容をコピーまたは共有して開発者へお知らせください。"
private const val PRIVACY_NOTE = "uuid・apikey・secretKeyは含まれません（セサミの表示名だけが入ります）。"

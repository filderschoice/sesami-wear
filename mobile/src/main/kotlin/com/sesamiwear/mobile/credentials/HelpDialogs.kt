package com.sesamiwear.mobile.credentials

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sesamiwear.mobile.help.HelpContent
import com.sesamiwear.mobile.help.HelpTopic

/**
 * ヘルプのダイアログ（BL-059 / BL-113）。初期表示では出さず、設定メニューからのみ開く。
 * 項目を選ぶ[HelpMenuDialog]と、選んだ項目の本文を出す[HelpTopicDialog]の2段構成にしている。
 * 値の取得方法だけの単一ダイアログでは、資格情報が未登録でもウォッチ側でデモ（BL-109）を
 * 操作できることに気づく導線が無かったため（BL-113）。
 */
@Composable
internal fun HelpDialog(onDismiss: () -> Unit) {
    var selectedTopic by remember { mutableStateOf<HelpTopic?>(null) }
    val topic = selectedTopic
    if (topic == null) {
        HelpMenuDialog(onTopicSelected = { selectedTopic = it }, onDismiss = onDismiss)
    } else {
        HelpTopicDialog(topic = topic, onBack = { selectedTopic = null }, onDismiss = onDismiss)
    }
}

/** ヘルプの項目一覧（BL-113）。文言と並び順は[HelpContent]が持つ。 */
@Composable
private fun HelpMenuDialog(
    onTopicSelected: (HelpTopic) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(HelpContent.MENU_TITLE) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpContent.topics.forEach { topic ->
                    TextButton(
                        onClick = { onTopicSelected(topic) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = topic.title, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

/**
 * ヘルプ1項目の本文（BL-113）。本文が画面の高さを超える項目があるためスクロール可能にし、
 * 外部ページへのリンクを持つ項目ではブラウザを開くボタンを添える（1項目に複数可、BL-144）。
 */
@Composable
private fun HelpTopicDialog(
    topic: HelpTopic,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(topic.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topic.paragraphs.forEach { paragraph -> Text(paragraph) }
                topic.links.forEach { link ->
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    }) {
                        Text(link.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("戻る") }
        },
    )
}

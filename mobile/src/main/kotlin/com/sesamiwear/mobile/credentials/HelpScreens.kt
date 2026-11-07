package com.sesamiwear.mobile.credentials

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sesamiwear.mobile.help.HelpContent
import com.sesamiwear.mobile.help.HelpTopic
import com.sesamiwear.mobile.settings.SettingsPageScaffold

/**
 * ヘルプの項目一覧（BL-059 / BL-113）。文言と並び順は[HelpContent]が持つ。
 * もとは小さなダイアログで、項目が増えるほど窮屈だったため、設定画面のページにした（2026-11-08）。
 * 値の取得方法だけでは、資格情報が未登録でもウォッチ側でデモ（BL-109）を操作できることに
 * 気づく導線が無かったため、項目形式にしている（BL-113）。
 */
@Composable
internal fun HelpListScreen(
    onTopic: (HelpTopic) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(title = HelpContent.MENU_TITLE, onBack = onBack) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            HelpContent.topics.forEach { topic ->
                ListItem(
                    modifier = Modifier.clickable { onTopic(topic) },
                    headlineContent = { Text(text = topic.title) },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                )
            }
        }
    }
}

/**
 * ヘルプ1項目の本文（BL-113）。本文が画面の高さを超える項目があるためスクロール可能にし、
 * 外部ページへのリンクを持つ項目ではブラウザを開くボタンを添える（1項目に複数可、BL-144）。
 */
@Composable
internal fun HelpTopicScreen(
    topic: HelpTopic,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SettingsPageScaffold(title = topic.title, onBack = onBack) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topic.paragraphs.forEach { paragraph -> Text(paragraph) }
            topic.links.forEach { link ->
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }) {
                    Text(link.label)
                }
            }
        }
    }
}

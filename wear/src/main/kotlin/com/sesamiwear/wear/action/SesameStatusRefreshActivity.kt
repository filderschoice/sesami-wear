package com.sesamiwear.wear.action

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sesamiwear.wear.messaging.SesameCommandSenderProvider
import com.sesamiwear.wear.messaging.SesameConnectedNodeProvider

/**
 * Tileのデバイス名表示タップから起動される、ユーザー契機の状態更新実行画面（BL-063）。
 * Tile表示時の自動状態取得（BL-061、DataItemが30秒以上古い場合のみ）とは別に、
 * ユーザーが任意のタイミングで最新状態を取得し直せる導線として設ける。施錠/解錠は行わず、
 * [com.sesamiwear.wear.messaging.SesameCommandSender.requestStatus]をFire-and-forgetで
 * 送信するのみで、結果はDataItem変更として非同期に届く。uuidが
 * [com.sesamiwear.core.SesameWearProtocol.ALL_DEVICES_TARGET_UUID]（「全デバイス」選択）の
 * 場合は登録済み全デバイスへ状態取得をリクエストする（BL-071、複数デバイス一括操作）。
 */
class SesameStatusRefreshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceUuid = intent.getStringExtra(SesameActionCommandParser.EXTRA_DEVICE_UUID)
        if (deviceUuid.isNullOrEmpty()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                SesameStatusRefreshScreen(deviceUuid = deviceUuid, onFinished = ::finish)
            }
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            deviceUuid: String,
        ): Intent =
            Intent(context, SesameStatusRefreshActivity::class.java).apply {
                putExtra(SesameActionCommandParser.EXTRA_DEVICE_UUID, deviceUuid)
            }
    }
}

@Composable
private fun SesameStatusRefreshScreen(
    deviceUuid: String,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(deviceUuid) {
        val nodeId = SesameConnectedNodeProvider.firstConnectedNodeId(context)
        if (nodeId != null) {
            val sender = SesameCommandSenderProvider.create(context)
            SesameActionTargetResolver.resolveDeviceUuids(context, deviceUuid).forEach { targetUuid ->
                sender.requestStatus(nodeId, targetUuid)
            }
        }
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "状態を更新中...")
    }
}

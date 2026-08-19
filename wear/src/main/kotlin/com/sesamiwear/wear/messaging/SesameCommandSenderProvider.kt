package com.sesamiwear.wear.messaging

import android.content.Context
import com.google.android.gms.wearable.Wearable

/**
 * [SesameCommandSender] をMessageClientベースで構築するファクトリ。
 * 実際の呼び出し（Tileのクリックアクション）はBL-014で行う。
 */
object SesameCommandSenderProvider {
    fun create(context: Context): SesameCommandSender {
        val messageClient = Wearable.getMessageClient(context)
        return SesameCommandSender(MessageClientSesameMessageSender(messageClient))
    }
}

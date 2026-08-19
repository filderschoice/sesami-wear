package com.sesamiwear.wear.messaging

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sesamiwear.wear.haptics.SesameHapticPlayer

/**
 * Mobile側から返された施錠/解錠結果（PATH_COMMAND_RESULT）を受信し、ハプティクスで通知する。
 * 判定ロジックは[SesameResultHandler]でテスト済み。MessageEvent呼び出し以外のロジックを
 * 持たない薄いアダプタのためユニットテスト対象外（実際の体感確認はBL-011で人手検証）。
 */
class SesameResultListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val pattern =
            SesameResultHandler.hapticPatternForMessage(messageEvent.path, messageEvent.data)
                ?: return
        SesameHapticPlayer(applicationContext).play(pattern)
    }
}

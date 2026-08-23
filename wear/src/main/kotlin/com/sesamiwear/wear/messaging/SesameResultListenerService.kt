package com.sesamiwear.wear.messaging

import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sesamiwear.wear.complication.SesameComplicationDataSourceService
import com.sesamiwear.wear.haptics.SesameHapticPlayer
import com.sesamiwear.wear.tile.SesameTileService

/**
 * Mobile側から返された施錠/解錠結果（PATH_COMMAND_RESULT）を受信し、ハプティクスで通知する。
 * 判定ロジックは[SesameResultHandler]でテスト済み。あわせてTile/Complicationの再描画を
 * 即座にリクエストする（BL-063、DataItem変更通知（onDataChanged）を待つと反映が遅れ、
 * もう一度タップしないと最新状態が表示されない問題への対応）。
 * MessageEvent呼び出し以外のロジックを持たない薄いアダプタのためユニットテスト対象外
 * （実際の体感確認はBL-011で人手検証）。
 */
class SesameResultListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} payloadSize=${messageEvent.data.size}")
        val pattern =
            SesameResultHandler.hapticPatternForMessage(messageEvent.path, messageEvent.data)
                ?: return
        Log.d(TAG, "playing haptic pattern=$pattern")
        SesameHapticPlayer(applicationContext).play(pattern)

        Log.d(TAG, "requesting tile/complication update")
        TileService.getUpdater(applicationContext).requestUpdate(SesameTileService::class.java)
        ComplicationDataSourceUpdateRequester.create(
            applicationContext,
            ComponentName(applicationContext, SesameComplicationDataSourceService::class.java),
        ).requestUpdateAll()
    }

    private companion object {
        const val TAG = "SesameResultListener"
    }
}

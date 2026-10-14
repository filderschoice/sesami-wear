package com.sesamiwear.mobile.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.sesamiwear.mobile.MainActivity
import com.sesamiwear.mobile.R
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.widget.SesameWidgetModelResolver
import com.sesamiwear.mobile.widget.SesameWidgetRepository
import com.sesamiwear.mobile.widget.WidgetCommandReceiver
import com.sesamiwear.mobile.widget.WidgetInProgressTracker
import com.sesamiwear.mobile.widget.WidgetUnlockConfirmActivity

/**
 * スマートフォンのクイック設定パネルの「Sesame」タイル（BL-240）。
 *
 * 表示と操作の判定は[QuickSettingsTileContent]が持ち、ここは保存値の読み出しと`Tile`への反映、
 * タップ時の起動だけを行う。施錠・状態取得はホーム画面ウィジェットと同じ[WidgetCommandReceiver]へ
 * ブロードキャストで渡し（`goAsync`で実行でき、重複抑止・振動・ウィジェットとウォッチへの反映が揃う）、
 * 解錠はウィジェットと同じ確認画面を開く。
 *
 * パネルが開いている間（`onStartListening`）に保存値から描き直す。操作の途中・完了時の描き直しは、
 * ウィジェットの再描画と同じ契機で[requestUpdate]から要求する。
 * Android依存の薄いアダプタのためユニットテスト対象外。
 */
class SesameQuickSettingsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        when (val tap = loadContent().tap) {
            QuickSettingsTileContent.Tap.OpenApp -> startAndCollapse(Intent(this, MainActivity::class.java))
            is QuickSettingsTileContent.Tap.ConfirmUnlock ->
                startAndCollapse(
                    WidgetUnlockConfirmActivity.createIntent(this, NO_WIDGET, tap.deviceUuid, tap.displayName),
                )
            is QuickSettingsTileContent.Tap.Run ->
                sendBroadcast(WidgetCommandReceiver.runCommandIntent(this, NO_WIDGET, tap.deviceUuid, tap.command))
            is QuickSettingsTileContent.Tap.Refresh ->
                sendBroadcast(WidgetCommandReceiver.refreshStatusIntent(this, NO_WIDGET, tap.deviceUuid))
            QuickSettingsTileContent.Tap.None -> Unit
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val content = loadContent()
        tile.label = content.label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = content.subtitle
        tile.contentDescription = "${content.label} ${content.subtitle}"
        tile.state =
            when (content.tileState) {
                QuickSettingsTileContent.TileState.ACTIVE -> Tile.STATE_ACTIVE
                QuickSettingsTileContent.TileState.INACTIVE -> Tile.STATE_INACTIVE
                QuickSettingsTileContent.TileState.UNAVAILABLE -> Tile.STATE_UNAVAILABLE
            }
        val iconRes =
            when (content.iconKind) {
                QuickSettingsTileContent.IconKind.LOCKED -> R.drawable.ic_shortcut_lock
                QuickSettingsTileContent.IconKind.UNLOCKED -> R.drawable.ic_qs_lock_open
            }
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.updateTile()
    }

    private fun loadContent(): QuickSettingsTileContent {
        val registered = SesameWidgetRepository.loadRegisteredDevices(this)
        val target = QuickSettingsTileContent.targetUuid(registered)
        val lockStateStore = SesameDeviceStores.lockState(this)
        val model =
            SesameWidgetModelResolver.resolve(
                assignedUuid = target,
                registeredDevices = registered,
                snapshotOf = { uuid -> lockStateStore.load(uuid) },
                isCommandInProgress = WidgetInProgressTracker.shared.isInProgress(target, registered),
            )
        return QuickSettingsTileContent.from(model)
    }

    /**
     * 画面を開いてパネルを閉じる。Android 14以上はPendingIntent版だけが使え、
     * それより前はIntent版を使う（Intent版はAndroid 14で非推奨になり、ターゲット34以上では例外になる）。
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startAndCollapse(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val TAG = "SesameQsTile"

        /** ウィジェットからの操作ではないことを表す（受け口はウィジェットのIDを操作に使わない）。 */
        private const val NO_WIDGET = AppWidgetManager.INVALID_APPWIDGET_ID

        /**
         * パネルのタイルの描き直しを要求する。パネルが閉じていれば次に開いたとき（`onStartListening`）に
         * 描かれるため、ここでの失敗は無視してよい（タイルが追加されていない端末では例外になることがある）。
         */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, SesameQuickSettingsTileService::class.java))
            }.onFailure { Log.w(TAG, "request listening failed: $it") }
        }
    }
}

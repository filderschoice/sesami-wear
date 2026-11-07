package com.sesamiwear.mobile.credentials

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sesamiwear.mobile.ble.SesameRouteNotificationStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * 経路が切り替わったときの通知を出すかどうかの設定（BL-190）。
 *
 * 2026-09-20のユーザー判断で、通知は出すが**利用者が上部バーの設定メニューから止められる**ように
 * した。通知には`POST_NOTIFICATIONS`（Android 13以上）が要るため、オンにする操作の中で
 * 権限も求める。権限が無い・端末側で通知を切られている場合は、オンでも実際には届かないため、
 * 副題でその旨を示す（[shortStatus]）。
 *
 * Compose画面のためユニットテスト対象外（保存の可否は`SesameRouteNotificationStoreTest`で検証済み）。
 */
internal class RouteNotificationState(
    val isEnabled: Boolean,
    /** 端末側の通知が有効で、実際に届く状態か。 */
    val isDeliverable: Boolean,
    /** 設定メニューの副題に出す1行。 */
    val shortStatus: String,
    val setEnabled: (Boolean) -> Unit,
    /** 通知の許可を求める（Android 13以上）。 */
    val requestPermission: () -> Unit,
)

@Composable
internal fun rememberRouteNotificationState(): RouteNotificationState {
    val context = LocalContext.current
    val store = remember { SesameRouteNotificationStore(SharedPreferencesKeyValueStore.forBleReachability(context)) }
    var enabled by remember { mutableStateOf(store.isEnabled()) }
    var deliverable by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        deliverable = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            deliverable = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return RouteNotificationState(
        isEnabled = enabled,
        isDeliverable = deliverable,
        shortStatus =
            when {
                !enabled -> STATUS_OFF
                deliverable -> STATUS_ON
                else -> STATUS_ON_BUT_BLOCKED
            },
        setEnabled = { next ->
            store.setEnabled(next)
            enabled = next
            if (next && !deliverable) request()
        },
        requestPermission = ::request,
    )
}

internal const val ROUTE_NOTIFICATION_TITLE = "経路が変わったとき"

private const val STATUS_ON = "オン"
private const val STATUS_OFF = "オフ"
private const val STATUS_ON_BUT_BLOCKED = "オン（端末の設定で通知が許可されていません）"
internal const val ROUTE_NOTIFICATION_DESCRIPTION =
    "Bluetoothで直接操作できなくなったとき、またはできるようになったときに通知します。" +
        "切り替わった瞬間だけ知らせるため、同じ状態が続く間は通知しません。"

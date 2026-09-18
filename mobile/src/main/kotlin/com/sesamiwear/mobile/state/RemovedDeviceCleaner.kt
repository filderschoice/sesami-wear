package com.sesamiwear.mobile.state

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.mobile.messaging.DataLayerBestEffort
import com.sesamiwear.mobile.widget.SesameWidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 資格情報を削除したデバイスについて、端末とウォッチに残る状態を消す（BL-160）。
 *
 * 資格情報そのものは`SesameCredentialsStore.remove`が消すが、それ以外の保存値は残っていた。
 * 同じuuidを登録し直すと、一度も取得していないのに削除前の施錠状態と失敗文言がそのまま表示される。
 * 消す対象は次の3つ。いずれもuuid・真偽値・時刻だけで機密情報は含まない。
 *
 * 1. [LockStateStore]のロック状態（最後に分かった施錠状態・取得時刻・直近の失敗）
 * 2. ホーム画面ウィジェットの対象デバイス割り当て
 *    （[com.sesamiwear.mobile.widget.WidgetDeviceAssignmentStore]）。
 *    未登録uuidの割り当ては`SesameWidgetModelResolver`が「タップして設定」へ倒すため表示は壊れないが、
 *    同じuuidを登録し直したときに利用者が設定し直していないウィジェットが黙って再び結び付くため消す。
 * 3. ウォッチへ同期済みのDataItem（[SesameWearProtocol.statusDataItemPath]）。
 *    残すとウォッチ側で同じ症状（再登録直後に削除前の状態が出る）が起きる。
 *
 * 保存先をつなぐだけのAndroid依存アダプタのためユニットテスト対象外
 * （各ストアの削除そのものは`LockStateStoreTest` / `WidgetDeviceAssignmentStoreTest`でテスト済み）。
 */
object RemovedDeviceCleaner {
    /**
     * [uuid]の残存状態を消す。端末内の保存値は同期的に消し、ウォッチのDataItemの削除だけは
     * [scope]で実行する（Data Layer APIはsuspend、かつ失敗しても呼び出し元を止めない）。
     */
    fun clean(
        context: Context,
        uuid: String,
        scope: CoroutineScope,
    ) {
        val appContext = context.applicationContext
        LockStateStore(SharedPreferencesKeyValueStore.forLockState(appContext)).remove(uuid)
        SesameWidgetRepository.assignmentStore(appContext).unassignDevice(uuid)
        scope.launch { deleteSyncedStatus(appContext, uuid) }
    }

    private suspend fun deleteSyncedStatus(
        appContext: Context,
        uuid: String,
    ) {
        DataLayerBestEffort.run(onFailure = { Log.w(TAG, "status delete skipped: statusCode=$it") }) {
            val uri =
                Uri.Builder()
                    .scheme(PATH_SCHEME)
                    .path(SesameWearProtocol.statusDataItemPath(uuid))
                    .build()
            Wearable.getDataClient(appContext).deleteDataItems(uri).await()
        }
    }

    private const val TAG = "RemovedDeviceCleaner"

    /** DataItemのURIスキーマ（`wear://<node>/<path>`。ホストを省くと全ノードが対象になる）。 */
    private const val PATH_SCHEME = "wear"
}

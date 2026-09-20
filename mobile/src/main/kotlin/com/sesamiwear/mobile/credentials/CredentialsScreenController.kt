package com.sesamiwear.mobile.credentials

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.messaging.SesameDeviceListSyncer
import com.sesamiwear.mobile.state.RemovedDeviceCleaner
import com.sesamiwear.mobile.widget.SesameWidgetRepository
import com.sesamiwear.mobile.widget.SesameWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 資格情報設定画面（[CredentialsSettingsScreen]）の保存・削除と、それに伴う同期をまとめる（BL-177）。
 *
 * 画面のComposableへ直接書くとdetektの`LongMethod`（上限60行）を超えるため、状態の持ち方と
 * 副作用をここへ寄せている。保存・削除の判定そのものは[CredentialsListEditor]（ユニットテスト対象）で、
 * ここはストアとData Layer・ウィジェットの呼び分けだけを持つAndroid依存のアダプタ。
 */
internal class CredentialsScreenController(
    private val context: Context,
    private val credentialsStore: SesameCredentialsStore,
    private val coroutineScope: CoroutineScope,
) {
    /** 画面に出す登録済みデバイス。保存・削除のたびにストアから読み直す。 */
    var devices by mutableStateOf(credentialsStore.loadAll())
        private set

    /**
     * 追加・編集の確定。既存uuidなら上書き、新規uuidなら追加になる（[CredentialsListEditor.upsert]）。
     * uuidを変更した編集でも、元の項目が重複して残らない（BL-100）。
     */
    fun save(
        editingUuid: String?,
        edited: SesameCredentials,
    ) {
        val updated =
            CredentialsListEditor.upsert(
                credentialsList = devices,
                editingUuid = editingUuid,
                edited = edited,
            )
        credentialsStore.saveAll(updated)
        devices = updated
        syncDeviceList(updated)
    }

    /**
     * 削除。資格情報だけでなく、そのデバイスの残存状態（ロック状態・ウィジェットの割り当て・
     * ウォッチのDataItem）も消す（[RemovedDeviceCleaner]、BL-160）。
     */
    fun delete(uuid: String) {
        credentialsStore.remove(uuid)
        RemovedDeviceCleaner.clean(context, uuid, coroutineScope)
        devices = credentialsStore.loadAll()
        syncDeviceList(devices)
    }

    /**
     * wear側は資格情報を持たない設計方針のため、Tile Configuration Activityでの
     * デバイス選択肢表示用にuuid/displayNameのみの一覧をDataClient経由で同期する（BL-052）。
     * ホーム画面ウィジェットは表示名・対象デバイスの有無が変わるため、あわせて再描画を要求する（BL-121）。
     * 1台以上の登録になった場合は、デモを割り当てていたウィジェットを未設定へ戻す（BL-123）。
     */
    private fun syncDeviceList(list: List<SesameCredentials>) {
        SesameWidgetRepository.assignmentStore(context).onRegisteredDevicesChanged(list.size)
        // ウォッチ同期が失敗してもウィジェットの再描画が飛ばないよう、2つを独立したコルーチンで実行する（BL-134）。
        coroutineScope.launch { SesameWidgetUpdater.updateAll(context) }
        coroutineScope.launch { SesameDeviceListSyncer(context).sync(list) }
    }
}

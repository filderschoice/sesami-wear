package com.sesamiwear.mobile.widget

import android.content.Context
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import com.sesamiwear.mobile.state.LockStateStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore

/**
 * ウィジェットの表示に必要な保存値（割り当て・登録済みデバイス・ロック状態）を読み、
 * [SesameWidgetModelResolver]へ渡す（BL-121）。保存先をつなぐだけのAndroid依存アダプタのため
 * ユニットテスト対象外（判定は[SesameWidgetModelResolver]でテスト済み）。
 * 登録済みデバイスは資格情報ストアから uuid と表示名だけを取り出し、apikey・secretKey は扱わない。
 */
object SesameWidgetRepository {
    fun loadModel(
        context: Context,
        appWidgetId: Int,
    ): SesameWidgetModel {
        val appContext = context.applicationContext
        val registeredDevices = loadRegisteredDevices(appContext)
        val assignedUuid = assignmentStore(appContext).assignedDeviceUuid(appWidgetId)
        val lockStateStore = LockStateStore(SharedPreferencesKeyValueStore.forLockState(appContext))
        return SesameWidgetModelResolver.resolve(
            assignedUuid = assignedUuid,
            registeredDevices = registeredDevices,
            lockStateOf = { uuid -> lockStateStore.load(uuid)?.isLocked },
            isCommandInProgress = WidgetInProgressTracker.shared.isInProgress(assignedUuid, registeredDevices),
        )
    }

    fun loadRegisteredDevices(context: Context): List<SesameDeviceSummary> =
        SesameCredentialsStore(EncryptedSharedPreferencesKeyValueStore.create(context.applicationContext))
            .loadAll()
            .map { SesameDeviceSummary(uuid = it.uuid, displayName = it.displayName) }

    fun assignmentStore(context: Context): WidgetDeviceAssignmentStore =
        WidgetDeviceAssignmentStore(SharedPreferencesKeyValueStore.forWidgetAssignments(context.applicationContext))
}

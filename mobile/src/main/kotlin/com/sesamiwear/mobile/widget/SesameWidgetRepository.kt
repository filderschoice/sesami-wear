package com.sesamiwear.mobile.widget

import android.content.Context
import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.mobile.state.SesameDeviceStores

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
        val lockStateStore = SesameDeviceStores.lockState(appContext)
        return SesameWidgetModelResolver.resolve(
            assignedUuid = assignedUuid,
            registeredDevices = registeredDevices,
            snapshotOf = { uuid -> lockStateStore.load(uuid) },
            isCommandInProgress = WidgetInProgressTracker.shared.isInProgress(assignedUuid, registeredDevices),
        )
    }

    fun loadRegisteredDevices(context: Context): List<SesameDeviceSummary> =
        SesameDeviceStores.credentials(context)
            .loadAll()
            .map { SesameDeviceSummary(uuid = it.uuid, displayName = it.displayName) }

    fun assignmentStore(context: Context): WidgetDeviceAssignmentStore = SesameDeviceStores.widgetAssignments(context)
}

package com.sesamiwear.mobile.state

import android.content.Context
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.mobile.credentials.EncryptedSharedPreferencesKeyValueStore
import com.sesamiwear.mobile.showcase.ShowcaseMode
import com.sesamiwear.mobile.widget.WidgetDeviceAssignmentStore

/**
 * 画面・ウィジェットが読み書きする保存先（登録済みデバイス・ロック状態・ウィジェットの割り当て）を開く（BL-212）。
 *
 * 通常は実物の保存先（資格情報は暗号化ストア、それ以外は非暗号化のSharedPreferences）を返す。
 * デバッグ版の撮影モード中だけは、撮影用の別ファイルを返す（[ShowcaseMode]）。
 * **モード中に何を操作しても、登録済みの資格情報と実物の状態へは書き込まれない。**
 * リリース版の[ShowcaseMode]は常にnullを返すスタブで、実物の保存先だけが使われる。
 *
 * 施錠/解錠を実行する`SesameDeviceCommandExecutorFactory`は本オブジェクトを使わず、実物の保存先を直接開く
 * （撮影モード中は実行口そのものが撮影用に差し替わるため、実物の実行口が撮影用の値を読むことは無い）。
 * 保存先を選ぶだけのAndroid依存アダプタのためユニットテスト対象外。
 */
object SesameDeviceStores {
    fun credentials(context: Context): SesameCredentialsStore {
        val appContext = context.applicationContext
        return SesameCredentialsStore(
            ShowcaseMode.credentialsKeyValueStore(appContext)
                ?: EncryptedSharedPreferencesKeyValueStore.create(appContext),
        )
    }

    fun lockState(context: Context): LockStateStore {
        val appContext = context.applicationContext
        return LockStateStore(
            ShowcaseMode.lockStateKeyValueStore(appContext) ?: SharedPreferencesKeyValueStore.forLockState(appContext),
        )
    }

    fun widgetAssignments(context: Context): WidgetDeviceAssignmentStore {
        val appContext = context.applicationContext
        return WidgetDeviceAssignmentStore(
            ShowcaseMode.widgetAssignmentsKeyValueStore(appContext)
                ?: SharedPreferencesKeyValueStore.forWidgetAssignments(appContext),
        )
    }
}

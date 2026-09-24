package com.sesamiwear.mobile.showcase

import android.content.Context
import com.sesamiwear.core.SesameCredentialsStore
import com.sesamiwear.core.SesameKeyValueStore
import com.sesamiwear.mobile.command.LockStateNotifier
import com.sesamiwear.mobile.command.SesameDeviceCommands
import com.sesamiwear.mobile.messaging.SesameStatusSyncer
import com.sesamiwear.mobile.state.LockStateStore
import com.sesamiwear.mobile.state.SharedPreferencesKeyValueStore
import com.sesamiwear.mobile.widget.SesameWidgetUpdater

/**
 * 撮影モード（BL-212）。**デバッグ版にだけ含まれる**（リリース版は`src/release`の同名スタブ）。
 *
 * 掲載スクリーンショットを撮るために、ウォッチ・ホーム画面ウィジェット・カード一覧へ撮影用のデバイスと
 * 状態を出す。モード中は次のとおり実物から切り離す。
 *
 * - 登録済みデバイス・ロック状態・ウィジェットの割り当ては、撮影用の別ファイルを読み書きする
 *   （`SesameDeviceStores`が差し替える）。**実物の資格情報と状態へは書き込まない。**
 * - 施錠/解錠・状態取得は[ShowcaseDeviceCommands]が受け、Sesame Web APIもBLEも呼ばない。
 * - 撮影用のAPIキー・秘密鍵は保存時にダミー値へ置き換える（[ShowcaseCredentialsKeyValueStore]）。
 *
 * モードの切り替えと撮影用の状態の書き換えは[ShowcaseSync]が行う。
 * 保存先はいずれも非暗号化のSharedPreferencesで、ダミー値しか持たない。
 */
object ShowcaseMode {
    private const val MODE_PREFS_FILE_NAME = "sesami_wear_showcase_mode"
    private const val KEY_ACTIVE = "active"
    private const val CREDENTIALS_PREFS_FILE_NAME = "sesami_wear_showcase_credentials"
    private const val LOCK_STATE_PREFS_FILE_NAME = "sesami_wear_showcase_lock_state"
    private const val WIDGET_ASSIGNMENTS_PREFS_FILE_NAME = "sesami_wear_showcase_widget_assignments"

    fun isActive(context: Context): Boolean = modePrefs(context).getBoolean(KEY_ACTIVE, false)

    fun credentialsKeyValueStore(context: Context): SesameKeyValueStore? =
        if (isActive(
                context,
            )
        ) {
            ShowcaseCredentialsKeyValueStore(prefsStore(context, CREDENTIALS_PREFS_FILE_NAME))
        } else {
            null
        }

    fun lockStateKeyValueStore(context: Context): SesameKeyValueStore? =
        if (isActive(context)) prefsStore(context, LOCK_STATE_PREFS_FILE_NAME) else null

    fun widgetAssignmentsKeyValueStore(context: Context): SesameKeyValueStore? =
        if (isActive(context)) prefsStore(context, WIDGET_ASSIGNMENTS_PREFS_FILE_NAME) else null

    fun commandsOrNull(context: Context): SesameDeviceCommands? {
        if (!isActive(context)) return null
        val appContext = context.applicationContext
        val devices = showcaseDevices(appContext)
        return ShowcaseDeviceCommands(
            loadDevices = devices::loadAll,
            lockStateStore = showcaseLockStateStore(appContext),
            notifier =
                LockStateNotifier(
                    local = { _, _ -> SesameWidgetUpdater.updateAll(appContext) },
                    watch = { uuid, snapshot -> SesameStatusSyncer(appContext).sync(uuid, snapshot) },
                ),
        )
    }

    /** 撮影用デバイスの保存値（施錠状態など）。撮影モード中でなくても撮影用の保存先から読む。 */
    fun showcaseLockStateStore(context: Context): LockStateStore =
        LockStateStore(prefsStore(context, LOCK_STATE_PREFS_FILE_NAME))

    private fun prefsStore(
        context: Context,
        fileName: String,
    ) = SharedPreferencesKeyValueStore(context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE))

    /** モードの保存値だけを書き換える。ウォッチ・ウィジェットの描き直しは[ShowcaseSync.setActive]が行う。 */
    internal fun writeActive(
        context: Context,
        active: Boolean,
    ) {
        modePrefs(context).edit().putBoolean(KEY_ACTIVE, active).commit()
    }

    private fun modePrefs(context: Context) =
        context.applicationContext.getSharedPreferences(MODE_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    /** 撮影モード中かどうかに関わらず、撮影用の登録済みデバイスの保存先を開く。 */
    fun showcaseDevices(context: Context): SesameCredentialsStore =
        SesameCredentialsStore(ShowcaseCredentialsKeyValueStore(prefsStore(context, CREDENTIALS_PREFS_FILE_NAME)))
}

package com.sesamiwear.mobile.ble

import android.os.Build

/**
 * 資格情報設定画面に出す、BLE直接操作の権限の案内（BL-153）。
 *
 * **権限は任意で、拒否されてもアプリは従来どおりWeb API経由で動く。** 文言はそのことを
 * どの状態でも明示する。API 30以下でだけ必要になる位置情報の許可については、
 * 近くのSesameを探すためだけに使い、位置情報の収集・送信は行わないことを併記する
 * （DESIGN.md「BLE直接操作の併用方針」論点4、およびデータセーフティ申告の説明と揃える）。
 *
 * 文言と状態の決定はAndroid非依存のためユニットテスト対象。実際の許可状況の問い合わせと
 * 権限要求そのものは[SesameBlePermissions]と画面側が担う。
 */
object SesameBlePermissionPrompt {
    /** 画面に出す案内の状態。 */
    enum class State {
        /** 必要な権限がすべて許可されている。 */
        GRANTED,

        /** まだ一度も要求していない。ボタンから要求できる。 */
        NOT_REQUESTED,

        /** 要求したが許可されなかった。以降は端末の設定画面から許可してもらう。 */
        DENIED,
    }

    /** 見出し。どの状態でも同じ。説明のダイアログの題名にもなる。 */
    const val TITLE = "近くのSesameをBluetoothで操作する（任意）"

    /** 説明のダイアログを閉じるだけのボタンの文言。 */
    const val DISMISS_LABEL = "あとで"

    /**
     * 資格情報設定画面へ常時出す1行。画面は縦スクロールしないため、長い説明は
     * [summary]としてダイアログ側へ回し、ここは1行に収める。
     */
    fun shortStatus(state: State): String =
        when (state) {
            State.GRANTED -> "Bluetooth：許可済み（近くにいるときは直接操作します）"
            State.NOT_REQUESTED -> "Bluetooth：未許可（インターネット経由で動作中）"
            State.DENIED -> "Bluetooth：許可されていません（インターネット経由で動作中）"
        }

    /**
     * [missingPermissions]が空なら[State.GRANTED]。空でない場合、一度でも要求済み
     * （[alreadyAsked]）なら[State.DENIED]、まだなら[State.NOT_REQUESTED]。
     */
    fun state(
        missingPermissions: List<String>,
        alreadyAsked: Boolean,
    ): State =
        when {
            missingPermissions.isEmpty() -> State.GRANTED
            alreadyAsked -> State.DENIED
            else -> State.NOT_REQUESTED
        }

    /**
     * [state]の説明文。[sdkInt]がAPI 30以下のときだけ、位置情報の許可についての補足を足す。
     */
    fun summary(
        state: State,
        sdkInt: Int,
    ): String =
        when (state) {
            State.GRANTED -> GRANTED_SUMMARY
            State.NOT_REQUESTED -> NOT_REQUESTED_SUMMARY + legacyLocationNote(sdkInt)
            State.DENIED -> DENIED_SUMMARY + legacyLocationNote(sdkInt)
        }

    /** [state]で出すボタンの文言。[State.GRANTED]ではボタンを出さないためnull。 */
    fun buttonLabel(state: State): String? =
        when (state) {
            State.GRANTED -> null
            State.NOT_REQUESTED -> "Bluetoothの利用を許可する"
            State.DENIED -> "端末の設定を開く"
        }

    /**
     * ボタンで権限要求のダイアログを出せるか。[State.DENIED]では要求しても表示されないため、
     * 端末の設定画面へ送る（画面側がこの値で分岐する）。
     */
    fun requestsPermission(state: State): Boolean = state == State.NOT_REQUESTED

    private fun legacyLocationNote(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.S) "" else LEGACY_LOCATION_NOTE

    private const val GRANTED_SUMMARY =
        "スマートフォンがSesameの近くにあるときは、Bluetoothで直接操作します。" +
            "このときはインターネットを経由せず、APIのリクエスト回数も消費しません。" +
            "近くにいないときは、これまでどおりインターネット経由で操作します。"

    private const val NOT_REQUESTED_SUMMARY =
        "Bluetoothの利用を許可すると、スマートフォンがSesameの近くにあるときは直接操作するようになり、" +
            "APIのリクエスト回数を消費しなくなります。" +
            "許可しなくても、これまでどおりインターネット経由で施錠・解錠と状態の取得ができます。"

    private const val DENIED_SUMMARY =
        "Bluetoothの利用は許可されていません。これまでどおりインターネット経由で操作します。" +
            "端末の設定から許可すると、Sesameの近くにいるときは直接操作するようになり、" +
            "APIのリクエスト回数を消費しなくなります。"

    private const val LEGACY_LOCATION_NOTE =
        "\nお使いのAndroidでは、近くのSesameを探すために位置情報の許可が必要です。" +
            "位置情報は近くの機器を探すためだけに使い、収集も送信もしません。"
}

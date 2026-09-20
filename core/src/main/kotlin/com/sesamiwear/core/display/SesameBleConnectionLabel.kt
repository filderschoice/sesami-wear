package com.sesamiwear.core.display

/**
 * Bluetooth（BLE）でSesameへ届いているかどうかの表示文言（BL-190）。
 *
 * BL-152の経路選択は「直近に到達できた実績があるときだけBLEを先に試す」という内部状態で動くが、
 * その状態は利用者からまったく見えなかった。2026-09-20の実機検証（BL-165）で、
 * 到達確認が実機の所要時間に対して短すぎてBLE経路が一度も選ばれていないことが判明し、
 * **利用者が「今どちらで動いているか」「Bluetoothが届いているか」を確認できる必要がある**
 * とユーザーが判断したため、状態そのものを画面へ出す。
 *
 * [SesameRouteLabel]が「最後に使った経路」を表すのに対し、こちらは「次にBLEを使えそうか」を表す。
 * 経路（実績）と到達性（見込み）は別物で、圏内でもまだ一度もBLEを使っていなければ経路は
 * インターネットのままになる。
 *
 * Android非依存のためユニットテスト対象。
 */
object SesameBleConnectionLabel {
    /** BLEの到達状況。 */
    enum class State {
        /** 直近の確認で届いた（次の操作はBLEを先に試す）。 */
        IN_RANGE,

        /** 確認したが届かなかった。 */
        OUT_OF_RANGE,

        /** 一度も確認していない、または権限が無く確認できない。 */
        UNKNOWN,
    }

    /** 未確認のときの文言。確認の契機（操作）が無いだけなので、失敗とは書かない。 */
    const val UNKNOWN_LABEL = "Bluetooth：未確認"

    /** 権限が無くBLEを使わない構成のときの文言。 */
    const val UNAVAILABLE_LABEL = "Bluetooth：使用しない設定"

    /**
     * [state]の1行。確認済みの場合は、いつ確認したかを[SesameStatusFreshness]と同じ相対表記で添える。
     *
     * @param lastCheckedAtEpochMillis 最後に到達確認を行った時刻。nullなら未確認。
     * @param nowEpochMillis 現在時刻。テストから固定できるよう引数で受ける。
     */
    fun label(
        state: State,
        lastCheckedAtEpochMillis: Long?,
        nowEpochMillis: Long,
    ): String {
        if (state == State.UNKNOWN || lastCheckedAtEpochMillis == null) return UNKNOWN_LABEL
        val freshness = SesameStatusFreshness.label(lastCheckedAtEpochMillis, nowEpochMillis)
        // 「たった今」だけは助詞を挟むと不自然になるため、そのまま続ける。
        val checked =
            if (freshness == SesameStatusFreshness.JUST_NOW_LABEL) "${freshness}確認" else "${freshness}に確認"
        val name = if (state == State.IN_RANGE) IN_RANGE_NAME else OUT_OF_RANGE_NAME
        return "Bluetooth：$name（$checked）"
    }

    /** 圏内の呼び名。 */
    const val IN_RANGE_NAME = "圏内"

    /** 圏外の呼び名。 */
    const val OUT_OF_RANGE_NAME = "圏外"
}

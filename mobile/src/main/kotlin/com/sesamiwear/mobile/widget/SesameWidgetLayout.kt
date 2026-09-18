package com.sesamiwear.mobile.widget

/**
 * ホーム画面ウィジェットの表示バリエーション（BL-128）。
 *
 * 第1段階（BL-121）はTile相当の1サイズ固定だったが、ホーム画面の1マスへ置きたい場合に大きすぎた。
 * 利用者がウィジェットをリサイズしたときに、入る情報量へ応じて出し分ける。
 * 複数台を横に並べる表示（4x1等）はwear側に無い機能になり、以降の表示変更で両方を追従させる必要が
 * 出るため採らない（2026-09-18、ユーザー確認済み）。
 */
enum class SesameWidgetLayout {
    /** 1マス（1x1）相当。状態アイコンと短い状態文言だけを出し、タップの挙動は[FULL]と同じ。 */
    COMPACT,

    /** Tile相当（4x2）。デバイス名・「変更」・状態・操作文言・最終取得時刻をすべて出す。 */
    FULL,
    ;

    companion object {
        /**
         * ウィジェットの表示領域（dp）から使うレイアウトを決める。
         *
         * しきい値は「左列（デバイス名と『変更』のチップ、[SesameWidget]の`LEFT_COLUMN_WIDTH_DP`=96dp）と
         * 状態表示を横に並べて成立する幅があるか」で決める。左列96dp＋間隔6dp＋状態表示に最低でも
         * 同程度の幅が要るため、200dpを境にする。高さは、状態アイコン・状態文言・最終取得時刻・
         * 操作文言の4行が入る必要があるため100dpを境にする。どちらかを下回れば[COMPACT]。
         */
        fun of(
            widthDp: Int,
            heightDp: Int,
        ): SesameWidgetLayout = if (widthDp >= FULL_MIN_WIDTH_DP && heightDp >= FULL_MIN_HEIGHT_DP) FULL else COMPACT

        const val FULL_MIN_WIDTH_DP = 200
        const val FULL_MIN_HEIGHT_DP = 100
    }
}

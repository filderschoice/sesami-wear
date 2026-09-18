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
         * 操作文言の4行が入る必要があるため140dpを境にする。どちらかを下回れば[COMPACT]。
         *
         * 高さの内訳（[SesameWidget]の定数と対応、行の高さは文字サイズの約1.35倍で見積もる）は、
         * 外周パディング8dp×2＝16dp、状態表示の内側パディング6dp×2＝12dp、
         * アイコン28sp≒38dp、状態文言16sp≒22dp、最終取得時刻・失敗文言11sp×2行≒30dp、
         * 操作文言13sp≒18dp で合計約136dp。当初は100dpとしていたが、
         * 4マス×1マス（Pixel 8 Pro + Nova Launcherで約128dp）でも[FULL]が選ばれ、
         * 操作文言が縦に見切れていた（BL-158）。余裕を見て140dpへ引き上げる。
         * 既定の配置（4x2）はどの端末でも140dp以上になるため[FULL]のままになる。
         */
        fun of(
            widthDp: Int,
            heightDp: Int,
        ): SesameWidgetLayout = if (widthDp >= FULL_MIN_WIDTH_DP && heightDp >= FULL_MIN_HEIGHT_DP) FULL else COMPACT

        const val FULL_MIN_WIDTH_DP = 200
        const val FULL_MIN_HEIGHT_DP = 140
    }
}

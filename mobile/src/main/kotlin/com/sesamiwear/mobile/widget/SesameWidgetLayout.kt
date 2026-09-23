package com.sesamiwear.mobile.widget

/**
 * ホーム画面ウィジェットの表示バリエーション（BL-128 / BL-174）。
 *
 * 第1段階（BL-121）はTile相当の1サイズ固定だったが、ホーム画面の1マスへ置きたい場合に大きすぎた。
 * 利用者がウィジェットをリサイズしたときに、入る情報量へ応じて出し分ける。
 * 複数台を横に並べる表示（4x1等）はwear側に無い機能になり、以降の表示変更で両方を追従させる必要が
 * 出るため採らない（2026-09-18、ユーザー確認済み）。
 */
enum class SesameWidgetLayout {
    /**
     * 1マス（1x1）相当。状態アイコンと短い状態文言だけを出し、タップの挙動は[FULL]と同じ。
     *
     * 縮小の下限は2マス×1マス（[MEDIUM]）へ引き上げたため（BL-174）、通常の操作でこの表示に
     * なることはない。**保険として残している**。`minResizeWidth`より狭い表示領域を渡す
     * ランチャーや、BL-174より前に1マスで置かれた既存のインスタンスが該当しうる。
     */
    COMPACT,

    /**
     * 2マス×1マス相当（BL-174）。左1マスにデバイス名と「◀ ▶」（対象デバイスの順送り、BL-175）、
     * 右1マスに状態アイコンと状態文言を出す。最終取得時刻・操作文言は高さが足りないため出さない。
     */
    MEDIUM,

    /** Tile相当（4x2）。「更新」・「変更」・デバイス名と経路・状態・操作文言・最終取得時刻をすべて出す。 */
    FULL,
    ;

    companion object {
        /**
         * ウィジェットの表示領域（dp）から使うレイアウトを決める。
         *
         * [FULL]のしきい値は「左列（デバイス名と『変更』のチップ、[SesameWidget]の
         * `LEFT_COLUMN_WIDTH_DP`=96dp）と状態表示を横に並べて成立する幅があるか」で決める。
         * 左列96dp＋間隔6dp＋状態表示に最低でも同程度の幅が要るため、200dpを境にする。
         * 高さは、デバイス名の帯と、状態アイコン・状態文言・最終取得時刻・操作文言の4行が入る必要があるため
         * 172dpを境にする。
         *
         * 高さの内訳（[SesameWidget]の定数と対応、行の高さは文字サイズの約1.35倍で見積もる）は、
         * 外周パディング8dp×2＝16dp、状態表示の内側パディング6dp×2＝12dp、
         * アイコン28sp≒38dp、状態文言16sp≒22dp、最終取得時刻・失敗文言11sp×2行≒30dp、
         * 操作文言13sp≒18dp で合計約136dp。当初は100dpとしていたが、
         * 4マス×1マス（Pixel 8 Pro + Nova Launcherで約128dp）でも[FULL]が選ばれ、
         * 操作文言が縦に見切れていた（BL-158）。余裕を見て140dpへ引き上げた。
         * BL-205で状態表示の上へデバイス名の帯（13sp≒18dp＋上下余白4dp×2＝約26dp）と間隔6dpを
         * 足したため合計約168dpとなり、同じく余裕を見て172dpとする。
         * 既定の配置（4x2）は一般的な端末で約250dpになるため[FULL]のままになる。
         *
         * [FULL]に届かない場合は幅だけで[MEDIUM]と[COMPACT]を分ける（BL-174）。
         * [MEDIUM_MIN_WIDTH_DP]はAndroidが定める「2マス分の最小幅」（70dp×2−30dp＝110dp）で、
         * `sesame_widget_info.xml`の`minResizeWidth`と同じ値にしている。
         * 高さは下限（`minResizeHeight`＝50dp）に達していればデバイス名・「◀ ▶」の2段が入るため、
         * ここでは見ない。
         */
        fun of(
            widthDp: Int,
            heightDp: Int,
        ): SesameWidgetLayout =
            when {
                widthDp >= FULL_MIN_WIDTH_DP && heightDp >= FULL_MIN_HEIGHT_DP -> FULL
                widthDp >= MEDIUM_MIN_WIDTH_DP -> MEDIUM
                else -> COMPACT
            }

        const val FULL_MIN_WIDTH_DP = 200
        const val FULL_MIN_HEIGHT_DP = 172

        /** 2マス分の最小幅（Androidの算出式 70dp×マス数−30dp）。`minResizeWidth`と同じ値。 */
        const val MEDIUM_MIN_WIDTH_DP = 110
    }
}

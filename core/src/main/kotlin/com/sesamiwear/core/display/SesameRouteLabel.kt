package com.sesamiwear.core.display

import com.sesamiwear.core.SesameRoutePolicy
import com.sesamiwear.core.SesameStatusRoute

/**
 * 経路（[SesameStatusRoute]）と経路の方針（[SesameRoutePolicy]）の表示文言（BL-167 / BL-168）。
 *
 * BL-152の当初方針は「利用者は経路を意識しない」だったが、BLEで届かなかったときに利用者が
 * 原因を切り分けられないという問題があり、2026-09-19のユーザー判断で**経路を見せる**方針へ変更した。
 *
 * 表示できる面積が大きく違うため、2通りの表し方を用意する。
 *
 * - [icon]: Tile・Complication向け。**行を増やさず**既存の1行へ前置する
 *   （表示余白が無く、過去に文言が収まらず省略された事例がある。BL-102 / BL-104 / BL-158）。
 * - [name]: スマートフォンの画面向け。アイコンだけでは意味が伝わりにくいため語で書く。
 *
 * スマートフォン側（アプリ画面・ホーム画面ウィジェット）は絵文字ではなくMaterial Symbolsの
 * ベクターアイコンを使う（BL-176）。ウォッチ側（Tile・Complication）は、テキストへ画像を
 * 埋め込めない面があるため絵文字のままにしている。
 *
 * wearとmobileで食い違わせないよう、両者が参照できる`core`に置く（`SesameTileContent`と同方針）。
 * Android非依存のためユニットテスト対象。
 */
object SesameRouteLabel {
    /**
     * Bluetoothで直接操作した経路のアイコン（「直接つながっている」）。
     *
     * 当初は📶だったが、携帯電話の電波強度として広く使われているため、Bluetoothでの直接操作を
     * 表すものとして読み取れないという指摘があり🔗へ変更した（BL-173、2026-09-20）。
     * Unicodeに「Bluetooth」の絵文字は無く、ロゴはルーン文字の合字のため絵文字フォントに
     * 含まれない（端末によっては豆腐になる）。搭載率の高いEmoji 1.0の範囲から選んでいる。
     */
    const val ICON_BLE = "🔗"

    /**
     * インターネット（Sesame Web API）経由の経路のアイコン。
     *
     * 当初は☁だったが、[ICON_BLE]と対で分かりやすさを揃えるため🌐へ変更した（BL-173）。
     */
    const val ICON_WEB_API = "🌐"

    /**
     * [route]のアイコン。経路が分からない（一度も操作・取得していない）場合はnullを返し、
     * 呼び出し側は何も前置しない。
     */
    fun icon(route: SesameStatusRoute?): String? =
        when (route) {
            SesameStatusRoute.BLE -> ICON_BLE
            SesameStatusRoute.WEB_API -> ICON_WEB_API
            null -> null
        }

    /**
     * [label]の先頭へ[route]のアイコンを付けた文字列。経路が分からなければ[label]をそのまま返す。
     * 区切りの空白は入れない（1文字でも幅を節約するため）。
     */
    fun withIcon(
        route: SesameStatusRoute?,
        label: String,
    ): String = icon(route)?.let { "$it$label" } ?: label

    /** スマートフォンの画面で使う経路の呼び名。経路が分からない場合は[UNKNOWN_ROUTE_NAME]。 */
    fun name(route: SesameStatusRoute?): String =
        when (route) {
            SesameStatusRoute.BLE -> "Bluetooth"
            SesameStatusRoute.WEB_API -> "インターネット"
            null -> UNKNOWN_ROUTE_NAME
        }

    /** 設定画面で選択肢として並べる方針の名前。 */
    fun policyLabel(policy: SesameRoutePolicy): String =
        when (policy) {
            SesameRoutePolicy.AUTO -> "自動（Bluetooth優先）"
            SesameRoutePolicy.WEB_API_ONLY -> "常にインターネット経由"
        }

    /** 選択肢の説明。選ぶと何が変わるかを1文で示す。 */
    fun policyDescription(policy: SesameRoutePolicy): String =
        when (policy) {
            SesameRoutePolicy.AUTO ->
                "セサミの近くにいるときはBluetoothで直接操作し、届かなければインターネット経由へ切り替えます。" +
                    "Bluetoothで操作できた分はAPIのリクエスト回数を消費しません。"
            SesameRoutePolicy.WEB_API_ONLY ->
                "常にインターネット経由で操作します。Bluetoothの探索を行わないぶん動作が一定になりますが、" +
                    "操作のたびにAPIのリクエスト回数を消費します。"
        }

    /**
     * 複数デバイスを1つの表示へ集約する（「全デバイス」対象、BL-071）ときの経路。
     *
     * 全デバイスが同じ経路で、かつ1台以上分かっている場合だけその経路を返す。
     * 経路が混在している場合・1台でも分かっていない場合はnull（アイコンを出さない）。
     * 混在しているのに片方のアイコンを出すと、出ていない側のデバイスについて誤解を与えるため。
     */
    fun commonRoute(routes: List<SesameStatusRoute?>): SesameStatusRoute? = routes.distinct().singleOrNull()

    /**
     * Bluetoothで届かず、インターネット経由へ切り替えたときにスマートフォンで出す文言（BL-168）。
     *
     * 切り替えが起きたことに利用者が気づけないと、「Bluetoothにしたのに回数が減らない」という
     * 状況の原因を切り分けられない。まだWeb APIの呼び出しは終わっていない時点で出すため、
     * 完了形ではなく「操作します」と書く。
     */
    const val FALLBACK_MESSAGE = "Bluetoothで届かないため、インターネット経由で操作します"

    /**
     * インターネット経由から、Bluetoothでの直接操作へ戻ったときの文言（BL-190）。
     *
     * 利用者の関心は「回数を消費しない経路へ戻ったか」なので、戻ったことも伝える。
     * 切り替わった瞬間だけ出す（毎回の操作では出さない。`SesameRouteChangeTracker`）。
     */
    const val RECOVERED_MESSAGE = "Bluetoothで直接操作できるようになりました"

    /** 経路の切り替わりを知らせる通知の見出し（BL-190）。 */
    const val ROUTE_CHANGE_TITLE = "操作の経路が変わりました"

    /** [route]へ切り替わったことを知らせる本文。 */
    fun routeChangeMessage(route: SesameStatusRoute): String =
        when (route) {
            SesameStatusRoute.BLE -> RECOVERED_MESSAGE
            SesameStatusRoute.WEB_API -> FALLBACK_MESSAGE
        }

    /** 経路が分からないときの呼び名。 */
    const val UNKNOWN_ROUTE_NAME = "未取得"
}

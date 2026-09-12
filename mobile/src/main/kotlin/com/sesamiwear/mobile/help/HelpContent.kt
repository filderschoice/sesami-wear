package com.sesamiwear.mobile.help

/**
 * ヘルプ内の外部リンク。[label]をタップすると[url]をブラウザで開く。
 */
data class HelpLink(
    val label: String,
    val url: String,
)

/**
 * ヘルプの1項目。[title]がメニューへ並び、選ぶと[paragraphs]と[link]が表示される。
 */
data class HelpTopic(
    val id: String,
    val title: String,
    val paragraphs: List<String>,
    val link: HelpLink? = null,
)

/**
 * mobile側ヘルプの文言（BL-113）。Android非依存のためユニットテスト対象。
 *
 * 従来のヘルプは値の取得方法だけを説明する単一のダイアログで、資格情報が未登録でも
 * ウォッチ側でデモ（BL-109）を操作できることに気づく導線が無かった。取得方法の説明は残したまま、
 * 「値の取得方法」「デモで試す」「登録後の使い方」の3項目から選ぶメニュー形式へ拡張し、
 * 資格情報を用意する前後のどちらの利用者も、次に何をすればよいかを追えるようにする。
 */
object HelpContent {
    /** SESAME Biz 開発者ページ。uuid・apikey・secretKeyはいずれもここから取得する（BL-059）。 */
    const val SESAME_BIZ_DEVELOPER_URL = "https://biz.candyhouse.co/biz/developer"

    /** ヘルプボタンから開くメニューの見出し。 */
    const val MENU_TITLE = "ヘルプ"

    val credentials =
        HelpTopic(
            id = "credentials",
            title = "値の取得方法",
            paragraphs =
                listOf(
                    "uuid・apikey・secretKeyは biz.candyhouse.co（SESAME Biz 開発者ページ）で確認できます。",
                    "secretKeyは16進数32文字です。Sesameアプリの「鍵をシェア」QRコードの値ではありません。",
                    "3つの入力欄は半角文字のみを受け付けます。全角で入力した英数字は自動的に半角へ" +
                        "変換され、値に現れない文字は取り除かれます。",
                ),
            link = HelpLink(label = "SESAME Biz 開発者ページを開く", url = SESAME_BIZ_DEVELOPER_URL),
        )

    val demo =
        HelpTopic(
            id = "demo",
            title = "Sesameが無くてもデモで試す",
            paragraphs =
                listOf(
                    "Sesameを1台も登録していない間は、ウォッチ側で「デモ（体験用）」を選べます。" +
                        "実際の鍵は動かさず、タイルとコンプリケーションの操作感だけを確認できます。",
                    "1. ウォッチの文字盤を左右にスワイプしてタイル一覧を開き、" +
                        "「＋」からSesami Wearのタイルを追加します。",
                    "2. タイルをタップして設定画面を開き、「デモ（体験用）」を選びます。",
                    "3. タイル右側をタップすると解錠、もう一度タップすると施錠へ戻ります。" +
                        "解錠時は確認画面を挟み、完了すると振動でお知らせします。",
                    "4. 文字盤のコンプリケーションにも、同じデモの状態を表示できます。",
                    "デモ中はスマートフォンとの通信もSesame APIへの通信も行いません。" +
                        "このアプリへSesameを1台でも登録すると、デモの選択肢は消え、登録したSesameが対象になります。",
                ),
        )

    val afterRegistration =
        HelpTopic(
            id = "after-registration",
            title = "登録後のウォッチでの使い方",
            paragraphs =
                listOf(
                    "Sesameを登録すると、ウォッチのタイルとコンプリケーションの操作対象として選べるようになります。",
                    "1. タイル左下の「変更」をタップして操作対象を選びます。" +
                        "2台以上登録している場合は「全デバイス」も選べます。",
                    "2. タイル右側をタップすると施錠・解錠を実行します。解錠は確認画面を挟みます。",
                    "3. タイル左上のデバイス名をタップすると、最新の状態を取り直します。",
                    "施錠・解錠はこのスマートフォンを経由して実行されます。" +
                        "ウォッチとスマートフォンが接続されていないときは、タイルに「スマホ未接続」と表示されます。",
                ),
        )

    /** メニューへ並べる順。資格情報を用意する前の利用者が迷わないよう、取得方法・デモ・使い方の順にする。 */
    val topics: List<HelpTopic> = listOf(credentials, demo, afterRegistration)
}

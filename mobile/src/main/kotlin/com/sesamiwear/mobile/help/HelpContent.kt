package com.sesamiwear.mobile.help

/**
 * ヘルプ内の外部リンク。[label]をタップすると[url]をブラウザで開く。
 */
data class HelpLink(
    val label: String,
    val url: String,
)

/**
 * ヘルプの1項目。[title]がメニューへ並び、選ぶと[paragraphs]と[links]が表示される。
 * [links]は0個以上。1項目から複数の公式ページへ誘導する必要が出たため、単一の`link`から
 * リストへ変更した（BL-144）。
 */
data class HelpTopic(
    val id: String,
    val title: String,
    val paragraphs: List<String>,
    val links: List<HelpLink> = emptyList(),
)

/**
 * mobile側ヘルプの文言（BL-113）。Android非依存のためユニットテスト対象。
 *
 * 従来のヘルプは値の取得方法だけを説明する単一のダイアログで、資格情報が未登録でも
 * ウォッチ側でデモ（BL-109）を操作できることに気づく導線が無かった。取得方法の説明は残したまま、
 * 「値の取得方法」「デモで試す」「登録後の使い方」の3項目から選ぶメニュー形式へ拡張し、
 * 資格情報を用意する前後のどちらの利用者も、次に何をすればよいかを追えるようにする。
 * ホーム画面ウィジェット（BL-121〜BL-123）の追加に伴い「ホーム画面ウィジェットの使い方」を加え、
 * デモの説明をウォッチに限らない記述へ直した（BL-124）。
 * Web APIの月間リクエスト上限へ到達してアプリ全体が機能停止する事象（BL-141）を受け、
 * 上限の存在と確認先を説明する「APIのリクエスト回数の上限」を加えた（BL-144）。
 */
object HelpContent {
    /** SESAME Biz 開発者ページ。uuid・apikey・secretKeyはいずれもここから取得する（BL-059）。 */
    const val SESAME_BIZ_DEVELOPER_URL = "https://biz.candyhouse.co/biz/developer"

    /**
     * APIキーの取得手順を説明したCANDY HOUSE公式の記事（BL-144）。
     * 2026-09-18に認証なしで開けることを確認済み。記事のURLは日本語のパスを持つため、
     * `Uri.parse`がそのまま扱えるようパーセントエンコード済みの形で保持する。
     *
     * Web APIのリファレンス（`doc.candyhouse.co/ja/SesameAPI/`）は同日時点でGitHub Pagesの
     * 認証へリダイレクトされ、認証なしでは開けないためリンクとして採用しない。
     */
    const val SESAME_API_KEY_GUIDE_URL =
        "https://jp.candyhouse.co/blogs/how-to/" +
            "api%E3%82%AD%E3%83%BC%E5%8F%96%E5%BE%97%E6%96%B9%E6%B3%95" +
            "%E3%81%A8%E3%82%BB%E3%82%B5%E3%83%9Fid%E3%81%AE%E7%A2%BA%E8%AA%8D%E6%96%B9%E6%B3%95"

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
            links =
                listOf(
                    HelpLink(label = "SESAME Biz 開発者ページを開く", url = SESAME_BIZ_DEVELOPER_URL),
                    HelpLink(label = "APIキーの取得方法（公式記事）を開く", url = SESAME_API_KEY_GUIDE_URL),
                ),
        )

    val demo =
        HelpTopic(
            id = "demo",
            title = "Sesameが無くてもデモで試す",
            paragraphs =
                listOf(
                    "Sesameを1台も登録していない間は、ウォッチのタイルとスマートフォンのホーム画面ウィジェットで" +
                        "「デモ」を選べます。実際の鍵は動かさず、操作感だけを確認できます。",
                    "ウォッチで試す場合: 文字盤を左右にスワイプしてタイル一覧を開き、「＋」からSesami Wearの" +
                        "タイルを追加します。タイルをタップして「デモ」を選び、タイル右側をタップすると解錠" +
                        "（確認画面あり）、もう一度タップすると施錠へ戻ります。完了すると振動でお知らせします。" +
                        "文字盤のコンプリケーションにも、同じデモの状態を表示できます。",
                    "スマートフォンで試す場合: ホーム画面にSesami Wearのウィジェットを追加し、選択画面で" +
                        "「デモ」を選びます。ウィジェット右側のタップで、タイルと同じように施錠・解錠を試せます。",
                    "デモ中はSesame APIへの通信を行いません。ウォッチのデモとスマートフォンのデモは、" +
                        "それぞれの端末の中だけで状態を持ち、互いに連動しません。",
                    "このアプリへSesameを1台でも登録すると、デモの選択肢は消え、登録したSesameが対象になります。" +
                        "デモを選んでいたウィジェットは「タップして設定」に戻ります。",
                ),
        )

    val apiLimit =
        HelpTopic(
            id = "api-limit",
            title = "APIのリクエスト回数の上限",
            paragraphs =
                listOf(
                    "Sesameの操作にはCANDY HOUSEのWeb APIを使います。このAPIには1か月あたりの" +
                        "リクエスト回数の上限があり、上限に達すると状態の取得も施錠・解錠もすべて失敗します。",
                    "上限に達すると、このアプリの表示は「認証エラー」になります。APIキーの入力内容が" +
                        "正しくても同じ表示になるため、両方の可能性を確認してください。",
                    "当月の利用状況と上限は、SESAME Biz（biz.candyhouse.co）にログインして確認できます。" +
                        "上限は契約している内容によって変わるため、アプリからは確認できません。",
                    "上限に達した場合の対処は、翌月のカウンタのリセットを待つか、CANDY HOUSEへ" +
                        "上限の引き上げを問い合わせるかのいずれかです。",
                    "このアプリは0.12.0から、状態の自動取得を行いません。リクエストを送るのは、" +
                        "施錠・解錠を実行したときと、デバイス名をタップして状態を取り直したときだけです。",
                ),
            links = listOf(HelpLink(label = "SESAME Biz を開く", url = SESAME_BIZ_DEVELOPER_URL)),
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

    val widget =
        HelpTopic(
            id = "widget",
            title = "ホーム画面ウィジェットの使い方",
            paragraphs =
                listOf(
                    "スマートフォンのホーム画面から、ウォッチのタイルと同じ操作で施錠・解錠できます。" +
                        "ウォッチを持っていなくても使えます。",
                    "1. ホーム画面の空いている場所を長押しし、「ウィジェット」からSesami Wearを選んで配置します。",
                    "2. 選択画面が開くので、操作するSesameを選びます。2台以上登録している場合は" +
                        "「全デバイス」も選べます。",
                    "3. ウィジェット右側をタップすると施錠・解錠を実行します。施錠はすぐに実行し、" +
                        "解錠は確認画面を挟みます。実行中は「通信中...」と表示されます。",
                    "4. 左上のデバイス名をタップすると最新の状態を取り直し、左下の「変更」で操作対象を選び直せます。",
                    "ウィジェットとウォッチのどちらで操作しても、もう一方の表示も追随します。" +
                        "Sesameを削除したときなど対象がなくなると、「タップして設定」と表示されます。",
                ),
        )

    /**
     * メニューへ並べる順。資格情報を用意する前の利用者が迷わないよう、取得方法・デモ・使い方の順にし、
     * 使い方はウォッチ・ホーム画面ウィジェットの順に並べる。
     * APIのリクエスト上限は値の取得方法と同じ「資格情報まわり」の話のため、その直後へ置く（BL-144）。
     */
    val topics: List<HelpTopic> = listOf(credentials, apiLimit, demo, afterRegistration, widget)
}

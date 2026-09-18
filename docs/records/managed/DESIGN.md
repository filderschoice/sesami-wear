<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用設計記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.design.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
# 実装用プロンプト設計書

## 目的

- 本文書は、Sesami Wear（Pixel WatchからSesame 5スマートロックを操作するAndroid/Wear OSアプリ）を
  同一要件で再実装させるための最新版プロンプトである。実装済み機能・設計意図・制約・品質観点を
  現在の実装状態に統合して記載する（`docs/records/spec/FORMAT.md`の規定により、個々のイテレーションの
  試行錯誤・やり取りの経過は積み上げず、最終形のみを保持する。経過の詳細は`EXECUTE.md`を参照）。

## 対象システム概要

- 対象: Android（スマホ側）+ Wear OS（Watch側）の構成。`mobile`と`wear`は同一の`applicationId`
  （`com.sesamiwear.mobile`）を共有する独立した2つのapplicationモジュールで、それぞれ別のAABと
  してビルドし、Google Playの別トラックへ配信する（BL-090）。CANDY HOUSE Sesame 5 + Hub 3の
  クラウドAPI（`https://app.candyhouse.co/api/sesame2/{uuid}`）経由で施錠/解錠・状態取得を行う。
  登録済みの複数Sesameデバイス（3〜5台程度を想定）を1つのアプリから個別または一括で操作できる。
  操作手段はウォッチのTile（表示はComplicationも）と、スマートフォンのホーム画面ウィジェット
  （BL-121〜BL-124）の2つで、ウィジェットはウォッチを持たない利用者も使える。
- 前提環境: JDK 17、Android SDK（compileSdk/targetSdk 36、build-tools 36.0.0）、Gradle 8.13
  （Gradle Wrapper経由）。詳細は `CLAUDE.md`「本リポジトリの品質ゲート定義」段階Bを参照。

## 実装済み機能要件

### 暗号・署名

- `core.crypto.AesCmac`: RFC 4493準拠のAES-CMAC実装（BL-002）。`javax.crypto.Cipher`
  （AES/ECB/NoPadding）でAES-128単一ブロック暗号化を行い、サブキー生成・メッセージのブロック分割・
  最終ブロックのパディング/XORをRFC仕様どおりに実装。RFC 4493 Appendix Aの公開テストベクタ4件と
  鍵長不正時の異常系1件で検証済み。Sesame非依存の汎用CMAC実装であり、Sesame固有のバイト列組み立ては
  呼び出し側（`SesameCommandSigner`）が担う。
- `core.api.SesameCommandSigner`（internal）: 施錠/解錠コマンドの署名生成（BL-004）。現在のUnix
  タイムスタンプ（秒、Int）を4バイト・リトルエンディアンにエンコードし、そのうちインデックス1〜3
  （先頭バイトを除いた3バイト、約256秒単位に丸められたタイムスタンプ）をメッセージとしてAES-CMAC
  （secretKeyを鍵）を計算し、その結果16バイト全体を32文字のhex文字列化したものが`sign`
  （CMAC出力全体をそのまま使う。先頭バイトのみを使う実装ではない点に注意）。pycryptodome
  （`CMAC.new(key, ciphermod=AES)`）による独立計算と一致することを固定タイムスタンプのテストで
  確認済み（鍵はRFC 4493のダミー鍵、実資格情報ではない）。

### Sesame APIクライアント

- `core.api.SesameApiClient`: 状態取得（GET）・施錠/解錠（POST）を実装（BL-003, BL-004, BL-012）。
  OkHttp + kotlinx.serialization.jsonでx-api-keyヘッダー付きリクエストを送信する。
  - GET: `SesameStatus`（batteryVoltage/position/CHSesame2Status。isInLockRange/isInUnlockRangeは
    CHSesame2Statusからの計算プロパティ）へデコード。
  - POST（`SesameCommand`: LOCK=82 / UNLOCK=83 / TOGGLE=88 / CLICK=89）: エンドポイントは
    `POST {OFFICIALAPI_URL}/{uuid}/cmd`。リクエストボディ（JSON）は`cmd`・`history`（任意の文字列
    タグをBase64エンコード、履歴に残る）・`sign`（`SesameCommandSigner`参照）。
  - 失敗は種類を問わず`core.api.SesameApiException`へ正規化して送出する（BL-133）。HTTP非成功に加えて、
    通信そのものの失敗（`IOException`。圏外・タイムアウト・名前解決失敗）、想定外の応答本文
    （`SerializationException`）、接続先URLが不正な場合（`IllegalArgumentException`）を`asApiCall`が包む。
    呼び出し側（`SesameCommandHandler.execute`・`SesameDeviceCommandExecutor.fetchIsLocked`）は
    `SesameApiException`だけを捕捉するため、正規化前は通信エラーが素通りしてコルーチンから漏れ、
    プロセスが落ちる経路になっていた。例外メッセージには原因例外の型名だけを載せる（uuidを含むURLや
    応答内容をログへ流さないため）。原因例外は`cause`として保持する。
  - `SesameApiException`はHTTPのエラー応答に由来する場合だけ`httpStatusCode`を持つ（BL-139）。
    通信失敗・解析失敗ではnullで、呼び出し側が「認証エラー・上限超過（401 / 403 / 429）」と
    「圏外などの通信失敗」を区別するための唯一の手がかりになる。メッセージへは応答本文を載せない
    （従来は`Sesame API error: HTTP 403 - {本文}`の形で本文を含んでおり、メッセージがログへ流れると
    応答内容が露出しうるため。`rules/guardrails-unified.v1.md` 3.3）。
  - 失敗のログ文言は`core.api.SesameApiFailureLog.describe`が組み立てる（BL-139）。出力は
    `status failed: HTTP 403` / `lock failed: IOException` のように「どの操作
    （`core.api.SesameApiOperation`: STATUS / LOCK / UNLOCK）が」「どの種類の失敗で」落ちたかの1行だけで、
    apikey・secretKey・uuid・URL・応答本文は含めない。理由は`httpStatusCode`があればステータスコード、
    無ければ原因例外の型名を使う（`withContext`をまたぐ例外はkotlinx.coroutinesが複製するため、
    `cause`を最内までたどってから型名を取る）。含めてはならない値を埋め込んだ例外を渡すテストで、
    それらが出力へ現れないことを検証している。
  - 既定の`OkHttpClient`は接続3秒・読み書き3秒・呼び出し全体6秒のタイムアウトを持ち、プロセス内で
    共有する1インスタンス（BL-133 / BL-137）。ウィジェットのタップはBroadcastReceiverの`goAsync`で実行し、
    Glanceの`actionSendBroadcast`が`FLAG_RECEIVER_FOREGROUND`を付けるため、実行時間の制限は
    約10秒（バックグラウンド受信の60秒ではない）。OkHttpの既定（接続・読み書きとも10秒）では間に合わず、
    実機でANRによる強制終了が起きることを確認したため、制限内へ収まる値にしている（BL-137）。
  - MockWebServerを用いた単体テストで、レスポンスパース（施錠中/解錠中/未知フィールドの無視）・
    リクエストボディ・HTTPメソッド・パス・ヘッダー・異常系の例外送出を検証済み。
  - 上記フィールド構成・署名仕様は参考実装pysesame3
    (<https://github.com/mochipon/pysesame3>)のソースコード（2026-08-19時点mainブランチ、
    `cloud.py`/`const.py`）を読んで判明した内容であり、CANDY HOUSE公式ドキュメントそのものは
    未参照だが、実機（Sesame 5 + Hub 3）でのGET状態取得・POST施錠/解錠の疎通確認が完了し、
    SesameStatusのデコードエラーが発生しないことを確認済み（BL-010、人手検証）。
  - 実機調査（BL-139 / BL-141、2026-09-18、Pixel 8 Pro + Play版0.11.0）: ウィジェットの状態取得が
    常に「状態不明」になる事象を調査し、原因が**Sesame API側のapikey無効化（HTTP 403）**であることを
    確認した。応答本文は`{"Message":"User is not authorized to access this resource with an explicit
    deny in an identity-based policy"}`で、出鱈目なapikeyでの応答と完全に一致する（ヘッダー無しは401）。
    リクエストは実際に送信されており（無操作時は送受信0バイト、タップ時は送信約3KB・受信約8〜12KB）、
    応答は約240msで返るためタイムアウトではない。uuidは36文字・大文字で形式は正常。
    `mapping.txt`上で`SesameStatus` / `$$serializer` / `$Companion`はいずれも非難読化のまま残存して
    おり、R8によるkotlinx.serializationの破壊でもない。**アプリの実装は正常**で、修正は不要。
  - ただしこの調査で、リリースビルドでは失敗理由がどこにも残らないことが判明した。当時の
    `SesameDeviceCommandExecutor.fetchIsLocked`は`SesameApiException`を捕捉してnullを返すだけで、
    ログ出力を行わなかった。`Log.d`はproguard-rules.proの`-assumenosideeffects`で除去される（BL-083）。
    Play App Signingのため配布済みのリリース版へ後から診断ログを足すこともできない
    （インストール済みAPKの署名は`CN=Android, O=Google Inc.`で、アップロード鍵では上書き更新不可。
    アンインストールすると保存済みの資格情報が消える）。原因特定にはデバッグ版（BL-131の併存
    インストール）へ実資格情報を入力して一時的な診断ログを仕込む必要があった。
    この反省からBL-139で最低限の失敗ログをリリースビルドへ最初から含めるようにした（対応済み）。
    認証エラーと未取得の表示上の区別はBL-140として起票済み。
  - 403の直接の原因は、当月のAPIリクエスト数が上限（1000回）に達してアカウントのAPIキーが
    拒否されたことである可能性が高い（biz.candyhouse.coのサイト上からのリクエストも同時に
    通らなくなっていた）。上限到達時の応答がHTTP 429ではなく403である点はCANDY HOUSE側の実装に
    よるものと推測しており未確認。
  - **自動状態取得は廃止した（BL-142、対応済み）。** 以前は
    `wear.tile.SesameTileStateResolver.requestStatusIfStale`が、保存済みスナップショットが
    `STATUS_STALE_THRESHOLD_MILLIS`（30秒）より古ければ`PATH_STATUS_REQUEST`を送っていた。これは
    Tileの描画のたびに評価され、`wear.complication.SesameComplicationDataSourceService`も同じ
    解決処理を呼ぶためComplicationの更新でも発生し、対象が「全デバイス」なら1回の描画で登録台数ぶん
    飛んでいた。mobile側の`CommandDebouncer`は状態取得を対象外（施錠/解錠のみ）で、
    `SesameDeviceCommandExecutor.refreshStatus`にも最小間隔が無く、経路のどこにも抑制が無かった。
  - 消費ペースの見積もり（設計値からの算出、BL-142）。実機での実測ではなく、確定している設定値から
    上限値を求めた。Complicationのマニフェスト`UPDATE_PERIOD_SECONDS`は600秒のため定期更新は
    **144回/日/枠**。鮮度閾値30秒はこの間隔より常に短いため、更新のたびに必ず状態取得が飛ぶ。
    登録2台で「全デバイス」を対象にすると**288回/日 = 約8,600回/月**となり、Complication枠1つだけで
    月間上限1000回（= **約33回/日**）を8倍以上超過する。Tileの描画ぶんはこれに上乗せされる。
    実機での1日サンプルより、この算出のほうが上限の見積もりとして確実で、実機・実資格情報も要らない。
  - 対応後の消費は**利用者が明示的にタップした回数だけ**になる。状態が更新される契機は
    (1) 施錠/解錠の成功（APIのGETを伴わず、送ったコマンドの意図した状態を保存する）、
    (2) Tileのデバイス名チップのタップ（`SesameStatusRefreshActivity`）、
    (3) ホーム画面ウィジェットのデバイス名のタップ、の3つだけ。
    代償として表示は最後に分かった状態を出し続けるため、その古さを
    `core.display.SesameStatusFreshness`の文言として表示へ添える（下記「状態の鮮度表示」）。
    月間消費回数のカウントと表示はBL-147、タップ連打時の状態取得の重複抑止はBL-148として起票済み。
    エラー後のバックオフ（BL-143）は、抑制対象だった自動取得が消えたため対象消滅として閉じた
    （2026-09-18、ユーザー確認済み。失敗の種類を利用者へ伝える側面はBL-140が引き取る）。

### 状態の鮮度表示と失敗の区別

状態文言のすぐ下へ1行だけ添える表示。直近の取得・操作が失敗していればその理由、成功していれば
最後に取得した時刻の古さを出す（BL-142 / BL-140）。決定は`core.display.SesameStatusDetail`が行い、
`compactLabel`（Tile・Complication向け）と`detailedLabel`（ホーム画面ウィジェット向け）の
2通りの文言を返す。

- `core.display.SesameStatusFreshness`（Android非依存、ユニットテスト対象）: 「最後に状態を取得した
  時刻」の表示文言を決める（BL-142）。自動状態取得を廃止したことで、Tile・Complication・ホーム画面
  ウィジェットは最後に分かった状態を出し続けるため、利用者がその表示をいつまで信用してよいかを
  判断できるようにする。
- 文言の規則は次のとおり。1分未満は「たった今」、1時間未満は「N分前」、24時間未満は「N時間前」、
  24時間以上は日付のみ（「9/17」）、一度も取得していなければ「未取得」。24時間以内を相対表記に
  するのは「どれだけ古いか」が一目で分かるため、24時間を超えたものを日付のみにするのは、その状態が
  既に参考値であり日付まで分かれば足りるため。端末時計のずれで未来の時刻が保存されていた場合も
  「たった今」へ丸める。日付表記のタイムゾーンは引数で受け、既定は端末のタイムゾーン
  （`java.time`はminSdk 26で利用でき、desugaringは不要）。
- 「全デバイス」対象では`oldestOf`が最も古い取得時刻を代表値として返し、1台でも未取得なら全体を
  未取得（null）として扱う。集約状態の判定（`TileDisplayStateResolver.resolveAggregate`が1台でも
  未取得なら「状態不明」にする）と同じ、最悪値を採る考え方で揃えている。
- デモ用デバイス（BL-109 / BL-123）はSesame APIから取得しないため鮮度という概念が無く、表示しない
  （`SesameTileStatus.detailLabel` / `SesameWidgetModel.Configured.detailLabel`がnull）。
- 表示位置は、Tileは右チップの状態文言の下（`TYPOGRAPHY_CAPTION3`）、ウィジェットは同じ位置の
  11sp、Complicationは`LONG_TEXT`のみ末尾へ括弧付きで添える（`SHORT_TEXT`は表示できる文字数が
  非常に少ないため対象外）。wearのTileとmobileのウィジェットで文言を食い違わせないよう、
  文言の決定はcoreに置く（`SesameTileContent`と同方針、BL-119）。
- `core.SesameStatusFailure`（Android非依存、ユニットテスト対象）: 直近の失敗の分類（BL-140）。
  利用者が自分で対処できるかどうかで2つに分ける。`AUTH_OR_QUOTA`（HTTP 401 / 403 / 429。資格情報が
  拒否された、またはAPIの月間リクエスト上限に達した）と`COMMUNICATION`（それ以外のすべて。圏外・
  タイムアウト・名前解決失敗・想定外の応答）。分類は`core.api.SesameApiException.httpStatusCode`
  （BL-139）から決める。
  - `AUTH_OR_QUOTA`をさらに「資格情報の誤り」と「上限到達」へ分けることはできない。Sesame APIは
    どちらもHTTP 403（本文も同一）で返すためで、429が返るかどうかもCANDY HOUSE側の実装次第で未確認
    （BL-141）。そのため文言も両方を含む案内にする。
  - 文言は`shortLabel`（Tile・Complication向け、「認証エラー」「通信エラー」の5文字。過去に7文字の
    文言がタイル幅に収まらず末尾省略された事例があるため、BL-102 / BL-104）と`detailedLabel`
    （ウィジェット向け、「認証エラー（設定を確認）」「通信エラー（電波状況を確認）」）の2つを持つ。
  - 「全デバイス」対象では`worstOf`が集約する。1台でも`AUTH_OR_QUOTA`があればそれを優先し、次に
    `COMMUNICATION`。利用者が対処できる失敗を、対処できない失敗に埋もれさせないため。
- **失敗しても表示している施錠状態は「状態不明」へ戻さない**（BL-140、ユーザー確認済み）。
  最後に分かった状態を残し、下の1行だけを失敗の理由へ差し替える。BL-142で「最後に分かった状態を
  出し続ける」設計にしたことと揃えるためで、分かっていた情報を捨てず、Tileからの施錠/解錠操作も
  引き続き行える。失敗を鮮度より優先して表示するのは、失敗のほうが新しい情報であり、かつ利用者が
  次に取るべき行動に直結するため（失敗しているときは表示が古いことも同時に意味する）。

### 資格情報管理（複数デバイス対応）

- `core.SesameCredentials`: `uuid`・`apikey`・`secretKeyHex`（16進数32文字＝16バイトのAES-128鍵）・
  `displayName`（ユーザーが設定するSesame名、例:「玄関」）を保持する。`secretKeyBytesOrNull`は
  `HexFormat.of().parseHex()`でデコードし、失敗時または16バイト長でない場合はnullを返す
  安全なアクセサ（例外を投げない、BL-026）。
  - **技術メモ（BL-058）**: 当初`secretKeyBase64`としてBase64形式を前提にしていたが、CANDY HOUSE
    公式ドキュメント（`API_document/SesameOS3/webapi.md`）のコード例ではsecretKeyは16進数文字列
    として扱われており、Base64ではないと判明したため`secretKeyHex`へ修正した。`AesCmac`/
    `SesameCommandSigner`自体は鍵の16バイト長のみを要求しエンコーディング形式に依存しないため
    変更不要だった。
- `core.SesameCredentialsStore`: `List<SesameCredentials>`全体をkotlinx.serializationでJSON化し
  単一キーで保存する`saveAll`/`loadAll`/`remove(uuid)`（BL-047）。デバイスの一意識別子は別途
  `deviceId`を持たせず、Sesame API上で既に一意な`uuid`をそのまま用いる。
- `mobile.credentials.EncryptedSharedPreferencesKeyValueStore`: `androidx.security.crypto`の
  `EncryptedSharedPreferences`＋`MasterKey`ベースの永続化実装（Android依存の薄いアダプタ）。
  ログ出力（`android.util.Log`等）は一切使用しておらず、平文の資格情報がログへ出力される経路はない。
- `mobile.credentials.CredentialsInputValidator`: uuid/apikey/secretKeyHexのいずれかが空欄、または
  `secretKeyBytesOrNull`がnullになる不正な鍵は無効と判定する（BL-024, BL-026）。
- `mobile.credentials.CredentialsInputSanitizer`（Android非依存、ユニットテスト対象）: uuid/apikey/
  secretKeyの入力値を、入力のたびに有効な値として取り得る形へ正規化する（BL-112）。
  全角ASCII（U+FF01〜U+FF5E）を半角へ変換し、ダッシュ類（U+2010〜U+2015 / U+2212 / U+30FC）を
  半角ハイフンへ寄せたうえで、uuidは英数字とハイフン、apikeyは空白を除くASCII印字可能文字、
  secretKeyは16進数32文字までに絞り込む。日本語IMEで入力された全角英数字は見た目で半角と
  区別できないまま保存され、署名検証がAPI側で失敗する原因になるため、入力時点で混入経路を塞ぐ。
  表示名は日本語を入力する項目のため正規化しない。
- `mobile.credentials.CredentialsSettingsScreen`: 複数デバイスの一覧・追加・編集・削除ができる
  Compose画面（BL-049）。現在の構成:
  - 入力欄（表示名/uuid/apikey/secretKey）はラベルのみのシンプルな見た目とし、secretKey欄は
    `PasswordVisualTransformation`でマスキング表示する（BL-023, BL-059）。
  - uuid/apikey/secretKeyの3欄は`singleLine = true`とし、`KeyboardOptions`でASCIIキーボード
    （secretKeyは`KeyboardType.Password`）を既定にしたうえで、`onValueChange`で
    `CredentialsInputSanitizer`を通してから状態へ反映する（BL-112）。表示名欄は対象外。
  - 詳細な説明はヘルプボタン（`TextButton`）タップで開く`AlertDialog`へ集約する（BL-057, BL-059）。
    ヘルプは**メニュー形式**で、項目一覧（`HelpMenuDialog`）と本文（`HelpTopicDialog`、「戻る」で
    一覧へ戻る）の2段構成にする（BL-113）。文言と並び順はAndroid非依存の
    `mobile.help.HelpContent`（`HelpTopic` / `HelpLink`、ユニットテスト対象）が保持し、
    (1)「値の取得方法」（`https://biz.candyhouse.co/biz/developer`（SESAME Biz 開発者ページ）へ
    遷移する`TextButton`＝`Intent.ACTION_VIEW`を含む。uuid・apikey・secretKeyはいずれもこの
    ページから取得する。Sesameアプリの「鍵をシェア」QRコードは使わない運用）、
    (2)「APIのリクエスト回数の上限」（BL-144）、
    (3)「Sesameが無くてもデモで試す」（ウォッチのタイルとホーム画面ウィジェットの両方の試し方、
    両者のデモは連動しないこと、登録後にデモのウィジェットが「タップして設定」へ戻ること）、
    (4)「登録後のウォッチでの使い方」、(5)「ホーム画面ウィジェットの使い方」（BL-124）の5項目を持つ。
    ウィジェットの説明はウィジェットの表示文言（「変更」「全デバイス」「通信中...」「タップして設定」）を
    含むことをユニットテストで固定する。
  - `HelpTopic`の外部リンクは`links: List<HelpLink>`（0個以上）。「値の取得方法」がSESAME Bizと
    公式の取得手順記事の2本を持つため、単一の`link`から変更した（BL-144）。
  - 「APIのリクエスト回数の上限」の文言は、上限の存在・上限到達時の表示が「認証エラー」になること・
    確認先（SESAME Biz）・対処（翌月のリセット待ちか引き上げの問い合わせ）・本アプリが自動取得を
    しないこと、を説明する。**上限の具体的な回数は書かない。** 1000回という値は利用者の環境での
    実測値であり、公式ドキュメント上の記載を確認できていないため（BL-141）。
    書かないことをユニットテストで固定する。
  - 併記する公式リンクは、SESAME Biz 開発者ページ（`HelpContent.SESAME_BIZ_DEVELOPER_URL`）と、
    APIキーの取得手順の公式記事（`SESAME_API_KEY_GUIDE_URL`、`jp.candyhouse.co/blogs/how-to/...`）の
    2本。いずれも2026-09-18に認証なしで開けることを実際にアクセスして確認した。
    Web APIのリファレンス（`doc.candyhouse.co/ja/SesameAPI/`）は同日時点でGitHub Pagesの認証へ
    リダイレクトされ認証なしでは開けないため、リンクとして採用しない。
    公式記事のURLは日本語のパスを持つため、`Uri.parse`がそのまま扱えるようパーセントエンコード済みの
    形で保持し、非ASCII文字を含まないことをユニットテストで固定する。
    デモモード（BL-109）はwear側にしか導線が無く、資格情報を用意できない利用者が
    体験できることに気づけなかったため、(2)を追加してmobile側からの導線とした（BL-113）。
  - 保存ボタンは`enabled = isInputValid`で制御し、保存成功時は「保存しました」を
    `LaunchedEffect`と`delay`で2秒間表示する（BL-024）。デバイス0件時は
    「まだSesameが登録されていません」を表示する（BL-056）。
  - `Modifier.safeDrawingPadding()`でステータスバー等のシステムUIとの重なりを防止（BL-045）。
  - `Column(verticalArrangement = Arrangement.spacedBy(8.dp))`で入力欄・ボタン間隔を統一し、
    保存ボタンは`Modifier.fillMaxWidth()`で表示（BL-059）。
  - **未確認事項**: `biz.candyhouse.co`は動的サイトのためWebFetchでの実ページ内容確認はできて
    おらず、公式ドキュメントの記述とユーザーからの実機確認報告のみを根拠にしている。

### mobileホーム画面ウィジェット

スマートフォンのホーム画面から、wearのTileと同じ表示・操作ルールでSesameの状態を確認・操作するウィジェット
（BL-121で表示と設定、BL-122でタップ操作、BL-123でデモ、BL-124でヘルプ・ドキュメント）。Data Layerを経由せず、
mobile内の保存値と`mobile.command.SesameDeviceCommandExecutor`（BL-120）を使うため、ウォッチを持たない利用者も
使える。「ウィジェットとwearで機能差を大きくつけない」ことを方針とし、判定ロジックは`core.display`に1つだけ置く
（BL-119）。

- **wearのTileと揃えている点**:
  - 表示文言・状態アイコン・状態色・テキスト色（`core.display.SesameTileContent`）と、状態色を右側だけに使う構成
    （左にデバイス名と「変更」、右に状態）。
  - 操作ルール（`core.display.SesameTileActions`）: 施錠中→解錠、解錠中→施錠、一部解錠→全施錠、通信中・状態不明→操作なし。
  - 確認画面: 解錠のみ（`SesameCommandConfirmation`）、左＝キャンセル・右＝解錠／全解錠の並びと配色。
  - 選択肢と対象の展開（`core.display.SesameDeviceTargets`）: 2台以上で先頭に「全デバイス」、0台ならデモのみ、
    全デバイスは各uuidへ個別に実行。全デバイスの状態集約規則（1台でも未取得なら状態不明）。
  - デモ: 登録0台のときだけ提示し、Sesame APIへ送らない。確認画面の有無・状態文言は実デバイスと同じ。
  - 左側のデバイス名タップは状態取得のみ、同一uuidへの2秒以内の重複は1回（`CommandDebouncer`を経路間で共有）。
- **意図的に揃えていない点**:
  - 追加時の設定: ウィジェットは`appwidget-provider`の`android:configure`で追加直後に選択画面を開ける（Tilesには
    同等の標準機構が無く、Tileは「タップして設定」から誘導する）。選べる内容は同じ。
  - デモ状態は端末ごとに独立（ウォッチは`DemoLockStateStore`、スマホは`LockStateStore`のデモuuid）で同期しない。
    また登録1台以上になるとウィジェットはデモの割り当てを自動で解除する（Tileは表示から消えるのみ）。
  - 「スマホ未接続」（DISCONNECTED）はウィジェットに存在しない（スマホ自身がAPIを呼ぶため常に接続扱い）。
  - 結果の通知: wear・ウィジェットとも成否をハプティクスで区別する（BL-129で揃えた）。
    ウィジェットはさらに、失敗の理由を状態文言の下へ表示する（BL-140）。
  - 表示の鮮度: wear・ウィジェットとも自動取得は行わず（BL-142）、保存値を表示したうえで
    最後に取得した時刻、または直近の失敗の理由を添える（前述「状態の鮮度表示と失敗の区別」）。
    ウィジェットだけは表示領域に余裕があるため、失敗時に対処を併記する詳しい文言を使う（BL-140）。
    更新は利用者のタップで行う。
  - サイズ: 既定はTile相当（4x2）で、ホーム画面の1マス（1x1）まで縮められる（BL-128）。
    `SesameWidgetLayout`（Android非依存、ユニットテスト対象）が表示領域（dp）から`FULL`/`COMPACT`を
    決め、`SesameWidget`は`SizeMode.Responsive`で候補サイズを提示して`LocalSize`を受け取る。
    しきい値は幅200dp・高さ140dpで、どちらかを下回れば`COMPACT`。幅は「左列（96dp）＋間隔（6dp）＋
    状態表示」を横に並べて成立する下限、高さは状態アイコン・状態文言・最終取得時刻・操作文言の
    4行が入る下限（外周・内側のパディング28dpと4行分の約108dpで合計約136dp）から決めた。
    高さは当初100dpとしていたが、4x1（約128dp）でも`FULL`が選ばれて操作文言が縦に見切れたため、
    余裕を見て140dpへ引き上げた（BL-158）。既定の4x2はどの端末でも140dp以上になるため`FULL`のまま。
    `COMPACT`は状態アイコンと状態文言だけを出し、デバイス名・「変更」・最終取得時刻・操作文言は
    出さない（1マスに入らないため）。タップの挙動は`FULL`と同じ（`WidgetTapAction`の判定どおり）で、
    対象デバイスの変更はウィジェットの長押しメニュー（`widgetFeatures="reconfigurable"`）から行う。
    未設定時の文言は「タップして設定」ではなく「設定」にする。
    `sesame_widget_info.xml`の`minWidth`/`minHeight`はAPI 30以下で既定の配置サイズを決めるため
    250x110dpのままにし、縮小の下限は`minResizeWidth`/`minResizeHeight`（50dp）で指定する。
    **複数台を横に並べる表示（4x1等）は採らない**（2026-09-18、ユーザー確認済み）。wear側に無い機能に
    なり、以降の表示変更で両方を追従させる必要が出るため。

- 実装方式はJetpack Glance（`androidx.glance:glance-appwidget` 1.2.0）。Glanceは推移的に
  `work-runtime` 2.7.1（`room-runtime` 2.2.5・`sqlite` 2.1.0を伴う）を持ち込むため、`work-runtime`を
  明示して2.10.5へ引き上げている（2026-09-16にユーザー判断）。2.11系は`kotlin-stdlib`をコンパイラ
  （2.0.21）より新しい2.1.20へ上げるため採らない（BL-130と同じ基準）。Glanceの導入で既存依存の版が
  変わるのは`compose-runtime`の1.7.6→1.7.8のみ。
- `mobile.widget.SesameWidget`（`GlanceAppWidget`）/ `SesameWidgetReceiver`（`GlanceAppWidgetReceiver`）:
  構成はTileと揃え、左列（幅96dp）にデバイス名チップと「変更」チップ（中立色
  `SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB`）、右側の残り全域に状態アイコン・状態文言・操作文言を
  中央寄せで置き、状態色（`SesameTileContent.backgroundColorArgb`/`statusTextColorArgb`）は右側にだけ使う。
  背景は暗色（0xFF121212）で角丸16dp。未設定時は「タップして設定」のみを表示し、全面タップで選択画面を開く。
  `onDeleted`で割り当てを消す。PendingIntentをインスタンス・操作の種類ごとに区別するため、各Intentの
  dataへ操作名とappWidgetIdを入れる。
- タップ操作（BL-122）: 右側は`mobile.widget.WidgetTapAction.forModel`（Android非依存）で決める。
  提示コマンドは`core.display.SesameTileActions`、確認の要否は`SesameCommandConfirmation`（Tileと同じ）で、
  施錠中→解錠確認画面、解錠中→即施錠、一部解錠→即全施錠、通信中・状態不明→操作なし、未設定→選択画面。
  左側のデバイス名は状態取得（GET）のみ、「変更」は選択画面を開く。
- `mobile.widget.WidgetCommandReceiver`（`exported="false"`のBroadcastReceiver）: 施錠の即時実行・状態取得・
  解錠確認画面での確定を受け、`goAsync`で`WidgetCommandRunner`を実行する。BACKLOGの既定はGlanceの
  `ActionCallback`だったが、解錠確認画面（Activity）からも同じ経路で実行するため、同じ`goAsync`の仕組みを
  自前のReceiverで使う形にした。WorkManagerへは委譲していない（通常のワークは端末の状態により実行が
  遅延しうり、即時性を優先したため。期限付き実行はAndroid 11以下でフォアグラウンドサービス通知が要る）。
  `actionSendBroadcast`は`FLAG_RECEIVER_FOREGROUND`を付けるため、実行時間の制限は約10秒
  （バックグラウンド受信の60秒ではない）。超えるとプロセスごとANRで強制終了され、
  `WidgetCommandRunner`の解除と再描画が行われずに「通信中...」が固着する（2026-09-17の実機検証で
  現物を確認、BL-137）。そのため処理全体を`WORK_TIMEOUT_MILLIS`（8秒）の`withTimeoutOrNull`で囲み、
  制限より手前で自分から打ち切る。打ち切りは取り消し（キャンセル）として伝わり、
  `WidgetCommandRunner`の`NonCancellable`な再描画が状態を戻す。
  Intentのコマンド名は`SesameCommand`（LOCK/UNLOCKのみ）へ一致するものだけを受け付ける。
- `mobile.widget.WidgetCommandRunner`（Android非依存、ユニットテスト対象）: 対象uuidを
  `SesameDeviceTargets.targetUuids`で展開し、各uuidへ`SesameDeviceCommandExecutor`を**並行して**呼ぶ
  （全デバイス時の所要時間を1台分に近づけ、Receiverの実行時間を短く保つ）。実行中は
  `WidgetInProgressTracker`へ対象uuid（全デバイス時は全デバイスの特別値も）を登録して再描画し
  （IN_PROGRESS＝通信中）、終了時に解除して再描画する（取り消されても解除後の再描画は行う）。
  ロック状態は実行口が成功時にだけ保存するため、失敗時の再描画は操作前の状態に戻る（失敗の明示はBL-129）。
- `mobile.widget.WidgetInProgressTracker`（Android非依存、ユニットテスト対象）: 実行中uuidを開始時刻つきで
  プロセス内メモリだけに持つ（同一uuidの重複実行は開始した回数だけ並べる）。永続化しないのは、
  プロセス終了時には実行も終わっており通信中の表示が固まるのを避けるため。全デバイスのウィジェットは
  登録済みのいずれかが実行中なら通信中。
  開始から`IN_PROGRESS_TIMEOUT_MILLIS`（30秒）を過ぎた登録は、解除されていなくても実行中とみなさない
  （BL-135）。実行が途中で打ち切られて解除の再描画が行われないと、ウィジェットは定期更新を持たないため、
  右側のタップも効かない「通信中...」がホーム画面に残り続ける。上限は受信1回の上限
  （8秒、BL-137）とSesame APIの呼び出し全体のタイムアウト（6秒、BL-133）より十分長くとり、
  正常に終わる操作を誤って打ち切らない値にしている。
- `mobile.widget.WidgetUnlockConfirmActivity`: ダイアログテーマ（`Theme.Material.Light.Dialog.NoActionBar`）の
  軽量Activity（`exported="false"`・`noHistory`・`excludeFromRecents`・空の`taskAffinity`）。見出し
  「（表示名）を解錠しますか？」と、左＝「キャンセル」（中立色）・右＝「解錠」/全デバイスは「全解錠」
  （解錠中の状態色）の2ボタン（wearの確認画面と同じ並び）。解錠でReceiverへ実行を依頼してすぐ閉じ、
  キャンセル・画面外タップ・戻る操作では何も送らない。
- 状態の追随: `SesameDeviceCommandExecutorFactory`の通知先が、DataItem同期（ウォッチのTile）に続けて
  `SesameWidgetUpdater.updateAll`を呼ぶ（デモはDataItem同期を行わず再描画のみ）。ウォッチ経由で成功してもウィジェットが、ウィジェット経由で成功しても
  ウォッチのTileが追随する。重複抑止はプロセス内共有の`CommandDebouncer`で、両経路の同一uuidへの2秒以内の
  重複は1回になる。
- 表示内容の決定は`mobile.widget.SesameWidgetModelResolver`（Android非依存、ユニットテスト対象）が行い、
  `SesameWidgetModel.Unconfigured`か`Configured`（uuid・表示名・`TileDisplayState`・全デバイスか）を返す。
  通信中は`WidgetInProgressTracker`の結果を`isCommandInProgress`として渡す。
  未割り当て、割り当て済みの実デバイスが削除済み、全デバイスで登録0台、デモで登録1台以上のときは未設定。
  状態は`LockStateStore`の保存値から、単一は`TileDisplayStateResolver.resolve`（未取得は状態不明）、
  全デバイスは`resolveAggregate`（1台でも未取得なら状態不明）で決める。mobileは自身がAPIを呼ぶため
  「スマホ未接続」は存在せず常に接続扱い。デモは保存値が無ければ`SesameDemoMode.INITIAL_IS_LOCKED`。
- `mobile.widget.SesameWidgetRepository`（Android依存）: 資格情報ストアからuuidと表示名だけを取り出し、
  割り当てとロック状態を読んでResolverへ渡す。
- 表示の更新: Glanceはセッション中に`provideGlance`を再実行しないため、`mobile.widget.SesameWidgetUpdater`が
  各インスタンスの状態（`PreferencesGlanceStateDefinition`）へ更新トークン`refresh_token`を書き込んでから
  `update`を呼び、描画側は`currentState`のトークン変化を`LaunchedEffect`の契機に保存値を読み直す。
  資格情報の保存・削除時（`CredentialsSettingsScreen`。ウォッチへのデバイス一覧同期とは独立したコルーチンで
  呼び、同期の失敗で再描画が飛ばないようにする、BL-134）、選択画面での割り当て直後、および
  `MainActivity.onStart`（BL-135）に呼ぶ。定期更新は行わない（`updatePeriodMillis=0`）。
  `MainActivity`から呼ぶのは、解除の再描画が行われず表示が固まった場合に、利用者が最初にとる行動
  （アプリを開く）で確実に復帰させるため。
- `mobile.widget.WidgetDeviceAssignmentStore`（Android非依存、ユニットテスト対象）: appWidgetIdごとの対象uuid
  （実uuid・全デバイス・デモ）を非暗号化SharedPreferences（`sesami_wear_widget_assignments`）の単一キーへ
  JSONオブジェクトで保存する。`remove`（削除されたインスタンス）と`unassignDevice`（特定uuidの割り当て解除）を持つ。
  `onRegisteredDevicesChanged(件数)`は1台以上ならデモの割り当てを解除する（BL-123）。
- デモ（BL-123）: 登録済みデバイスが0台のとき選択肢はデモ用デバイスのみになり（Tileと同じ）、デモの状態は
  `LockStateStore`へデモuuidで保存する（mobile端末内のみ。Sesame APIへもウォッチへも送らない）。確認画面の有無・
  状態文言は実デバイスと同じ。資格情報を1台でも保存すると、`CredentialsSettingsScreen`がデバイス一覧の同期前に
  `onRegisteredDevicesChanged`を呼び、デモを割り当てていたウィジェットを「タップして設定」へ戻す
  （Resolver側も登録1台以上ならデモを未設定として扱うため、呼び出し前に描画されても誤表示しない）。
- `mobile.widget.WidgetConfigurationActivity`: `appwidget-provider`の`android:configure`で追加時に開き、
  「変更」「タップして設定」からも開く。選択肢は`SesameDeviceTargets.choices`（Tileと同じ）で、0台時は
  「デモモード」の見出しと説明を添える。選択で割り当てを保存し再描画を要求してから`RESULT_OK`で閉じる。
- 実機検証（BL-126、2026-09-16、Pixel 8 Pro + Pixel Watch 2）: Claude Codeがadb経由のUI操作
  （`input tap`/`input draganddrop`と`exec-out screencap`、`logcat`）で実施した。実資格情報を使わない
  ため、BL-131の併存インストールでデバッグ版を入れ、デモモードとダミー資格情報で確認している。
  確認できたのは次のとおり。(1) ウィジェット選択画面に`Sesami Wear (debug)`が4x2と説明文つきで並び、
  ホーム画面へのドロップで`WidgetConfigurationActivity`が開くこと。(2) 解錠は確認画面
  （`WidgetUnlockConfirmActivity`）を挟み、施錠は確認なしで1秒以内に「施錠中／タップで解錠」へ戻ること。
  (3) ダミー資格情報2台で選択画面に「全デバイス」と2台が並び、全デバイス選択時は「状態不明」を表示すること。
  (4) デバイス名タップ（状態取得）で表示が崩れないこと。(5) 資格情報を全削除すると「タップして設定」へ
  戻ること。(6) 登録0台でデモモードの見出しとデモ用チップが出ること。(9)(11) ウォッチ側はTileを
  デバッグ面（`am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile`）で
  追加し、タイル右側タップ→解錠確認→`解錠中`／`タップで施錠`（`SesameTileService`のログで
  `buildConfiguredTile ... state=UNLOCKED`）とハプティクスの再生（`Vibrator`のログ）、ウォッチの
  設定画面（`wear.MainActivity`）の表示を確認した。ウォッチのTile設定画面がスマホの登録0台を検知して
  デモモードを提示することから、デバイス一覧のData Layer同期が機能していることも確認できた。
  (3)のMIXED表示と(8)の相互追随はデモでは作れない（デモの状態はウィジェットとTileで意図的に独立して
  おり、実機でも追随しないことを確認済み）ため、BL-132のモックAPIで実デバイス相当の状態変化を作って
  検証した。結果は次のとおり。(8) ウィジェットから解錠するとウォッチのTileが`解錠中`へ追随し
  （wearのログで`state=UNLOCKED`）、ウォッチのTileから施錠するとウィジェットが`施錠中`へ追随した。
  (3) 2台のうち1台だけを解錠した状態で全デバイスを選ぶと「一部解錠／タップで全施錠」（紫）を表示し、
  タップで両uuidへ施錠が飛んで「全施錠中」になった。(7) 端末再起動後もウィジェットの割り当てと表示が
  保たれた。未確認のまま残るのは、(9)のうちComplicationの表示・追随（文字盤のコンプリケーション設定を
  差し替える必要があり、利用者の文字盤構成を変えるため未実施）と、(10)のWear OSコンパニオン未導入
  スマホでの動作で、BL-126へ残している。実Sesameデバイス・実資格情報での疎通確認も、モックでは
  代替できない範囲として残る。
- 実機検証（BL-136 / BL-137、2026-09-17、Pixel 8 Pro）: 「通信中...」の固着と「ウィジェットが繰り返し
  停止しています」の再現・解消を、Claude Codeがadb経由で確認した。実資格情報が登録された端末での検証
  だったため、`-PsesameApiBaseUrl`で接続先を到達不能なアドレスへ固定したデバッグ版だけを使い
  （実Sesame APIへは1度も送っていない）、施錠/解錠は行わず状態取得のみで確認している。
  確認できたのは次のとおり。(1) 修正前のビルドでは、前回検証時（前日）の「⏳通信中...」がそのまま
  ホーム画面に残っており、操作文言も消えて右側がタップできない状態だった。(2) デバイス名チップを
  タップすると10秒後に`ANR in com.sesamiwear.mobile.debug / Reason: Broadcast of Intent
  (act=...REFRESH_STATUS, flg=0x10000010)`が記録され、`Killing ...: bg anr`でプロセスが強制終了され、
  表示は「通信中...」のまま変わらなかった。`flg`の`0x10000000`は`FLAG_RECEIVER_FOREGROUND`で、
  実行時間の制限が約10秒であること（＝想定していた60秒ではないこと）がここで判明した。
  (3) 修正後のビルドでは、同じ操作でANRもFATALも発生せずプロセスが生存し、タップから約3.9秒で
  「❓状態不明」へ戻った（接続が即座に拒否される宛先では約0.4秒）。(4) `am force-stop`で途中終了させた
  ウィジェットは、アプリを開くだけで再描画され「状態不明」へ戻った（`MainActivity.onStart`、BL-135）。
  (5) logcatのSesame系タグの行に、uuid形状の文字列・32桁16進数の文字列は1件も出力されていなかった。
  当初はコードの到達経路から「通信エラーの例外が捕捉されずプロセスが落ちる」と推定していたが、
  実機で観測された直接の機序はANRによる強制終了だった。例外の取りこぼし（BL-133 / BL-134）も
  接続が即座に拒否される経路で成立する独立した危険として残るため、両方を修正している。
  選ばずに戻ると`RESULT_CANCELED`のままで、追加時ならウィジェットは配置されない。
  `exported="true"`（ホームアプリが起動するため）・`excludeFromRecents`・空の`taskAffinity`。
- 実機検証（BL-126、2026-09-17、Pixel 8 Pro + Pixel Watch 2）: デバッグ版（BL-131の併存インストール）で
  残項目を確認した。(1) Tileからの施錠/解錠操作、(2) ウィジェットとウォッチの相互追随、
  (3) Complicationの表示・追随、(4) 実Sesameデバイス・実資格情報でのSesame API疎通が、いずれも現行コードの
  デバッグ版で動作することをユーザーが確認した（実資格情報を用いる操作はユーザーが実施し、Claude Codeは
  資格情報を取得・入力していない。`rules/guardrails-unified.v1.md` 12.5）。残るのはWear OSコンパニオン
  アプリ未導入スマホでの動作のみで、検証環境の作り方と確認ポイントは`docs/INSTALL.md`
  「1.7 Wear OSコンパニオンアプリ未導入の環境で検証する」へ分離した。
  検証中に判明した、Play版とデバッグ版を併存させたときの注意点を3点記録する。
  - **Wear OSのタイル一覧（`AddTileActivity`）は、すでにカルーセルへ追加済みのタイルを表示しない。**
    デバッグ面のブロードキャスト（`DEBUG_SURFACE`の`add-tile`）でデバッグ版のタイルを追加すると
    一覧から消えるため、「デバッグ版が一覧に出ない＝インストールされていない」と誤読しやすい。
    実際にはシステムから両方が見えており、`cmd package query-services -a
    androidx.wear.tiles.action.BIND_TILE_PROVIDER`と、`WearServices`の`DefaultMetadataPreviewProvider`が
    出す列挙ログの双方で確認できる。`remove-tile`で外すと一覧へ再び現れることで機序を確定した。
  - `SesameTileService`は`androidx.wear.tiles.PREVIEW`のmeta-dataを持たないため、列挙時に
    `has no metadata`が記録され、一覧のプレビューはアプリアイコンで代替される。Play版も同じ状態で
    一覧に表示されるため、これは一覧非表示の原因ではない。
  - 文字盤のComplicationやTileが、Play版とデバッグ版のどちらから提供されているかは見た目で区別できない。
    logcatの`for bound-service {<パッケージ>/...SesameComplicationDataSourceService}`でパッケージ名を
    確認する。再インストール直後は、デバッグ版を入れてもPlay版のデータソースが割り当てられたままに
    なりうるため、デバッグ版で検証するには文字盤側でデータソースを差し替える必要がある。
- 実機検証（BL-126、2026-09-17、Androidエミュレータ Pixel 6 / Android 15）: Wear OSコンパニオンアプリ
  未導入環境での動作を、Claude Codeがadb経由のUI操作で確認した。検証環境は`avdmanager`で作成したAVD
  （`nocompanion`、`system-images;android-35;google_apis;x86_64`、`-d pixel_6`）で、`pm list packages`に
  `wear`を含むパッケージが1件も無く（Google Play services自体は導入済み）、logcatにも
  `Wear_Controller: Wearable module requires a companion app to be installed.`と
  `WearableService: Wear is not available on this device.`が出る、コンパニオン未導入の状態である。
  実資格情報は使わず、ダミーの資格情報2台（MockLockA / MockLockB）と、接続先をBL-132のモックAPIへ
  差し替えたデバッグ版（`-PsesameApiBaseUrl=http://10.0.2.2:8080/api/sesame2`。エミュレータからは
  `10.0.2.2`がホストPCを指す）で確認している。確認できたのは次のとおり。(1) アプリの起動。
  (2) ダミー資格情報2台の保存（一覧へ反映）。(3) 2台の削除（0台へ戻り、設置済みウィジェットが
  「タップして設定」へ復帰）。(4) ウィジェットの追加と対象デバイスの選択（「全デバイス」と2台が並ぶ）。
  (5) デバイス名タップによる状態取得（モックへGETが届き「施錠中／タップで解錠」へ確定）。
  (6) ウィジェットからの解錠（確認画面あり）・施錠（確認画面なし）と、「全デバイス」での一括解錠
  （2台へcmd=83が飛び「全解錠中」へ）。いずれもクラッシュせず、logcatの`FATAL EXCEPTION`は0件だった。
  Data Layer APIの失敗は`SesameStatusSyncer: syncLocked skipped: statusCode=17`と
  `SesameDeviceListSyncer: sync skipped: statusCode=17`として計9件記録され（17＝`API_UNAVAILABLE`）、
  **`DataLayerBestEffort`が握りつぶして本体の処理を継続していること（BL-118 / BL-134）が実地で確認できた**。
  秘密情報の非出力も確認した（logcat全体1761行に、uuid形状の文字列・32桁16進数・実際に入力した
  ダミー値は1件も出力されていない）。
  検証手順で踏んだ注意点を1点記録する。品質ゲートの`./gradlew assembleDebug`は`-PsesameApiBaseUrl`を
  伴わないため、**注入済みのAPKを本番URLのものへ上書きする**。接続先を差し替えたAPKは品質ゲートの
  実行後に組み直して入れ直す（上書きされたAPKを入れるとモックへ1件も届かず、原因が分かりにくい）。
- 実機検証（BL-149、2026-09-18、Pixel 8 Pro + Pixel Watch 2）: BL-142の最終取得時刻、BL-140の失敗文言、
  BL-128のサイズ別レイアウト、BL-147の呼び出し回数、BL-148の連打抑止を、Claude Codeがadb経由のUI操作で
  確認した。実資格情報は使わず、ダミー資格情報2台（Genkan / Ura）とモックAPI（BL-132）で状態を作っている。
  確認できたのは次のとおり。(1) Tileは全施錠中・全解錠中・一部解錠の3状態で、アイコン・状態文言・
  最終取得時刻（「たった今」「1分前」「6分前」）・操作文言の4要素が円形画面のセーフエリアへ収まり、
  省略・見切れは無い。(2) Tileの失敗文言「認証エラー」「通信エラー」も同じ位置へ収まる。
  (3) ウィジェット（4x2）は「未取得」「たった今」「3分前」と、12文字の「認証エラー（設定を確認）」
  「通信エラー（電波状況を確認）」のいずれも1行で省略無く表示する。(4) ウィジェットは追加直後が4x2
  （選択画面の表示も4x2）で、1x1まで縮められ、縮小時はアイコンと状態文言だけになり文字は欠けない。
  縮小時もタップで施錠/解錠でき、長押しメニューの「ウィジェットの設定」から対象デバイスを変更できる。
  (5) 状態取得はデバイス名のタップでのみ飛び、Tileの再描画（`onTileRequest`）が複数回起きてもモックへ
  GETは1件も届かない（自動取得の廃止、BL-142）。(6) 同一デバイスへ1秒あけて2回タップしても、モックへの
  GETは1巡分しか届かない（BL-148）。(7) 「今月のAPI呼び出し」は実際の呼び出し回数（GET 4回＋POST 5回＝
  9回）と一致した（BL-147）。(8) 資格情報を全削除するとウィジェットは「タップして設定」へ戻り、
  タップするとデモ用デバイスだけが選択肢に出る（BL-123）。デモの施錠/解錠では最終取得時刻の行が
  出ず、呼び出し回数も増えない（18回のまま、BL-147）。(9) Complicationは`SHORT_TEXT`枠で
  「混在」を省略なく表示した（`onComplicationRequest type=SHORT_TEXT` / `state=MIXED`をデバッグ版の
  プロセスのログで確認）。失敗の再現には、常に403を返すモックと、接続が即座に拒否される宛先
  （`http://127.0.0.1:1/api/sesame2`）を`-PsesameApiBaseUrl`で注入している。
  この検証で不具合を検出し、BL-156（ウィジェット操作の振動が鳴らない。`SesameHapticPlayer`が
  `VibrationAttributes`を渡さないため、バックグラウンド実行のウィジェット操作では
  `VibratorManagerService`が振動を破棄する。ウォッチ側は前面のActivityから鳴らすため正常）、
  BL-157（Tileの「全デバイス」操作で`LockStateStore`の更新が後勝ちで失われ、解錠済みのデバイスを
  「施錠中」と表示し続ける）、BL-158（幅4マス×高さ1マスでFULLレイアウトの操作文言が縦に見切れる）、
  BL-159（呼び出し回数の表示が画面の再開では更新されない）、BL-160（資格情報を削除してもロック状態が
  残る。`LockStateStore.remove`が本体のコードから呼ばれていない）として起票した。未確認のまま残るのは、
  Complicationの`LONG_TEXT`枠の表示（該当枠を持つ文字盤へ割り当てられなかった）と、Android 11以下での
  既定の配置サイズ（該当端末が無い）で、BL-149へ残している。
- 利用者向けドキュメント（BL-124）: `docs/USER_GUIDE.md`「ホーム画面ウィジェットで操作する」、
  `docs/CLOSED_TEST.md`（ウォッチ無しでも参加・試用できること）、`README.md`の主な機能、
  `docs/RELEASE_NOTES.md`の0.11.0（未リリース）、`docs/store/STORE_LISTING.md`（短い説明・詳細な説明・
  対象デバイス）へ反映した。GitHub上ではマージ時点で公開される一方テスターの手元は0.10.0のままのため、
  **公開ドキュメントには「0.11.0以降」と明記する**（2026-09-16にユーザー判断）。STORE_LISTINGはPlay Consoleへの
  転記を0.11.0の配信時（BL-127）に行う旨を冒頭に注記している。プライバシーポリシーとデータセーフティ申告は
  変更しない。ウィジェットが端末内に保存するのはロック状態（uuid・真偽値・時刻）と割り当て（appWidgetIdと
  uuid）のみで、端末外へ新たに送信する情報は無く（Sesame APIへの送信内容は従来と同じ）、wearが既に
  端末内に保存しているTile割り当て・デモ状態と同種の非機密情報であるため。
- `res/xml/sesame_widget_info.xml`: 既定のサイズはTile相当（minWidth 250dp / minHeight 110dp、
  `targetCellWidth`/`targetCellHeight`で4x2セル）で、`resizeMode=horizontal|vertical`と
  minResizeWidth / minResizeHeight 50dpにより1マス（1x1）まで縮められる（BL-128）。
  `widgetFeatures=reconfigurable`、`initialLayout`はGlance既定の読み込み中レイアウト。

### Data Layer APIプロトコル定義（`core.SesameWearProtocol`）

`mobile`/`wear`間で共有するメッセージパス・DataItemパス・ペイロードキーの定義（Android非依存）。

- `PATH_LOCK_REQUEST` / `PATH_UNLOCK_REQUEST`: 施錠/解錠コマンド送信（BL-006）。
- `PATH_COMMAND_RESULT`: コマンド結果返送（BL-006）。
- `PATH_STATUS_REQUEST`: 状態取得リクエスト、Fire-and-forget（BL-061）。結果は返さず
  `STATUS_DATA_ITEM_PATH`のDataItem変更として非同期に届く。
- `STATUS_DATA_ITEM_PATH` / `KEY_IS_LOCKED` / `KEY_UPDATED_AT_EPOCH_MILLIS` / `KEY_LAST_FAILURE`:
  ロック状態と直近の失敗の同期（BL-015 / BL-140）。`statusDataItemPath(uuid)`でデバイスごとに
  一意なパスを生成する（BL-050）。値が無い項目はキーごと載せず、wear側は
  `core.SesameStatusSnapshotFactory`がキーの有無から復元する。施錠状態が未取得のまま失敗だけが
  同期されることもある（一度も取得できていないデバイスで認証エラーになった場合）。
- `encodeDeviceUuid` / `decodeDeviceUuid`: 施錠/解錠/状態取得コマンドの対象デバイスuuidを
  メッセージペイロードへUTF-8バイト列としてそのまま載せる（BL-048）。
- `DEVICE_LIST_DATA_ITEM_PATH` / `KEY_DEVICE_LIST_JSON`: 登録済みデバイス一覧（`SesameDeviceSummary`
  のリスト、機密情報を含まない）をmobile→wearへ同期するパス・キー（BL-052）。
- `ALL_DEVICES_TARGET_UUID`（`"__all_devices__"`）: 「登録済み全デバイス」を表す特別な値
  （BL-071）。実際のSesame uuidと衝突しない固定文字列で、wear側のデバイス割当ストアへ実uuidの
  代わりに保存し、Tile/Complication表示・コマンド送信時に判定する。

### mobile側コマンド処理

- `mobile.messaging.SesameMessageListenerService`（`WearableListenerService`実装、BL-013）:
  受信パスをコマンドへ変換して`mobile.command.SesameDeviceCommandExecutor`（下記）へ渡し、結果を
  `PATH_COMMAND_RESULT`で返すだけの薄いアダプタ（BL-120）。重複として無視された場合は結果を返さない。
  未知のパスは実行口を呼ばず`FAILURE`を返す。`PATH_STATUS_REQUEST`受信時は実行口の`refreshStatus`を
  呼ぶだけで、結果はwear側へ返送しない（BL-061。取得できた状態は実行口の通知でDataItemへ同期される）。
- `mobile.command.SesameDeviceCommandExecutor`（Android非依存、ユニットテスト対象）: mobile端末内で
  施錠・解錠・状態取得を行う実行口（BL-120）。Sesame APIの呼び出しはもともとListenerServiceの
  privateメソッドに閉じていたが、ホーム画面ウィジェットもData Layerを経由せず同じ処理を呼ぶため
  切り出した。
  - `execute(uuid, command)`: 重複判定（`CommandDebouncer`）→ 資格情報の検索 → `SesameCommandHandler`
    （`SesameApiClient.sendCommand()`を呼ぶ）→ 成功時のみロック状態の保存と通知、の順（移設前と同じ）。
    戻り値は`SUCCESS`/`FAILURE`/`DEBOUNCED`。資格情報が無い・鍵が不正（`secretKeyBytesOrNull`がnull、
    BL-026）ならAPIを呼ばず`FAILURE`。保存する状態は「送信したコマンドが意図した状態」（LOCK→施錠、
    UNLOCK→解錠、BL-015の簡略化ロジック）。
  - `refreshStatus(uuid)`: `SesameApiClient.getStatus()`の結果を保存・通知し、施錠状態を返す。
    資格情報が無い場合はAPIを呼ばずnull（保存・通知もしない）。APIエラーの場合はnullを返すが、
    失敗の分類（`SesameStatusFailure`）を保存して通知する（BL-140）。
    同一uuidへの連打は`CommandDebouncer`で抑止し、抑止した場合はAPIを呼ばず保存済みの状態を返す
    （BL-148。失敗ではないため失敗の記録も残さない）。
  - Sesame APIの失敗は`SesameApiFailureLog.describe`が組み立てた1行を`logFailure`へ渡す（BL-139）。
    本クラスはAndroid非依存のユニットテスト対象で`android.util.Log`を直接呼べないため、出力先は
    注入する。施錠/解錠側は`SesameCommandHandler`の`onFailure`から同じ経路へ流す。
  - あわせて失敗の分類を`LockStateStore.saveFailure`で保存し、ウォッチ・ウィジェットへ通知する
    （BL-140）。状態取得と施錠/解錠のどちらの失敗も同じ扱いにする。成功時の`save`は失敗の記録を
    消すため、直近の1回の結果だけが残る。最後に分かった施錠状態と、その取得時刻はそのまま残す。
  - デモ用デバイス（BL-123）: `execute`はAPIを呼ばず常に成功として端末内の状態だけを書き換え（重複判定は
    実デバイスと同じ）、`refreshStatus`は保存値（無ければ`INITIAL_IS_LOCKED`）を返すだけで保存・通知しない。
    ウォッチへのDataItem同期も行わず、ウォッチ側のデモ状態（`wear.demo.DemoLockStateStore`）とは同期しない。
  - 資格情報の読み出し（`loadCredentials`）、`LockStateStore`、通知先`LockStateNotifier`（`local`＝すべての
    変化でウィジェット再描画、`watch`＝実デバイスの変化だけでDataItem同期。`watch`→`local`の順に呼ぶ。
    通知は`SesameStatusSnapshot`（施錠状態・取得時刻・直近の失敗）を丸ごと渡す、BL-140）、
    `SesameApiAccess`、`CommandDebouncer`、時刻取得を注入する。`CommandDebouncer`は
    companion objectの`sharedDebouncer`をプロセス内で共有し、ウォッチ経由とウィジェット経由の
    同一uuidへの2秒以内の重複も1回にまとめる。
  - `mobile.command.SesameApiAccess`（同一ファイル、Android非依存）: APIクライアントの生成
    （`clientFactory`。既定はデバッグビルドで`-PsesameApiBaseUrl`があれば接続先を差し替える、BL-132）、
    失敗ログの出力先（`logFailure`。既定は何もしない、BL-139）、呼び出し回数の記録口
    （`recordApiCall`。既定は何もしない、BL-147）をまとめた型。実行口の引数がdetektの
    `LongParameterList`閾値（7）に達したため、Sesame APIとのつなぎ方を1つにまとめた。
  - `mobile.command.SesameDeviceCommandExecutorFactory`（Android依存の配線のみ）: 資格情報は
    `EncryptedSharedPreferencesKeyValueStore`、ロック状態は`SharedPreferencesKeyValueStore.forLockState`、
    通知先は`watch`＝`SesameStatusSyncer.sync`（スナップショットをそのままDataItemへ、BL-118の
    ベストエフォート）、`local`＝`SesameWidgetUpdater.updateAll`、
    失敗ログは`Log.w(SesameApiFailureLog.TAG, ...)`で生成する。
    `Log.w`はproguard-rules.proの`-assumenosideeffects`の対象外のため、リリースビルドにも残る（BL-083）。
- `mobile.state.LockStateStore`（Android非依存、ユニットテスト対象）: uuidごとのロック状態（施錠中か・
  更新時刻・直近の失敗、`core.SesameStatusSnapshot`で返す）をmobile端末内に保存する（BL-120 / BL-140）。
  `save`は状態を保存して失敗の記録を消し、`saveFailure`は状態を残したまま失敗だけを上書きする。機密情報を含まないため
  保存先は非暗号化SharedPreferences（`mobile.state.SharedPreferencesKeyValueStore`、ファイル名
  `sesami_wear_lock_state`）。全デバイス分を1つのJSONオブジェクト（uuid → `isLocked`/`updatedAtEpochMillis`）
  にして単一キー`lock_states`へ保存し、`remove(uuid)`で個別に消せる。mobileはkotlinx.serializationの
  コンパイラプラグインを適用していないため`@Serializable`を使わずJsonObjectを直接組み立てる（R8の
  keepルールも不要）。壊れた値・欠けた項目は未取得扱い。書き込みは`@Synchronized`で同期化する。
- `mobile.state.ApiUsageCounter`（Android非依存、ユニットテスト対象、BL-147）: このアプリが
  Sesame Web APIを呼び出した回数を暦月ごとに数える。`SesameApiAccess.recordApiCall`が実際にAPIを
  呼ぶ直前に呼ばれ、**成否によらず**数える（上限は成功・失敗を問わず消費されるため）。
  デモ用デバイス・重複として無視した操作・資格情報が無い場合はAPIを呼ばないため数えない。
  保存値は「対象の年月」と「回数」の2つだけで機密情報を含まないため、保存先は非暗号化
  SharedPreferences（ファイル名`sesami_wear_api_usage`）。月が変わったら数え直す。
  月の境界を判定するタイムゾーンは注入可能で、既定は端末のタイムゾーン（CANDY HOUSE側のカウンタが
  どのタイムゾーンで月を区切るかは未確認のため、利用者の体感に合う側を既定とする）。
  - **数えるのはこのアプリからの呼び出しだけで、Sesame純正アプリなど他経路の消費は含まない。**
    上限値そのものも契約内容によって変わりアプリからは取得できないため、表示は「上限までの残り」
    ではなく消費の目安として出す。文言（`ApiUsageCounter.label`）に「このアプリからの分のみ・目安」
    を含めることをユニットテストで固定する。
  - 表示場所は資格情報設定画面の見出しの直下（`ScreenHeader`）。画面を開いた時点の値を出し、
    開いている間の更新は行わない（設定画面は操作の場ではないため）。上限の存在そのものの説明は
    ヘルプの「APIのリクエスト回数の上限」（BL-144）が持つ。
- `mobile.messaging.CommandDebouncer`（Android非依存、時刻取得を注入可能）: 同一キーへの
  2秒以内の重複を無視する（BL-062、Tile連打による多重送信・多重ハプティクスの防止）。
  - キーは施錠/解錠が`cmd:{uuid}`、状態取得が`status:{uuid}`で、**別々に数える**（BL-148）。
    分けないと、施錠した直後に状態を取り直せなくなる（BL-061の巻き戻り防止と衝突する）。
  - 状態取得を抑止の対象へ加えたのはBL-148。BL-142で自動状態取得を廃止し、Sesame Web APIの消費が
    利用者のタップ回数と等しくなったため、誤タップ・二度押しがそのまま月間リクエスト上限
    （BL-141）へ効くようになった。
  - 間隔は施錠/解錠と同じ2秒（`DEFAULT_WINDOW_MILLIS`）。**「連打」の定義を経路で揃えるため**で、
    二度押し・誤タップは確実に弾き、「取れなかったのでもう一度」という意図的な再試行（通常は
    2秒以上あく）は通す。利用者が明示的に意図した取得は抑制しないという方針（BL-142）を崩さない
    範囲で最大の効果を取る値として選んだ。
  - 「全デバイス」対象のタップで登録台数ぶん飛ぶのは意図した動作のため対象外（uuidが異なるため
    同一キーの重複に当たらない）。
  - `sharedDebouncer`をプロセス内で共有するため、ウォッチ経由とウィジェット経由の重複も
    まとめて1回に抑える。
- `mobile.messaging.SesameStatusSyncer`: `DataClient.putDataItem`ラッパー。コマンド送信成功時は
  「送信したコマンドが意図した状態」（LOCK成功→施錠、UNLOCK成功→解錠）をそのまま同期する簡略化
  ロジック。`PATH_STATUS_REQUEST`経由ではSesame APIのGET結果をそのまま同期する（BL-015, BL-061）。
- `mobile.messaging.SesameDeviceListSyncer`: 登録済みデバイス一覧（uuid/displayNameのみ、
  apikey/secretKeyは含めない）を`DEVICE_LIST_DATA_ITEM_PATH`へ同期する（BL-052）。
- `mobile.EntryPointGuard`（Android非依存、ユニットテスト対象）: システムからの入口
  （`WidgetCommandReceiver`のタップ処理、`SesameMessageListenerService`の受信、
  `WidgetConfigurationActivity`と`MainActivity`のライフサイクル）で起動したコルーチンから例外が漏れ、
  プロセスごとアプリが落ちるのを防ぐ（BL-134）。これらの入口は結果を受け取る呼び出し元がいないため、
  漏れた例外は既定のハンドラへ届いてプロセスを終了させ、ホーム画面のウィジェットは最後に描いた表示
  （多くは操作直後の「通信中...」）のまま取り残される。捕捉した例外は型名だけを`Log.w`へ渡す
  （接続先URLなどuuidを含む文字列をログへ流さないため）。コルーチンのキャンセルは再送出する。
- `mobile.messaging.DataLayerBestEffort`（Android非依存、ユニットテスト対象）: mobile側のWearable
  Data Layer呼び出し（上記2つのSyncerの`putDataItem`と、`SesameMessageListenerService`の結果返送
  `sendMessage`）をベストエフォート呼び出しにする（BL-118）。`ApiException`のみを捕捉して
  ステータスコードを`Log.w`へ渡し（資格情報・uuidは出さない）、呼び出し元の処理（資格情報の保存・
  削除、コマンド実行）を継続する。`ApiException`以外の失敗も同じく握りつぶし、`UNKNOWN_STATUS_CODE`
  （-1）として通知する（BL-134）。コルーチンのキャンセルは捕捉しない。Wear OSのコンパニオンアプリが
  入っていない端末ではWearable APIが`ApiException`で失敗しうるが、以前は例外処理なしで`await()`して
  いたため、資格情報の保存時に起動したコルーチンから例外が漏れてアプリが落ちる経路があった。
- **未確認事項**: 状態同期はコマンド送信成功時と`PATH_STATUS_REQUEST`経由（デバイス名チップの
  タップ時のみ。自動取得はBL-142で廃止した）に限られ、定期ポーリングは行わない。
  Sesame純正アプリでの操作等、他経路による状態変化は、利用者が明示的に状態取得するまで反映されない。
  表示が古いことは鮮度の文言（前述「状態の鮮度表示」）で分かるようにしている。

### wear側コマンド送信・結果受信

- `wear.messaging.SesameCommandSender`（インターフェース、Android非依存）/
  `MessageClientSesameMessageSender`（`MessageClient`ベースの実装）/
  `SesameCommandSenderProvider`（`Wearable.getMessageClient(context)`からのファクトリ）:
  lock/unlock/requestStatusの意図を`mobile`へ送信する（BL-006, BL-008, BL-061）。
- `wear.messaging.SesameConnectedNodeProvider`: `NodeClient.connectedNodes`から接続先ノードID
  取得（スマホ未接続時の判定に使用、BL-014）。
- `wear.messaging.SesameResultHandler`（Android非依存）/ `SesameResultListenerService`
  （`WearableListenerService`実装）: `PATH_COMMAND_RESULT`受信時にpathとpayloadから
  `HapticPattern`を判定し`SesameHapticPlayer`へ渡す。あわせてTile/Complicationの即時再描画も
  リクエストする（BL-016, BL-064）。
- `wear.messaging.SesameStatusListenerService`（`WearableListenerService.onDataChanged`）:
  `STATUS_DATA_ITEM_PATH`配下のDataItem変更を検知し、`TileService.getUpdater()`と
  `ComplicationDataSourceUpdateRequester.requestUpdateAll()`でTile/Complicationの再描画をリクエスト
  する（BL-061）。
- `wear.messaging.SesameStatusSnapshotReader`: DataClient経由でロック状態スナップショット
  （`core.SesameStatusSnapshot`、`SesameStatusSnapshotFactory`で構築）を読み取る（BL-015）。
- `wear.messaging.SesameDeviceListReader`: `DEVICE_LIST_DATA_ITEM_PATH`から登録済みデバイス一覧
  （`SesameDeviceSummary`のリスト）を読み取る。Tile/Complication Configuration画面の選択肢表示に
  用いる（BL-052）。
- **状態更新に関する実装メモ（BL-064、確認済み）**: 施錠/解錠成功後にTileの状態表示が自動更新
  されない事象が報告されていたが、2026-08-30の実機確認で期待どおり自動更新されることを確認した。
  ただし`DataClient.putDataItem`の`urgent`フラグ付き書き込みでもmobile→wear間の物理的な同期完了は
  保証されない（`await()`はローカル書き込み完了のみを示す）ため、コマンド結果受信直後の再描画
  リクエストがDataItem同期完了前に発火する競合の可能性は構造上残る。再発時の切り分け用に、各段階の
  ログ（`mobile.messaging.SesameMessageListenerService`・`wear.messaging.SesameResultListenerService`・
  `wear.tile.SesameTileService`の`Log.d`）を残置している。出力内容はパス・成否・状態の真偽値のみで、
  資格情報は含めない。

### Tile

- `wear.tile.SesameTileService`（`androidx.wear.tiles.TileService`実装）: 現在のレイアウトは
  タイル端から`CONTAINER_PADDING_DP`（16dp）内側へ寄せた`Row`（左列＋右チップ）構成。
  - 左列（`LEFT_COLUMN_WIDTH_DP`=76dp固定、高さいっぱい）: デバイス名チップ（タップで
    `SesameStatusRefreshActivity`を起動し状態更新をリクエスト）とデバイス変更チップ（タップで
    `TileConfigurationActivity`を起動）を`weight(1f)`で均等分割、間に`CHIP_SPACING_DP`（6dp）の
    Spacer。
  - 右チップ（残り全域）: 状態アイコン・状態文言・操作ラベルを中央寄せで表示し、タップで施錠/解錠
    コマンド（またはMIXED時は「全施錠」）を実行する。
  - 各チップは共通ヘルパー`buildChipModifiers`で角丸背景（`ModifiersBuilders.Corner`、半径
    `CHIP_CORNER_RADIUS_DP`=12dp）・内側パディング（`CHIP_INNER_PADDING_DP`=6dp）を持つ。
  - 状態色（`SesameTileContent.backgroundColorArgb`: 施錠中=緑/解錠中=赤/通信中=黄/MIXED=紫/
    未接続・不明=グレー）は右チップの背景にのみ適用し、左側2チップは中立色
    `CHIP_NEUTRAL_COLOR_ARGB`（0xFF424242）で統一する。テキスト色は左側2チップが白系
    （`CHIP_NEUTRAL_TEXT_COLOR_ARGB`=0xFFFFFFFF）、右チップは`statusTextColorArgb(state)`
    （通信中の明るいアンバー背景のみ濃色0xFF212121、それ以外は白）でコントラストを確保する。
  - `ModifiersBuilders.Semantics`でcontentDescriptionを設定し、状態をスクリーンリーダーで読み上げ
    可能にする（BL-025）。
  - `android:icon="@mipmap/ic_launcher_wear"`をTileサービス自体へ明示指定し、Tile追加ピッカーの
    アイコンをComplicationピッカーと統一済み（BL-068。ピッカー表示とTile内表示を別サイズにする
    ことはAndroid/Wear OSの仕様上不可能と判明したため、両者は同一リソースを使う）。
  - `TileService.onTileRequest`はGuavaの`SettableFuture`でコルーチン結果をブリッジしている
    （Tiles APIのレスポンスタイムアウト制約を避けるため、既存のDataItemスナップショットで即座に
    応答しつつmobile側へ状態取得リクエストを送信する設計、BL-061）。
- `core.display.SesameTileContent`（Android非依存）: 状態→表示文言・アイコン・背景色・テキスト色の
  マッピングと、中立チップ色`CHIP_NEUTRAL_COLOR_ARGB`。`statusLabel`/`actionLabel`は`isAllDevices`
  パラメータ（デフォルト`false`）を持ち、全デバイス選択時は「全施錠中」等の文言に切り替わる。
- `core.display.SesameTileActions`（Android非依存）: Tile状態→提示コマンドの決定。MIXED状態はタップで
  「全施錠」を提示する（迷ったら安全側の方針、UNLOCKのみ確認画面を挟む既存UXと組み合わせて安全側は
  確認不要のまま維持、BL-071）。
- `core.display.SesameDeviceTargets`（Android非依存）: 操作対象の選択肢と解決規則。`choices`は
  デバイス選択画面の項目を表示順に返す（0台ならデモ用デバイスのみ、2台以上なら先頭に「全デバイス」、
  表示名が空欄ならuuidをラベルにする）。`displayName`は割り当て済みuuidの表示名（デモ・全デバイスは
  固定文言、一覧に無ければuuid）、`targetUuids`はコマンド送信先の展開（全デバイスなら登録順の全uuid）。
  登録済みデバイス一覧の取得（wearはDataItem）は呼び出し側が担う。
- 上記3つは当初wearモジュール（`wear.tile` / `wear.action.SesameActionTargetResolver` /
  `wear.ui.DeviceSelectionScreen` / `SesameTileStateResolver`）にあったが、mobileのホーム画面
  ウィジェット（BL-121以降）とTileで表示・操作ルールを食い違わせないよう、両者が参照できるcoreへ
  移した（BL-119。mobileはwearへ依存できない）。wear側は参照先を付け替えただけで挙動は変えておらず、
  固定文言の対象（デモ・全デバイス）ではDataItemを読まない点も移設前と同じ。
- `wear.tile.SesameTileStateResolver`（Tile/Complication共通）: 対象uuidが
  `ALL_DEVICES_TARGET_UUID`の場合は登録済み全デバイスの状態を`TileDisplayStateResolver
  .resolveAggregate`で集約し、それ以外は単一デバイスの状態を解決する（BL-071でSesameTileService/
  SesameComplicationDataSourceServiceの重複ロジックを集約）。**状態取得のリクエストは送らない**
  （BL-142で廃止）。戻り値は`SesameTileStatus`（表示状態と、状態文言の下へ添える1行）。
- `wear.tile.TileConfigurationActivity` / `TileDeviceAssignmentStore`: Tileインスタンス
  （`tileId`、Wear Tilesがタイル追加ごとに割り振る固有ID）ごとに操作対象デバイスのuuidを
  `SharedPreferences`（機密情報を含まないため非暗号化）へ永続化する「複数Tileインスタンス方式」
  （BL-052）。選択画面は`wear.ui.DeviceSelectionScreen`（Tile/Complication共通）を用いる。
- **未確認事項**: 上記の多重インスタンス対応はコード側では実装済みだが、実機（Wear OS 7の
  Pixel Watch、2026-08-30確認）では同一Tileを2つ以上追加できない。Watch上のタイル編集の「＋」でも
  スマホのPixel Watchアプリのタイル管理画面でも、追加済みのタイルはチェック済み扱いで再選択でき
  ない。`wear/AndroidManifest.xml`のTileService定義に多重追加を妨げる指定はなく、Tiles APIにも
  多重追加を制御するフラグは存在しないため、Wear OS側のタイル（ウィジェット）管理UIの制約と
  推測されるが未確認。このため実運用では単一Tileのデバイス切り替えが主な使い方となる。
  その単一Tileでのデバイス切り替えは2026-09-05に実機（Pixel Watch + Sesame 5実機2台以上）で
  確認済みで、デバイス名・状態表示・施錠/解錠の対象デバイス・成否ハプティクス・「全デバイス」
  一括操作と混在表示のいずれも選択デバイスと一致した（BL-055完了）。

### Complication

- `wear.complication.SesameComplicationDataSourceService`（`ComplicationDataSourceService`実装、
  `SUPPORTED_TYPES=SHORT_TEXT,LONG_TEXT`）: `SesameTileStateResolver`を共用し、要求された
  `ComplicationType`に応じて`ShortTextComplicationData`（状態文言のみ）または
  `LongTextComplicationData`（デバイス名＋状態文言）で状態を表示する（BL-009, BL-071, BL-072）。
  タップは設定画面（デバイス変更）を開くだけで施錠/解錠のコマンド送信は行わないため、
  全デバイス選択時も集約状態の表示のみとする（操作導線はTile側に限定、BL-073）。
  状態解決は`withTimeout`（10秒）で打ち切り、例外・タイムアウト時も「不明」表示へフォールバック
  して必ず`listener.onComplicationData`を呼ぶ（データ未返却＝空欄表示を作らないための防御、
  BL-072）。切り分け用に`Log`（TAG=`SesameComplication`）で要求された型とデータ返却有無を出力する。
- `wear.complication.SesameComplicationContent`（Android非依存）: `TileDisplayState`→短い表示文言
  （MIXED用の🔀アイコン・「一部解錠」ラベルを含む。BL-071、文言はBL-104で短縮）と、`LONG_TEXT`枠向けの
  「デバイス名＋状態文言」（BL-072）。
- `wear.complication.ComplicationConfigurationActivity` / `ComplicationDeviceAssignmentStore`:
  Complicationインスタンス（`complicationInstanceId`）ごとに対象デバイスを永続化する
  「複数Complicationインスタンス方式」（BL-054）。設定画面の起動経路は2つある（BL-073）。
  (1) 文字盤のComplicationピッカーでデータソースを選んだ直後にシステムが起動する標準の設定導線
  （マニフェストのサービス側`PROVIDER_CONFIG_ACTION` meta-dataと、Activity側の同じactionを持つ
  intent-filter＋`category.PROVIDER_CONFIG`）。対象は`EXTRA_CONFIG_COMPLICATION_ID`でInt値として
  渡され、選択完了時に`RESULT_OK`を返さないと文字盤側がデータソース選択自体をキャンセル扱いにする。
  (2) `SesameComplicationDataSourceService`が付与する`tapAction`。未設定枠の「タップして設定」と、
  設定済み枠のデバイス変更の双方で使う。この2経路が無かった当初は「未設定枠を1度タップする」
  1経路しか無く、枠ごとに別デバイスを割り当てることも、割り当て済みの枠を変更することも
  できなかった（BL-073）。
- 更新契機は、(1) `SesameStatusListenerService`がDataItem変更を受けて発行する
  `ComplicationDataSourceUpdateRequester.requestUpdateAll()`、(2) `ComplicationConfigurationActivity`
  がデバイス割り当て後に発行する`requestUpdate(complicationInstanceId)`、(3) マニフェストの
  `UPDATE_PERIOD_SECONDS=600`による定期更新の3つ。(3)は更新要求が届かなかった場合でも表示が
  自己回復するための保険として、従来の`0`（定期更新なし）から変更した（BL-072）。
- **解消済みの不具合（BL-072）**: 文字盤のComplication枠へデバイスを割り当てても状態文言が
  表示されず空欄のままになる事象を2026-08-30の実機確認（Wear OS 7のPixel Watch）で検出していた。
  同一の`SesameTileStateResolver`を使うTile側は正しく表示できていたため、状態解決ロジックではなく
  Complication固有の要因（要求される`ComplicationType`、`onComplicationRequest`の非同期実装、
  更新契機）を疑い、実機ログなしでは切り分けられないことから上記3要因すべてへの防御的修正を実装し、
  2026-09-05の実機確認で状態文言の表示を確認した。
- **未確認事項（BL-073）**: 上記の設定導線2経路により、複数のComplication枠へ別々のデバイスを
  割り当てられること・割り当て済みの枠を変更できることの実機確認が残っている。

### 施錠/解錠操作画面

- `wear.action.SesameActionActivity`: Tileタップで起動。LOCKはワンタップ即送信、UNLOCKは確認画面
  （`SesameCommandConfirmation`が要求要否を判定）を経由してから送信する。確認画面は左＝
  「キャンセル」・右＝「施錠」または「解錠」の角丸チップ2つ（`SesameActionChip`、
  `Modifier.clip(RoundedCornerShape(12.dp))`の自作コンポーネント、Wear Compose Materialの円形
  `Button`は不使用）で、Tile側のチップと同じ配色・角丸半径を用いる（BL-070）。全デバイス選択時は
  ボタンラベルが「全施錠」「全解錠」に切り替わる（BL-071）。送信はFire-and-forget方式。
- `wear.action.SesameActionCommandParser`（Android非依存）: Intent Extra文字列→`SesameCommand`。
- `wear.action.SesameActionTargetResolver`（Android依存の薄いアダプタ）: コマンド送信・状態更新の対象
  uuid一覧を解決する。全デバイス時のみDataItemから登録済み一覧を読み、展開は
  `core.display.SesameDeviceTargets.targetUuids`へ委ねる（全デバイス時は登録済み全uuidのリスト、
  それ以外は単一uuid）。`SesameActionActivity`/
  `SesameStatusRefreshActivity`が全デバイス選択時にループで各デバイスへ個別にlock/unlock/
  status-requestメッセージを送信する（mobile側は既存の単一デバイス処理をそのままN回受けるだけで
  対応でき、mobile側の変更は不要だった、BL-071）。
- `wear.action.SesameStatusRefreshActivity`: `PATH_STATUS_REQUEST`をFire-and-forgetで送信するのみの
  軽量Activity（施錠/解錠は行わない）。Tileのデバイス名チップタップから起動する（BL-063）。
- 対象uuidがデモ用デバイス（`core.SesameDemoMode.DEMO_DEVICE_UUID`）の場合、`SesameActionActivity`は
  `applyDemoCommand`へ分岐してMessageClientへ一切送信せず、`SesameStatusRefreshActivity`も
  状態取得リクエストを送らない（後述「デモモード」、BL-109）。

### デモモード

資格情報が未登録でもTile・Complication・デバイス選択画面を一通り操作できるようにする仕組み
（BL-109）。Google Playのクローズドテスト（BL-106）で、Sesame 5実機・Hub 3・APIキーを持たない
テスターがアプリを操作できず、製品版アクセス申請フォームのパート1「テスターのエンゲージメント」に
実態を伴う回答ができない問題への対策として追加した。

- `core.SesameDemoMode`（Android非依存、ユニットテスト対象）: デモ用デバイスのuuid
  （`__demo_device__`。実デバイスのUUID形式とも`ALL_DEVICES_TARGET_UUID`とも衝突しない固定文字列）・
  表示名（「デモ」。Tileのデバイス名チップ（左列76dp、CAPTION2）に収まる上限
  `MAX_DISPLAY_NAME_CHARS` = 5文字以内。当初の「デモ（体験用）」7文字はチップの背景をはみ出して
  表示されていた、BL-115）・初期状態（施錠中）と、提示可否（`isAvailable`）・選択肢生成
  （`selectableDevices`）・表示状態（`displayState`）・コマンド適用後の状態（`nextIsLocked`）を定義する。
- 提示条件は「mobile側から同期された登録済みデバイスが0台」に限定する。1台でも登録されている場合は
  選択肢へ混ぜない（実際には施錠されていないのに施錠済みと誤認する事故を避けるため）。
- `wear.demo.DemoLockStateStore`: ダミー施錠状態をwear単体で永続化する。実デバイスが存在せず
  mobile側の関与がないため、DataItem経由の同期は使わない。機密情報を含まないため非暗号化の
  `SharedPreferences`（`TileDeviceAssignmentStore`と同方針）。
- `wear.ui.DeviceSelectionScreen`は0台時にデモ用デバイスのみを選択肢として表示し、見出し
  （「デモモード」）と説明文（「スマホで登録すると」「実際の鍵を操作できます」）を添える。
  文言は`wear.ui.DeviceSelectionContent`（Android非依存、ユニットテスト対象）が保持し、
  **1行あたりの文字数上限（`MAX_LINE_CHARS` = 11）以内の行へあらかじめ分割しておく**（BL-114）。
  当初は32文字の1文をそのまま`Text`へ渡し、折り返し位置を画面幅に委ねていたため、円形画面の
  左右の縁で行頭・行末の文字が見切れていた。上限は最小構成の円形端末（幅192dp）から
  ScalingLazyColumnの既定水平パディング（10dp）と本画面の水平パディング（12dp）を引いた
  残り幅148dpに、caption2（12sp）の全角文字が12.3文字並ぶ計算に基づく。
- `wear.tile.SesameTileStateResolver`はデモ用uuidのとき`DemoLockStateStore`から状態を解決する。
  スマホ接続状態・DataItemの鮮度に依存させず、ウォッチ単体で操作を体験できるようにしている
  （DISCONNECTED/UNKNOWNへ落ちない）。
- 施錠/解錠はローカル状態の書き換え・成功ハプティクス（実デバイス操作時と同じSUCCESSパターン）・
  Tile/Complicationの再描画要求のみで完結し、Sesame APIへも`MessageClient`へも一切送信しない。
- 実機検証（BL-115、2026-09-13、Pixel Watch 2 + Pixel 8 Pro）: デバイス選択画面の見出し・説明文・
  デモ用チップが円形画面の内側へ収まること、Tileのデバイス名チップに表示名が収まること、
  タイル右側タップ→解錠確認→`解錠中`／`タップで施錠`への遷移（`SesameTileService`のログで
  `state=UNLOCKED`を確認）を検証専用ビルド（`applicationId`を`com.sesamiwear.mobile.demotest`へ
  変更、コミットしない）で確認した。この検証で、表示名「デモ（体験用）」がTileのデバイス名チップの
  背景をはみ出していることが判明したため「デモ」へ短縮した。
- `wear.display.SesameDisplayUpdateRequester`: Tile/Complicationの再描画要求
  （`TileService.getUpdater` + `ComplicationDataSourceUpdateRequester`）を共通化したもの。
  `SesameStatusListenerService`とデモモードの双方から呼ぶ。
- 実機検証（BL-110、2026-09-13、Pixel Watch 2 + Pixel 8 Pro）: 登録済みデバイス0台の状態で、
  デバイス選択画面がデモ用デバイスのみを提示すること（「全デバイス」チップは`devices.size >= 2`の
  条件により非表示）、Tileが初期状態「施錠中／タップで解錠」を表示すること、タップ→解錠確認→
  「解錠中／タップで施錠」への遷移と再タップでの復帰、デバイス名チップのタップ
  （`SesameStatusRefreshActivity`）後もTileが崩れないこと、Complicationがデモ状態を表示し状態変更に
  追随すること（同一文字盤上の実デバイス用Complicationとは独立）を確認した。ハプティクスは
  `dumpsys vibrator_manager`の履歴で`[0, 80, 40, 80]`ms（`SesameHapticPlayer.SUCCESS_TIMINGS_MS`と
  一致）の再生完了を確認した。デモ中に通信が発生しないことは、wear成果物の要求パーミッションが
  `VIBRATE`のみで`INTERNET`を含まないことと、Data Layer送信ログが0件であることで確認した。
  mobile側へ1台登録するとデモの選択肢が消えて実デバイスのみになり、削除するとデモへ戻ることも
  確認した。検証は既存のPlay版アプリと登録済み資格情報へ影響を与えないよう、`applicationId`を
  `com.sesamiwear.mobile.demotest`へ変えた検証専用のデバッグビルドで実施し、終了後に両端末から
  アンインストールした（ビルド設定の変更はコミットしていない）。

### ハプティクス

施錠/解錠の成否を、画面を見なくても区別できるよう振動で通知する（BL-008 / BL-016、
ホーム画面ウィジェットはBL-129）。

- `core.haptics.HapticPattern`（Android非依存、ユニットテスト対象）: SUCCESS（短い振動2回）と
  FAILURE（長い振動1回）の2種類。波形（`timingsMillis`、`VibrationEffect.createWaveform`へ渡す
  時間の並び）もここで持つ。wearのTile経由とmobileのウィジェット経由で手触りを揃えるため、
  両者が参照できるcoreに置く（`SesameTileContent`と同方針、BL-119。mobileはwearへ依存できない）。
- `core.haptics.SesameHapticPatternResolver`（Android非依存、ユニットテスト対象）:
  `SesameCommandResult`→`HapticPattern`。wearがData Layer経由で受け取った結果に使う。
- `wear.haptics.SesameHapticPlayer` / `mobile.haptics.SesameHapticPlayer`
  （`Vibrator`/`VibratorManager`ベースの振動再生、Android依存のためユニットテスト対象外）:
  同じ実装を両モジュールが持つ。mobileはwearへ依存できず、Android依存コードはcoreへ置けないため、
  共通化できるのは波形の定義（`HapticPattern`）までになる。
- `mobile.widget.WidgetHapticResolver`（Android非依存、ユニットテスト対象、BL-129）:
  ウィジェットの施錠/解錠の実行結果（`List<Outcome>`）から鳴らすパターンを決める。
  1台でも`FAILURE`があれば`FAILURE`（一部だけ成功した状態を「成功」と伝えないため）、
  すべて`DEBOUNCED`なら鳴らさない（APIを呼んでおらず伝えるべき結果が無く、連打のたびに振動させると
  抑止の意味が薄れる。wear側も重複として無視した場合は結果を返さず振動しない、BL-062）、
  それ以外は`SUCCESS`。
- **状態取得（デバイス名のタップ）では振動しない。** wear側の状態取得もFire-and-forgetで結果を
  返さず振動しない（BL-061）ため、挙動を揃える。
- mobile側は`VIBRATE`権限を宣言する（wearは既に宣言済み）。端末が振動に対応していない、
  または設定で切られている場合は何も起きない（例外にはならない）。

### mobile/wearエントリポイント

- `mobile.MainActivity`: スマホ実行時は資格情報設定画面（`CredentialsSettingsScreen`）を表示する。
  ウォッチ実行時は`packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)`で判定し、
  explicit Intent（`Intent().setClassName(packageName, "com.sesamiwear.wear.MainActivity")`。
  mobileはwearへコンパイル時依存できないためクラス名文字列を使用）で`wear.MainActivity`へ委譲して
  `finish()`する（`ActivityNotFoundException`時はフォールバックで`finish()`のみ行う防御コード付き、
  BL-040, BL-066）。`wear.MainActivity`へのstartActivityには
  `Intent.FLAG_ACTIVITY_NEW_TASK`を明示付与する（BL-067）。
- `wear.MainActivity`: LAUNCHER intent-filterを持たない（`android:exported="false"`）。Tile/
  Complicationが主要導線のため独立起動は不要（BL-066）。
- `wear.MainActivity` / `SesameActionActivity` / `SesameStatusRefreshActivity` /
  `TileConfigurationActivity` / `ComplicationConfigurationActivity`はすべて
  `android:noHistory="true"`・`android:excludeFromRecents="true"`を持つ（BL-067。フォアグラウンドを
  外れた時点で即座に破棄しタスクに残留させないことで、`android:taskAffinity`未指定によるタスク
  共有からの意図しない画面露出・古いActivityインスタンス再利用を防ぐ）。

### Androidアイコンリソース

VectorDrawableベースのAdaptive Icon（BL-027）。背景色`#1E3A5F`（濃紺）は`mobile`/`wear`共通。
前景（`#C99A46`のゴールド）は「南京錠+ワイヤレス波」をモチーフとし、その周囲にコンプリケーション風
リング（下部に隙間、`trimPathEnd=0.72`/`trimPathOffset=0.39`）を配する。リング寸法は半径30・
ストローク幅4で、外周が中心(54,54)から32dpとなりAdaptive Iconのセーフゾーン（108dp viewport中心から
半径33dp）に収まる（BL-041）。

- ランチャーアイコン: `mobile/src/main/res/`の`ic_launcher_background.xml`/
  `ic_launcher_foreground.xml`/`ic_launcher.xml`/`ic_launcher_round.xml`。
  `mobile/AndroidManifest.xml`の`android:icon`/`android:roundIcon`から参照する。スマートフォンの
  ランチャーへ表示されるのはこのアイコンで、リング意匠も等倍でこちらに含める（BL-042）。
- ウォッチ側のランチャー・Tile/Complicationピッカー用: `ic_launcher_wear_background.xml`/
  `ic_launcher_wear_foreground.xml`/`ic_launcher_wear.xml`/`ic_launcher_wear_round.xml`。
  **`wear/src/main/res/`側に配置**している（BL-091で`mobile`側から移設した。dynamic feature構成では
  featureのマニフェストが参照するリソースをbase側へ置く必要があったが、独立モジュール化により
  その制約が解消したため）。`wear/AndroidManifest.xml`の`<application>`の`android:icon`/
  `android:roundIcon`と、TileService/ComplicationDataSourceServiceの`android:icon`から
  `@mipmap/ic_launcher_wear`の名前で参照する。
  - Tile追加ピッカーでの見え方に合わせ、`ic_launcher_wear_foreground.xml`の全パスを
    `<group android:scaleX="0.5" android:scaleY="0.5" android:pivotX="54" android:pivotY="54">`で
    包み中心基準50%縮小している（BL-068。Tile表示時アイコンとピッカーアイコンは同一リソースしか
    持てないため、両方に反映される）。ランチャーアイコン側はこの縮小を行わない。
- 図案はXMLパスの手書きによるものであり、視覚的な洗練度はデザイナーによる最終調整を前提としていない。
  Google Play Console提出に必要な高解像度アイコン画像（512x512 PNG）は、Adaptive Iconの図案を
  ラスタライズした`docs/store/images/play_store_icon_512.png`として用意済み。

### リリースビルド・署名・バージョン管理

- 署名設定（BL-028）: `mobile`/`wear`のbuild.gradle.ktsで`local.properties`（`.gitignore`対象）から
  `RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`を読み込み、
  存在する場合のみ`signingConfigs.release`を構築する。未設定時は`assembleDebug`/`assembleRelease`
  ともunsignedのまま成功する。Keystoreの実際の生成は完了済み（BL-032、人手検証）。
- ProGuard/R8（BL-029, BL-083, BL-090）: `mobile`と`wear`がそれぞれ`proguard-rules.pro`を持ち、
  kotlinx.serializationの`@Serializable`クラス・`$$serializer`・`serializer()`companionを保護する
  keepルールを置く。リリースビルドは両モジュールとも`isMinifyEnabled=true`。dynamic feature構成では
  base（`mobile`）側の設定がアプリ全体へ適用されていたが、独立モジュール化（BL-090）に伴い
  `wear/proguard-rules.pro`を追加した。あわせて`-assumenosideeffects`により
  `android.util.Log`の`d`/`v`呼び出しをリリースビルドから除去する（BL-083。出力内容に資格情報は
  含まれないが配布物へ内部状態を残さないための措置。障害調査に必要な`w`/`e`は残す）。
- `scripts/release-build.bat`（`scripts/release-build.ps1`への薄いエントリポイント、BL-035）:
  `scripts/version.properties`に現在のversionCode/versionNameを永続化。引数なし実行時はversionCode
  を1インクリメント、`-VersionCode`/`-VersionName`指定時はその値を固定使用する。`pwsh`優先、
  無ければWindows PowerShellへフォールバック（スクリプトファイルはUTF-8 BOM付き。BOM無しだと
  Windows PowerShell 5.1が日本語コメントを誤解釈し構文エラーになるため）。ビルド失敗時は
  `version.properties`を更新しない。

### Google Play配布方式

`mobile`と`wear`をいずれも`com.android.application`とし、同一の`applicationId`
（`com.sesamiwear.mobile`）を共有する**独立した2つのAAB**としてビルドする。Google Playでは
1つのストア掲載ページの中で、スマホ用AABを電話・タブレット系トラックへ、ウォッチ用AABを
**Wear OS専用トラック**へアップロードする（BL-090）。

この構成はGoogleの要件に基づく。Googleは「Wear OS APKs are separate from mobile APKs」
「You cannot use a single app bundle with a dynamic feature module for Wear OS」と明記しており、
単一App BundleへWear OSをdynamic featureとして同梱する構成をサポートしていない。また
Play Consoleは2023年3月以降、Wear OS向けリリースを専用トラックで公開することを必須としている。

- **旧構成（BL-036、失敗）**: `wear`を`com.android.dynamic-feature`にして`mobile`へ統合し、
  単一AABで配布しようとしていた。`wear/AndroidManifest.xml`の
  `uses-feature android:name="android.hardware.type.watch"`が`mobile`のマージ済みマニフェストへ
  取り込まれ、`android:required`の既定値`true`によってアプリ全体が腕時計必須と宣言される状態に
  なっていた。この結果Play Consoleは、(1)バンドル全体をWear OSアプリと分類して専用トラックを要求し、
  (2)スマートフォンを配信対象から除外する。ローカルの`adb install`はPlayのデバイスフィルタを
  通らないため、実機検証では表面化しなかった。`required="false"`を付ける回避策はGoogleが
  明示的に非サポートとしている（Android/Wear OS双方で動く単一APKは未サポート構成）。
- `wear/build.gradle.kts`は`applicationId`（mobileと同値）・`targetSdk`・`signingConfigs`・
  `versionCode`/`versionName`・`minifyEnabled`/`proguardFiles`を自前で持つ。署名は`mobile`と同じ
  Keystore（`local.properties`から読み込み）を使う。同一`applicationId`の成果物は同じ鍵で署名する
  必要があるため。
- `versionCode`は全フォームファクタで一意である必要がある（Googleの要件）。`mobile`は1始まり、
  `wear`は1001始まりの独立した系列とし、`scripts/version.properties`の`VERSION_CODE`/
  `WEAR_VERSION_CODE`で管理する。`versionName`は利用者から見たアプリのバージョンであり、
  掲載ページも1つであるため両者で共通とする。
- `wear`のソースは`mobile`のクラスを一切参照していなかったため、dynamic feature時代にAGPの制約で
  必須だった`implementation(project(":mobile"))`依存は削除した。`wear`は`:core`のみに依存する。
- guavaは`wear`が`implementation`で直接持つ。dynamic feature構成ではbase/feature間でクラスが
  重複し`ListenableFutureが2重定義`エラーになるため`compileOnly`にしていたが、成果物が分かれた
  以降はその問題が発生しない。`mobile`側に置いていた`wear`のためのguava依存は削除した。
- `wear/AndroidManifest.xml`は`dist:module`ブロックを持たない。
  `uses-feature android:name="android.hardware.type.watch"`は宣言したままとし、`required`属性は
  付けない（既定値`true`）。Google Playはこの宣言により当該成果物をWear OS向けと判定する。
- ウォッチ側のランチャーアイコンは`wear`の`MainActivity`が持つ（BL-091）。BL-066でLAUNCHER
  intent-filterを除去していたのは、baseモジュール（`mobile`）が常にウォッチへもインストールされ
  アイコンが2つ表示されるという単一AAB構成固有の問題への対処であり、分離後はウォッチ側に`mobile`が
  存在しないため復活させた。
- ビルド・インストールはモジュールごとに行う。ローカルビルドの実機インストールは、
  `ANDROID_SERIAL`環境変数でインストール先を1台へ固定し、スマホへは`:mobile:installDebug`、
  ウォッチへは`:wear:installDebug`を実行する（手順は`docs/INSTALL.md`）。同一`applicationId`の
  ため、1台のデバイスに両方をインストールすることはできない（後から入れた方が置き換える）。
- リリースAABは`scripts\release-build.bat`の1回の実行で両方が生成される（BL-093）。
- `minSdk=26`（`mobile`と統一。旧30）でも、Wear OS向けライブラリを用いたTileの表示・
  施錠/解錠がPixel Watch実機で動作することを確認済み（2026-08-30）。
- 「スマートフォンへインストール後、ペアリング済みのWearデバイスへ自動的にウォッチ用アプリを
  インストールする」機能（Google Playの自動プッシュインストール）が2成果物構成でも機能することは、
  Play Console経由の配信で確認済み（2026-09-06、BL-097）。各デバイスへ正しい成果物が配信され
  （スマホ=mobile / ウォッチ=wear、いずれも`installerPackageName=com.android.vending`）、
  ランチャー登録Activityは各端末1件ずつになる。

### GitHub公開対応

- LICENSE（MIT License、Copyright 2026 filderschoice）を新規作成し、READMEへライセンスセクション
  （pysesame3への謝辞含む）を追加した。pysesame3はプロトコル仕様（コマンドコード・署名アルゴリズム・
  レスポンス構造）を参照した独自実装であり、ソースコードの逐語的コピーではないため侵害リスクは
  ないと判断した（同リポジトリのライセンスがMIT Licenseであることも確認済み）。
- README.md / `docs/store/STORE_LISTING.md`へ「本アプリは個人による非公式アプリであり、CANDY HOUSE
  株式会社とは提携・協力関係にない」旨を明記した。
- アプリ名「Sesami Wear」の「Sesami」は、公式製品「Sesame」をもじった**意図的な造語**である
  （当初は綴りの誤りだったが、2026-09-15に造語として据え置くと決定した）。由来は「Sesameの日本語読み
  『セサミ』のローマ字表記」と「イタリア語でsesamo（ゴマ）の複数形がsesamiであり、複数台対応に
  かけたもの」の2点。非公式アプリであることを前面に置き、公式製品と取り違えないよう綴りを変えている
  という位置づけで、README.md冒頭の注記と`docs/store/STORE_LISTING.md`「非公式アプリについて」に
  記載している。商標「Sesame」をアプリ名へそのまま含めることによるGoogle Playのなりすまし・
  知的財産ポリシー上のリスクを避ける意味もある。
- `.github/CODEOWNERS`のプレースホルダー（テンプレート由来の実在しないチーム名）を実際のGitHub
  ユーザー名へ修正した。
- Public公開に向けた整備（BL-074〜BL-084）。git履歴の秘密情報スキャン（apikey/secretKey形状の
  16進32文字・実uuid・鍵ファイル・メールアドレス）を実施し、混入はダミー値とRFC 4493公開テスト
  ベクタのみであることを確認済み。
  - `SECURITY.md`（脆弱性報告の受付方針。対象範囲は本リポジトリのコードのみで、CANDY HOUSE社の
    Sesame本体・クラウドAPIは対象外。非公開の報告経路と、報告に資格情報を含めない依頼を明記）。
  - `.github/ISSUE_TEMPLATE/`（不具合報告・機能要望・使い方の質問のIssueフォームと`config.yml`）と
    `.github/PULL_REQUEST_TEMPLATE.md`（`CLAUDE.md`規定のPR説明構成＋品質ゲート・資格情報混入確認の
    チェックリスト）。不具合報告・質問のフォームには資格情報を含めていないことの必須チェックを置く。
  - `CONTRIBUTING.md`へ報告の受け付け方針を明記した（BL-087）。本アプリの実装はClaude Codeによる
    AI実装を主体として開発者本人が行う体制のため、コードの変更経路を開発者側へ一本化し、
    **外部からのPull Requestは受け付けない**。Issue（不具合報告・機能要望・使い方の質問・
    ドキュメントの誤り）と脆弱性の非公開報告は受け付け、対応は開発者が実装する。要望を実装するか
    どうかの判断基準（secretKeyをwear側へ持たせない設計と広告/解析SDK非導入の維持を含む）と、
    テンプレート由来の役割分担（セキュリティ担当等）をメンテナー1名兼務の実態へ修正した点は維持する。
    Issueフォームおよび`.github/PULL_REQUEST_TEMPLATE.md`もこの方針に合わせている（BL-088。
    PRテンプレートは開発者・AIエージェント専用）。
  - 利用者向けドキュメント`docs/USER_GUIDE.md`（操作ガイド）・`docs/SUPPORT.md`（アップデート内容の
    確認先と問い合わせ窓口）・`docs/RELEASE_NOTES.md`（バージョンごとの変更点）を新設し、
    `README.md`を「利用者向け導線→開発者向け情報」の構成へ再編した。
  - 記録先の使い分けを`CHANGELOG.md`（運用ルール・ドキュメント）／`docs/RELEASE_NOTES.md`
    （利用者向け変更点）／`EXECUTE.md`（コード修正）として明文化した。

### ストア掲載情報・プライバシーポリシー

`docs/store/STORE_LISTING.md` / `docs/store/PRIVACY_POLICY.md`（BL-030。BL-081で索引
`docs/store/README.md`を追加し、Play Consoleの入力項目・文字数上限との対応表と更新手順を定義。
掲載情報の原本をリポジトリ側に置き、Play Console側を直接書き換えない運用とする。
プライバシーポリシーはPublic公開後のGitHub上のURLをそのままPlay Consoleへ登録する方針）。単一アプリ
登録（上記「Google Play配布方式」参照）を前提に、アプリ名・短い説明・詳細な説明・カテゴリ案・
対象デバイスと、収集する情報（uuid/apikey/secretKey、利用者本人が入力しサーバー側では収集しない）・
保存方法（mobile側のEncryptedSharedPreferencesのみ）・送信先（CANDY HOUSE Sesame APIのみ、
広告/分析SDK不使用）・削除方法を記載している。問い合わせ先メールアドレスは確定済みの値を記載済み。
Play Console提出用の高解像度アイコン（512x512 PNG）は`docs/store/images/play_store_icon_512.png`
として用意済み。プライバシーポリシーの公開URLでのホスティングとPlay Consoleへの登録、および
Data safety（データ セーフティ）申告は2026-09-07に完了した（BL-033 / BL-086）。申告内容は
apikeyを「個人情報 > ユーザーID」、Sesameデバイスのuuidを「デバイスIDまたはその他のID」とし、
いずれも収集・共有の両方・目的はアプリの機能のみ。secretKeyは端末内での署名計算にのみ使われ
端末外へ送信されないため、Googleの「収集」の定義に該当せず申告対象外としている。Play Consoleへの
アップロードは内部テスト（BL-097）・クローズドテスト（BL-106）の両トラックで実施済み。
利用者向けの配布状況の正本は`docs/SUPPORT.md`「現在の配布状況」。

## 設計方針

### アーキテクチャ方針

- secretKeyは機密性が高いためWatch単体には保持させない（PLAN.md記載方針を踏襲）。
  - `mobile`: apikey/secretKey/uuidを保持し、AES-CMAC署名生成とSesame API呼び出しを担当する。
  - `wear`: Tile/AppからWearable Data Layer API（MessageClient）で「lock」「unlock」等の意図のみを
    `mobile`へ送信する。`mobile`が実行し、結果（成功/失敗）を`wear`へ返す。
  - `core`: `mobile`/`wear`双方から参照する非機密のプロトコル定義（`SesameWearProtocol`等）・
    暗号・APIクライアント・状態解決ロジックを配置する。secretKey等の機密値やAndroid依存コードは
    置かない。Tileとホーム画面ウィジェットで共有する表示・操作の判定（`core.display`）もここに置く
    （`mobile`は`wear`へ依存できないため、共有先は`core`しかない。BL-119）。
- スマートフォンのホーム画面ウィジェット（BL-121以降）は、Data Layerを経由せず`mobile`内で
  `SesameDeviceCommandExecutor`からSesame APIを呼ぶ。ウォッチ経由のコマンドも同じ実行口を通るため、
  資格情報の検索・署名・重複抑止・状態保存は1か所に集約されている。secretKeyを`wear`へ持たせない方針は
  変わらない（ウィジェットはsecretKeyを保持する`mobile`自身の中で完結する）。ウィジェットから成功した操作は
  DataItemでウォッチへベストエフォートで同期し、ウォッチ経由の成功はウィジェットの再描画を要求する。

### BLE直接操作の併用方針（採用決定、未実装）

Sesame Web APIの月間リクエスト上限（BL-141）に対する構造的な対策として、BLE（Bluetooth Low Energy）
での直接操作をWeb APIと**併用**する方針を採用した（BL-145、2026-09-18にユーザー決定）。
本節は調査の結論と、実装に着手する際の前提をまとめたもので、実装そのものは未着手（BL-150〜BL-154）。

#### 調査の結論（論点1〜5）

1. **鍵の同一性（最重要）**: 本アプリが保持するsecretKey（SESAME Biz由来、16進数32文字、BL-058）は、
   BLE操作に必要なsecret keyと**同一である確度が高い**。`meronepy/gomalock`のREADMEは`SECRET_KEY`を
   16進数32文字とし、入手元としてQRコードリーダー（マネージャー権限以上のQRから抽出）と
   SESAME Bizの**両方**を挙げており、両者は同じ値に収束する。`homy-newfs8/libsesame3bt-core`は
   Sesame 5で`set_keys("", SESAME_SECRET)`と公開鍵を空にしてsecretだけを渡す。
   **実機では未実証**のため、BL-150（人手検証）で先に確かめる。
   - 現行ヘルプの「secretKeyは16進数32文字です。Sesameアプリの『鍵をシェア』QRコードの値では
     ありません」は、QRの生の`sk`（base64・先頭1バイトがモデル番号）を指した表現であり、
     QRリーダーが**抽出したあと**の値とは矛盾しない。BL-150で同一性が確認できた場合も、
     この文言は「生のQRの値ではない」という意味で正しいため、変更は不要と判断する。
2. **クラウド依存**: 公式SDKのREADMEはAmplify（AWS Cognito）の初期化を「OS3の登録やクラウド機能を
   利用するには」必要としており、**既登録デバイスのBLE操作のみの最小構成は明記されていない**。
   非公式実装（gomalock / libsesame3bt-core / ha-sesame-ble）はいずれもクラウドへ一切接続せず
   BLEのみで施錠/解錠まで到達している。公式SDKを使う場合に初期化で要求されるかは、
   BL-151の着手時に実際に組み込んで確かめる。
3. **secretKeyの保持場所**: **現行方針を変更しない。** secretKeyは`mobile`のみが保持し、
   BLEの実行主体も`mobile`とする。`wear`は従来どおりData Layer経由でコマンドの意図だけを送る。
   この結果、**BLEはスマートフォンがSesameの電波圏内にあるときしか使えない**。
4. **権限と審査**: API 31以上は`BLUETOOTH_SCAN`（`android:usesPermissionFlags="neverForLocation"`を
   宣言）と`BLUETOOTH_CONNECT`で足り、位置情報権限は不要。minSdkが26のためAPI 30以下向けに
   `ACCESS_FINE_LOCATION`が必要だが、`android:maxSdkVersion="30"`を付けて旧端末限定にできる。
   `uses-feature android.hardware.bluetooth_le`は`required="false"`とする（BLE非搭載端末でも
   Web API経由で動くため、配信対象を狭めない）。Google Playのデータセーフティ申告と権限の
   用途説明の更新が必要（BL-154）。
5. **実装方式**: 公式SDK（`CANDY-HOUSE/SesameSDK_Android_with_DemoApp`、MIT、JitPack配布、
   JDK 17 / Android SDK 36 / minSdk 24。本アプリのminSdk 26と両立する）をJitPackで取り込むことを
   既定とする。自前実装より保守負担が小さいため。ただしAABサイズへの影響をBL-151の着手時に計測し、
   許容できない場合はBLE部分だけの自前実装へ切り替える（その場合もAES-CMACは
   `core.crypto.AesCmac`を流用できる）。`core`はAndroid非依存の制約があるため、
   BLE実装は`mobile`の新パッケージ（`mobile.ble`想定）へ置く。

#### 経路の優先順位と切り替え条件

施錠/解錠・状態取得のいずれも、次の順に試す。

1. **BLE**: Bluetoothが有効で、必要な権限が許可されており、対象デバイスがスキャンで見つかり、
   所定時間内に接続できた場合。
2. **Web API**: 上記以外のすべて（圏外、Bluetoothオフ、権限未許可、スキャン・接続の失敗）。

- **利用者は経路を意識しない。** 表示・操作・結果の見え方は経路によらず同じにする。
  どちらの経路で実行したかは`Log.w`相当の診断ログ（BL-139）にのみ残す。
- **実行時間の制限が最大の制約**（BL-137）。ウィジェットのタップは`FLAG_RECEIVER_FOREGROUND`により
  約10秒で打ち切られ、現状はSesame APIのタイムアウトを接続3秒・読み書き3秒・全体6秒、
  受信全体を`withTimeoutOrNull`の8秒で囲んでいる。「BLEを試して失敗→Web API」を直列に行うと
  この枠を超えるため、BLEの探索・接続に与える上限は合計2秒程度に抑える。
- 上記でも枠が厳しいため、**直近のスキャン結果（uuidごとに「最後にBLEで到達できた時刻」）を
  保存し、一定時間内に到達実績がある場合だけBLEを先に試す**設計を既定とする。到達実績が無ければ
  Web APIから始め、成功後にバックグラウンドでスキャンして到達実績を更新する。
- 状態取得はBLEなら上限を消費しないため、BLEで到達できる間は自動取得の再開（BL-142で廃止した
  鮮度ベースの取得）を検討できる。ただしBL-152の完了までは自動取得を再開しない。

#### 段階的移行案

| 段階 | 内容 | 項目 |
| --- | --- | --- |
| 1 | 保持中のsecretKeyでBLE接続・状態取得ができることを実機で確認する | BL-150（人手検証） |
| 2 | `mobile.ble`にBLEクライアントを実装する。デバッグビルドの隠し設定でBLE単体を検証できるようにし、この段階では経路の自動切り替えを入れない | BL-151 |
| 3 | 経路選択（BLE優先・Web APIフォールバック）を実装し、実行時間の制限内へ収める | BL-152 |
| 4 | BLE権限の要求UIとマニフェストを整備する | BL-153 |
| 5 | データセーフティ申告・ストア掲載情報・利用者向けドキュメントを更新する | BL-154（人手検証） |

`-PsesameApiBaseUrl`のようなモック差し替えがBLEには存在せず、**検証は実機必須**になる
（本リポジトリにCIは無く、ローカル実行が唯一の品質ゲート）。経路選択の判定ロジックは
Android非依存のクラスへ切り出し、ユニットテストで検証できる範囲を最大化する。

#### 参照する外部実装（2026-09-18時点、BL-146の調査結果）

- 公式: `CANDY-HOUSE/SesameSDK_Android_with_DemoApp`（Kotlin、MIT、更新継続、JitPack配布
  `com.github.CANDY-HOUSE.SesameSDK_Android_with_DemoApp:sesame-sdk:<version>`）。BLE実装は
  `sesame-sdk/src/main/java/co/candyhouse/sesame/ble/os3/`で、Sesame 5は`CHSesame5Device.kt`が担当。
  プロトコル定義は`ble/SesameProtocols.kt`、AES-CMACは`utils/aescmac/`配下。
  `VALIARK-jp/Pedal_Share`は、このSDKを自アプリへモジュールとして同梱した先行事例。
- 非公式（いずれもクラウド未接続でBLEのみで動作）: `meronepy/gomalock`（Python、MIT、最も読みやすい。
  必要な資格情報はBLEアドレスと16進数32文字のsecret keyのみ）、`homy-newfs8/libsesame3bt-core`
  （C++、MIT。サービスUUIDは`0000fd81-0000-1000-8000-00805f9b34fb`、Tx/Rxの各キャラクタリスティック
  経由で通信）、`bingxyz/ha-sesame-ble`（Python、MIT、SESAME 5 Proの実機で検証済み）、
  `lanpili/ha-sesame-local`、`Khronos31/home-assistant-candy-house-ble`、
  `zunda-pixel/sesame-swift`（Swift、Apache-2.0）。
- ライセンスはいずれもMITまたはApache-2.0で、参照・流用の障害は無い。

### モジュール構成・パッケージ方針

- ルートパッケージ: `com.sesamiwear`（`core` / `mobile` / `wear` 配下にサブパッケージ）。
- `mobile`: `com.android.application`。`applicationId=com.sesamiwear.mobile`、`minSdk=26`、
  `versionCode`は1始まりの系列。スマートフォン向け成果物。
- `wear`: `com.android.application`。`applicationId`は`mobile`と同値
  （`com.sesamiwear.mobile`）、`minSdk=26`、`versionCode`は1001始まりの独立系列（BL-090）。
  `AndroidManifest.xml`に`uses-feature android:name="android.hardware.type.watch"`
  （`required`属性を付けず既定値`true`）と
  `com.google.android.wearable.standalone=false`（スマホ連携必須アプリのため）を設定済み。
- debugビルドは`applicationIdSuffix=".debug"`・`versionNameSuffix="-debug"`を付け、Playストア版と
  同一端末へ併存インストールできるようにしている（BL-131）。Play版はPlayアプリ署名鍵で署名されて
  おり、ローカルのデバッグ署名では上書き更新できない（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。
  入れ替えるにはPlay版のアンインストールが必要で、`mobile`の保存済み資格情報が消えるため、
  検証のたびに再設定を強いる構成を避けた。`mobile`/`wear`の双方へ同じサフィックスを付ける必要が
  ある（Data Layer APIはノード間で`applicationId`と署名の一致を要求するため）。あわせて
  `src/debug/res/values/strings.xml`で`app_name`（`mobile`は`widget_label`も）を
  `Sesami Wear (debug)`へ上書きし、ランチャー・ウィジェット選択画面・ウォッチのアプリ一覧で
  Play版と見分けられるようにしている。リリースビルドには影響しない。
- `mobile`のdebugビルドは、Gradleプロパティ`-PsesameApiBaseUrl`でSesame APIの接続先を差し替えられる
  （BL-132）。`buildConfigField`の`SESAME_API_BASE_URL`を`SesameDeviceCommandExecutor.defaultApiClient`が
  `BuildConfig.DEBUG`かつ非空のときだけ`SesameApiClient`の`baseUrl`へ渡す。未指定時・リリースビルドでは
  空文字となり本番URLのままで、`buildConfig = true`を有効にしている。接続先の実体は
  `scripts/mock-sesame-api.py`（標準ライブラリのみ、状態はプロセス内メモリ、署名は検証しない）で、
  実Sesameデバイス・実資格情報が無いと作れない「施錠/解錠の成功」を再現し、そこを起点とする
  状態同期（ウィジェット⇔ウォッチのTile）の検証に使う。デバッグビルドのみ
  `src/debug/AndroidManifest.xml`で`usesCleartextTraffic="true"`を設定しており、リリースの通信は
  HTTPSのままである。
- 依存バージョンは`gradle/libs.versions.toml`（Version Catalog）で一元管理する
  （AGP 8.13.0 / Kotlin 2.0.21 / Compose BOM 2024.12.01 / Wear Compose 1.4.1 等）。
- `androidx.fragment:fragment`は本アプリのコードから直接使っていないが、`mobile`/`wear`の双方で
  `implementation`に明示し1.8.9へ固定している（BL-130）。推移的依存（`mobile`は
  `play-services-basement`、`wear`は`watchface-complications-data`→`preference`→`appcompat`）が
  1.1.0を解決しており、Google Play Consoleの技術的な品質で1.2.1以降への更新を求められたため。
  `play-services-basement`は最新版でも1.1.0を指定しており、Google Play開発者サービス側の更新では
  解消しない。最新安定版1.9.0は推移的に`kotlin-stdlib`をコンパイラ（2.0.21）より新しい2.1.20へ、
  `androidx.tracing`を2.0.0へ引き上げるため、配布中アプリへの影響を最小にする目的で、fragment以外の
  解決結果が変わらない1.8.9を採った（2026-09-16にユーザー判断）。推移的依存を明示で引き上げている
  ため、依存元ライブラリを更新する際は`dependencyInsight`で解決結果を確認し、不要になれば明示を外す。

### UI/UX方針（現状の実装内容）

- Tileはロック状態（施錠中/解錠中/通信中/未接続/不明/混在）をアイコン・色でひと目表示する。
- 施錠はワンタップ即実行、解錠は誤操作防止のため確認画面（左＝キャンセル、右＝施錠/解錠の角丸
  チップ2つ）を挟む。
- 通信中は明確な処理中表示（IN_PROGRESS状態）＋`CommandDebouncer`による2秒以内の重複コマンド無視で
  二重送信を防止する。
- 成功/失敗をハプティクスパターンで区別する。
- スマホ未接続時はTile上で明示し操作不可にする。
- Complicationで常時ロック状態を文字盤表示する。
- 登録済みデバイスが2台以上の場合、Tile/Complicationのデバイス選択に「全デバイス」を選べる
  （BL-071）。
- 登録済みデバイスが0台の場合は、デモ用デバイスのみを選択肢として提示し、Sesame実機を持たない
  利用者でもTile・Complicationの操作感を確認できるようにする（BL-109、上記「デモモード」）。
- スマートフォンのホーム画面ウィジェットは、Tileと同じ表示・操作ルールを使い、ウォッチを持たない利用者にも
  同じ操作体験を提供する（BL-121〜BL-123、上記「mobileホーム画面ウィジェット」）。

### 複数Sesameデバイス対応方針

「複数Tileインスタンス方式」を採用している（BL-046〜BL-054、実装済み。単一Tileでのデバイス
切り替えは2026-09-05に実機確認済み、BL-055）。
1つのTileが1台のSesameデバイス、または「全デバイス」に対応し、ユーザーがTileギャラリーから必要な
台数分のTileを追加する（Google Wear OSの標準的なマルチインスタンスパターン）。各Tileはそのデバイス
専用の表示名とロック状態のみをシンプルに表示し、タップで即座に対象デバイスへコマンド送信する。
Complicationも同様に、文字盤の複数スロットへそれぞれ異なるデバイスを設定する方式である。

Tile Configuration機構の技術的背景: `androidx.wear.tiles`（本プロジェクトは1.4.1系、
`RequestBuilders.TileRequest`/`ComplicationRequest`ベースの旧世代Tiles API。新世代
`androidx.wear.protolayout`への移行は対象外）には、Android AppWidgetの`android:configure`属性の
ような「タイル追加時に自動的に設定Activityを起動する」標準機構は存在しない（`TileService`の
`onTileAddEvent`等は通知目的のコールバックであり、バックグラウンドからのActivity自動起動は
Androidのポリシー上一般に許可されないため確実な設定導線にならない）。そのため「Tile自体がタップで
設定画面へ誘導する」パターンを採用している: `TileRequest.getTileId()`（Tileインスタンス固有のInt
ID）をキーとして選択デバイスの`uuid`をローカル永続化し（`TileDeviceAssignmentStore`）、未設定の
tileIdの場合はTile上に「タップして設定」等の誘導表示を出し、タップで`ActionBuilders.LaunchAction`
により`TileConfigurationActivity`（Intent extraで`tileId`を渡す）を起動する。Complicationも
`complicationInstanceId`で同じパターンを適用している。

## 非機能要件

- 性能: 未定義（現時点で計測対象の実装なし）。
- 信頼性: 通信失敗時はサイレント失敗を避け、Tile上に明示する（PLAN.md UX要件）。
- セキュリティ/プライバシー:
  - secretKey/apikey/uuidは`mobile`側のみで保持し、`EncryptedSharedPreferences`で暗号化保存する。
  - ログへの秘密情報出力を禁止する（`rules/guardrails-unified.v1.md` 3.3/4.2）。実装上、
    資格情報を扱うクラスは`android.util.Log`等のログ出力を一切使用していない。
  - 実資格情報を用いた検証はBACKLOGで`区分: 人手検証`として自動実行対象から除外している。
    AES-CMACの自動テストはRFC 4493公開テストベクタとダミー鍵のみを用いる。
    Sesame実機での疎通確認・Pixel Watch実機での一連の操作確認は完了済み（BL-010, BL-011）。

## 実装制約

### 技術制約

- Sesame API: 施錠/解錠（POST）はsecretKeyによるAES-CMAC署名付きコマンドが必須（素のJSON送信では
  通らない）。状態取得（GET）はx-api-keyヘッダーのみで可能。エンドポイント・署名生成の詳細は
  「実装済み機能要件 > Sesame APIクライアント」参照。仕様の一次情報源は参考実装pysesame3
  (<https://github.com/mochipon/pysesame3>)のソースコードであり、CANDY HOUSE公式ドキュメント
  そのものは未参照だが、実機疎通確認（BL-010）で最終確認済み。
- secretKeyは16進数文字列（32文字=16バイト）であり、Base64ではない（BL-058）。apikey/uuid/
  secretKeyはいずれも`biz.candyhouse.co`（SESAME Biz 開発者ページ）から取得する
  （`partners.candyhouse.co`ではない、BL-057, BL-059）。
- Wear Tilesの`LaunchAction`で起動するActivityは`android:exported="true"`が必須（BL-060。
  `exported="false"`のままだと`Activity constraints not met`でLaunchActionが機能しない）。
- Wear OSの配布制約: Googleは単一App BundleへWear OSアプリをdynamic featureとして同梱する構成を
  サポートしておらず、Wear OS向けリリースは専用トラックでの公開が必須である。そのため`mobile`と
  `wear`は同一`applicationId`の独立した2成果物とする（BL-090。詳細は「Google Play配布方式」参照）。
  `versionCode`は全フォームファクタで一意である必要があり、両モジュールで重複させられない。
  なおdynamic feature構成だった時期には、base/featureの`<application>`タグがマージされる制約により
  `<application>`直下の属性（`icon`等）を表示先ごとに変えられない（BL-068）、featureが参照する
  リソースをbase側へ置く必要がある（BL-043）といった制約があったが、独立モジュール化により
  いずれも解消している。
- **アプリを再インストールすると、文字盤のComplicationスロットにシステム側の不整合な状態が残り、
  データソースを割り当てても枠が空のままになることがある**（BL-101で判明）。原因はWear OSの
  システムサービス側で、文字盤編集の確定処理
  （`WatchFaceEditingSessionController.commitFavoriteAndNotifyListener` →
  `AndroidXWatchFaceEngine.switchTo`）が同一スロットIDに対する`NO_DATA`と`SHORT_TEXT`の重複で
  `IllegalArgumentException: Multiple entries with same key`を投げ、確定処理が中断する。
  この状態では`onComplicationRequest`が一度も呼ばれない。**ウォッチの再起動で解消する。**
  アプリ側の実装に問題はなく修正は不要だが、実機検証でComplicationが表示されない場合は
  まず再起動を試すこと。切り分けには`ComplicationConfigurationActivity`が出力する
  `SesameComplicationConfig`タグのログ（`Log.w`のためリリースビルドでも残る）が使える。
- **`@Serializable`クラスに`private companion object`を持たせてはならない**（BL-099で判明）。
  kotlinx.serializationは`@Serializable`クラスのcompanion objectへ`serializer()`を生成するため、
  companionを`private`にすると生成される`Companion`フィールドも`private`になり、他クラスからの
  シリアライズ時に`IllegalAccessError: tried to access private field ...Companion`が実行時に
  発生する。コンパイルは通り、AGP 8.7.3では顕在化していなかったが、AGP 8.13.0への更新で
  表面化した。クラス固有の定数はファイルプライベートのトップレベル`private const val`へ置く
  （`SesameCredentials.kt`の`AES_128_KEY_LENGTH_BYTES`が該当）。
- Google Playの対象APIレベル要件により`targetSdk`は36以上が必須（2026-09時点）。`targetSdk`は
  `compileSdk`を超えられないため両方を36とする。compileSdk 36はAGP 8.13.0以上でサポートされ
  （最大API 36.1）、AGP 8.13.0はGradle 8.13を要求する。AGP 8.7.3でもビルド自体は通るが
  「tested up to compileSdk = 35」の警告が出るため、ストア配布物のビルドには用いない。
- detekt: `LongMethod`（60行）・`TooManyFunctions`（クラス内関数数の実測上限10）に複数回抵触した
  実績があり、状態解決等のロジック追加時はクラス内に増やすのではなく別ファイルの新規object等へ
  切り出す設計を優先する（BL-063, BL-071）。
- Windows PowerShell 5.1（`powershell.exe`）はBOM無しUTF-8スクリプト内の日本語コメントを正しく
  解釈できない場合がある（`scripts/release-build.ps1`はBOM付きで対処済み、BL-035）。

### 運用制約

- 実機（Pixel Watch / Sesame 5 + Hub 3）を用いる検証と、実資格情報を用いるAPI疎通確認は
  自律ループ実行モードの自動実行対象外とし、`区分: 人手検証`としてBACKLOGへ計上する。
- 「Sesami」（アプリ名・`applicationId`の`com.sesamiwear.mobile`・パッケージ名・リポジトリ名・
  Googleグループ名）を「Sesame」の誤記として修正しない。アプリ名は意図的な造語であり（上記
  「GitHub公開対応」参照）、`applicationId`はGoogle Playで変更できない。一方、CANDY HOUSE社の製品・
  APIを指す箇所は「Sesame」と表記する。

## Copilot実装指示

- 初回実装時の出力要件・追加実装時の出力要件・要件トレーサビリティ要件は `CLAUDE.md` の
  該当セクション（「開発プロセス要件」「出力要件」）に従う。自律ループ実行モードでは
  `CLAUDE.md`「出力要件の読み替え」に従い、本文書とEXECUTE.md/BACKLOG.mdの更新をもって充足する。
<!-- COPILOT_RECORDS:END -->

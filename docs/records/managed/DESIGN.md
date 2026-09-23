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
  - **上限到達の解消を確認した（BL-141、2026-09-19、Pixel 8 Pro）。** プランの引き上げにより
    上限が解消され、ホーム画面ウィジェットからの状態参照と施錠が成功することをユーザーが実機で
    確認した。403が上限到達によるもので、apikeyの失効ではなかったことが裏付けられた
    （再発行は行っていない）。翌月のカウンタリセット待ちではなくプラン引き上げを選んでいる。
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

### セサミ本体の状態（電池残量・角度・経路）

施錠状態だけでなく、電池残量・サムターンの角度・最後に使った経路を保持して表示に使う（BL-166）。

- **電池残量はBLE専用の情報ではない。** Sesame Web APIの状態取得レスポンス（`core.api.SesameStatus`）も
  `batteryVoltage`と`position`を返しており、BL-166より前は捨てていただけだった。したがって
  電池・角度の表示は経路によらず成立する。
- 電圧から残量（%）への換算表は`core.SesameBatteryLevel`が持つ（`meronepy/gomalock`と同じ値で、
  公式アプリの表示に合わせた非線形な対応）。BLE経由・Web API経由のどちらも同じ換算を通るため、
  経路によって表示がずれない。もとは`mobile.ble.SesameBleMechStatus`にあったものを`core`へ移した。
- `core.SesameStatusSnapshot`へ`batteryPercentage` / `position` / `lastRoute`を追加した。
  分かった値だけを上書きする`merge(SesameStatusMeasurement)`を持ち、**分からなかった項目は
  前回の値を残す**。Web API経由の施錠/解錠は状態を返さないため、経路だけが更新される。
- BLE経由の施錠/解錠では、ログイン直後に届く`MECH_STATUS`から電池残量が分かるため一緒に更新する。
  **角度は使わない**（コマンドを送る前の値になるため。`SesameBleClient.CommandOutcome`のKDoc）。
- 「電池切れ間近」フラグ（BLEのみ取得可能）は持たない。残量（%）があれば表示の判断には足り、
  スナップショットの項目数を増やすとdetektの`LongParameterList`（上限7）に触れるため。
- 保存（`mobile.state.LockStateStore`）とDataItemの双方で、キーが無い場合は未取得として扱う。
  BL-166より前に保存された値・同期された値を読んでも壊れない。

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
  利用者が自分で対処できるかどうかで3つに分ける。`AUTH_OR_QUOTA`（HTTP 401 / 403 / 429。資格情報が
  拒否された、またはAPIの月間リクエスト上限に達した）、`BACKGROUND_RESTRICTED`（端末の
  「バックグラウンドデータの制限」でOSが従量制回線の通信を止めている、BL-192）、
  `COMMUNICATION`（それ以外のすべて。圏外・タイムアウト・名前解決失敗・想定外の応答）。
  分類は`core.api.SesameApiException.httpStatusCode`（BL-139）から決める。
  - `AUTH_OR_QUOTA`をさらに「資格情報の誤り」と「上限到達」へ分けることはできない。Sesame APIは
    どちらもHTTP 403（本文も同一）で返すためで、429が返るかどうかもCANDY HOUSE側の実装次第で未確認
    （BL-141）。そのため文言も両方を含む案内にする。
  - 文言は`shortLabel`（Tile・Complication向け、「認証エラー」「通信エラー」の5文字。過去に7文字の
    文言がタイル幅に収まらず末尾省略された事例があるため、BL-102 / BL-104）と`detailedLabel`
    （ウィジェット向け、「認証エラー（設定を確認）」「通信エラー（電波状況を確認）」）の2つを持つ。
  - `BACKGROUND_RESTRICTED`は、**応答を1度も受け取れていない**（`httpStatusCode`がnull）失敗のうち、
    `mobile.network.BackgroundDataRestriction`が「制限あり」を返したものだけに付ける（BL-192）。
    サーバから応答が返っている時点でOSの制限は掛かっていないため、ステータスコードを持つ失敗を
    端末設定のせいにしないための条件である。判定は`ConnectivityManager.getRestrictBackgroundStatus()`
    が`RESTRICT_BACKGROUND_STATUS_ENABLED`かどうかで、データセーバーとアプリごとの制限
    （`POLICY_REJECT_METERED_BACKGROUND`）のどちらもこの値で表れ、非従量制の回線では`DISABLED`に
    なる。前面にいるかどうかは含まれないが、前面なら通信が成功して分類自体を通らないため足りる。
    `shortLabel`は`COMMUNICATION`と同じ「通信エラー」にする（5文字では書き分けられず、
    書き分けても利用者が次に取る行動が変わらないため）。違いが出るのは`detailedLabel`
    （「通信エラー（端末の設定を確認）」）だけでよい。
  - 「全デバイス」対象では`worstOf`が集約する。宣言の早い順（`AUTH_OR_QUOTA` →
    `BACKGROUND_RESTRICTED` → `COMMUNICATION`）に優先する。利用者が対処できる失敗を、
    対処できない失敗に埋もれさせないため。
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
- **配色は端末のダークテーマ設定へ追随する**（BL-181、`mobile.ui.SesameTheme`）。
  もとは`MaterialTheme`へcolorSchemeを渡さずライト固定で、暗い部屋で画面全体が白く光り、
  ウィジェット（暗色固定）・Tile（黒背景）とも食い違っていた。
  **Material Youの動的カラーは使わない。** 施錠状態の色（緑／赤／紫、`SesameTileContent`）が
  壁紙由来の色と競合し、状態の読み取りを鈍らせるため。テーマは地の色だけを担う。
  Activityのウィンドウ側は`res/values/themes.xml`と`res/values-night/themes.xml`の
  `Theme.SesamiWear` / `Theme.SesamiWear.Dialog`（AppCompatを使わないため親はplatformの
  `Theme.Material`系）。システムバーのアイコン色は`MainActivity.applySystemBarIcons`が
  `isSystemInDarkTheme()`に応じて切り替える（BL-103の固定指定を置き換えたもの）。
- `mobile.credentials.CredentialsSettingsScreen`: 複数デバイスの一覧・追加・編集・削除ができる
  Compose画面（BL-049）。**構成は上部バー（`TopAppBar`）＋デバイスのカード一覧＋右下の追加ボタン
  （`ExtendedFloatingActionButton`）**（BL-177）。もとは見出し・API呼び出し回数・一覧・接続の設定・
  入力フォームが1画面へ縦に並び、文字ばかりで情報の区切りが見えなかった。現在の構成:
  - 画面全体が1つの`LazyColumn`で縦スクロールする。もとは`Column`の中に`LazyColumn`が入れ子に
    なっており、一覧の外側（入力フォーム）はスクロールできなかった。
  - 1台＝1枚の`Card`（`mobile.credentials.DeviceCard`）。行を3つに分け、(1) 施錠状態のアイコンと
    表示名、(2) 施錠状態・電池残量・角度、(3) 最後に取得した時刻（または失敗の理由）と経路、を出す。
    **1行へ詰め込まない**（詰め込むと端末の幅に収まらず折り返される）。行の組み立ては
    `core.display.SesameDeviceStatusLine.lines`（Android非依存、ユニットテスト対象）。
    編集・削除はカード内のアイコンボタン（`Icons.Default.Edit` / `Icons.Default.Delete`）。
  - **追加・編集は全画面ダイアログ**（`mobile.credentials.CredentialsEditorDialog`、BL-178）。
    `Dialog`＋`DialogProperties(usePlatformDefaultWidth = false)`で、上部に「×」（キャンセル）と
    「保存」を置く。`enabled = isInputValid`の制御は従来どおり（BL-024）。
    保存の完了は`Snackbar`で「保存しました」を出す（従来は`LaunchedEffect`＋`delay`の固定表示）。
  - **削除には確認ダイアログを挟む**（BL-179）。削除すると資格情報と残存状態（`RemovedDeviceCleaner`、
    BL-160）がすべて消え、再登録にはSESAME BizからsecretKeyを取り直す必要があるため。
  - 保存・削除と、それに伴う同期（ウォッチへのデバイス一覧、ウィジェットの再描画）は
    `mobile.credentials.CredentialsScreenController`が持つ。画面のComposableへ直接書くと
    detektの`LongMethod`（上限60行）を超えるため分離した。
  - 入力欄（表示名/uuid/apikey/secretKey）はラベルのみのシンプルな見た目とし、secretKey欄は
    `PasswordVisualTransformation`でマスキング表示する（BL-023, BL-059）。
  - uuid/apikey/secretKeyの3欄は`singleLine = true`とし、`KeyboardOptions`でASCIIキーボード
    （secretKeyは`KeyboardType.Password`）を既定にしたうえで、`onValueChange`で
    `CredentialsInputSanitizer`を通してから状態へ反映する（BL-112）。表示名欄は対象外。
  - **ヘルプ・操作の経路・Bluetooth権限は上部バーの設定メニュー（⋮、`mobile.credentials.SettingsMenu`）へ
    集約する**（BL-180）。3つとも「たまに開く設定」で、主画面に常時置くと情報量を押し上げるため。
    現在の経路の方針とBluetoothの許可状況はメニュー項目の副題に出し、メニューを開くだけで分かるようにする。
    Bluetoothの項目は、許可済みで求めるものが無いとき（`SesameBlePermissionPrompt.buttonLabel`がnull）は
    押せない状態表示になる。状態と文言の決定は`mobile.credentials.RoutePolicyState` /
    `BlePermissionState`（どちらも`remember`で状態を持つComposable関数）が担う。
  - 詳細な説明はヘルプの`AlertDialog`へ集約する（BL-057, BL-059）。
    ヘルプは**メニュー形式**で、項目一覧（`HelpMenuDialog`）と本文（`HelpTopicDialog`、「戻る」で
    一覧へ戻る）の2段構成にする（BL-113）。文言と並び順はAndroid非依存の
    `mobile.help.HelpContent`（`HelpTopic` / `HelpLink`、ユニットテスト対象）が保持し、
    (1)「値の取得方法」（`https://biz.candyhouse.co/biz/developer`（SESAME Biz 開発者ページ）へ
    遷移する`TextButton`＝`Intent.ACTION_VIEW`を含む。uuid・apikey・secretKeyはいずれもこの
    ページから取得する。Sesameアプリの「鍵をシェア」QRコードは使わない運用）、
    (2)「APIのリクエスト回数の上限」（BL-144）、
    (3)「Sesameが無くてもデモで試す」（ウォッチのタイルとホーム画面ウィジェットの両方の試し方、
    両者のデモは連動しないこと、登録後にデモのウィジェットが「タップして設定」へ戻ること）、
    (4)「登録後のウォッチでの使い方」、(5)「ホーム画面ウィジェットの使い方」（BL-124）、
    (6)「Bluetoothで届かないとき」（BL-192。セサミ公式アプリを終了する・Bluetoothの許可・
    経路の方針・Androidのスキャン回数の制限の4点を切り分けの順に並べる。公式アプリの案内は
    通知と同じ`SesameRouteLabel.OFFICIAL_APP_HINT`を共有する）、
    (7)「モバイル回線のときだけ失敗する」（BL-192。端末の「バックグラウンドデータの制限」が
    掛かっているとモバイル回線のバックグラウンド通信だけが失敗することと、その確認先。
    ウィジェットの表示文言`SesameStatusFailure.BACKGROUND_RESTRICTED.detailedLabel`と
    `COMMUNICATION.detailedLabel`の両方を本文へ含めることをユニットテストで固定し、
    利用者が表示と説明を結び付けられるようにする）の7項目を持つ。
    通信の困りごと（(6) と (7)）は末尾へまとめる。
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
  - 保存ボタンは`enabled = isInputValid`で制御する（BL-024）。デバイス0件時は
    「まだSesameが登録されていません。右下の『Sesameを追加』から登録してください。」を表示する
    （BL-056 / BL-177）。
  - `Modifier.safeDrawingPadding()`でステータスバー等のシステムUIとの重なりを防止（BL-045）。
  - 一覧の末尾に追加ボタンぶんの余白（72dp）を足し、最後のカードがボタンへ隠れないようにする。
  - **未確認事項**: `biz.candyhouse.co`は動的サイトのためWebFetchでの実ページ内容確認はできて
    おらず、公式ドキュメントの記述とユーザーからの実機確認報告のみを根拠にしている。

### 診断ログ（スマートフォン）

うまく動かないときに、利用者から内容を連携してもらって解析するための記録（BL-188）。
上部バーの設定メニュー（BL-180）から全画面ダイアログで開く。

- 記録するのは施錠・解錠・状態取得の**成功と失敗の両方**で、直近50件
  （2026-09-20のユーザー判断。失敗だけだと「そのとき何が起きていたか」の前後関係が分からないため）。
  1件は日時・操作・対象の表示名・経路・結果・失敗理由。
- **uuid・apikey・secretKeyは記録しない**（2026-09-20のユーザー判断）。対象は利用者が付けた
  表示名だけで示し、分からなければ「名前未設定」にする。そのまま貼って送っても資格情報が
  漏れない状態を保つため（`rules/guardrails-unified.v1.md` 3.3）。同名のデバイスは区別できないが、
  資格情報を外へ出さないことを優先する。**この性質は`SesameDiagnosticsLogTest`で固定している。**
- 組み立てと整形はAndroid非依存の`core.diagnostics`（`SesameDiagnosticsEntry` /
  `SesameDiagnosticsLog`）。記録の生成は`mobile.command.SesameCommandDiagnostics`が
  `SesameDeviceCommandExecutor`の結果から行う（実行口へ直接書かないのは、同ファイルの関数数が
  detektの上限に達しているため）。失敗の理由は、資格情報が使えないならその旨、そうでなければ
  直前に保存された`SesameStatusFailure`の短い文言から引く。
- 保存先は非暗号化SharedPreferences（機密を含まないため。`LockStateStore`と同方針）。
- 画面は「コピー」と「共有」を持つ。共有はAndroidの共有シートで、**送り先は利用者が選ぶ**
  （アプリからどこかへ自動送信はしない）。連携用の全文の先頭には、解析に要るアプリのバージョン・
  Androidのバージョン・機種名だけを添える（端末を特定できるシリアル・広告IDは載せない）。
- 実行口の引数は`SesameCommandGuard`（重複抑止＋診断ログ）へまとめている。
  `SesameDeviceCommandExecutor`の引数がdetektの`LongParameterList`の上限を超えるため。

### セサミの状態一覧（スマートフォン）

デバイスのカード一覧（BL-177）で、各デバイスの名前の下に状態を出す（BL-169）。
内容は「施錠中 ・ 電池85% ・ 角度42」と「3分前 ・ Bluetooth」の**2行**で、分かっていない項目は
出さない。何も分かっていなければ2行目の「未取得」だけになる。

- 組み立ては`core.display.SesameDeviceStatusLine`（Android非依存、ユニットテスト対象）。
  1行版（`label`）と2行版（`lines`）があり、カードは2行版を使う（BL-177。1行へ詰め込むと
  端末の幅に収まらず折り返されるため）。ウォッチの状態一覧（BL-170）とは`SesameStatusFreshness`・
  `SesameRouteLabel`を共用する。
- **経路はアイコンではなく語で書く**（「Bluetooth」「インターネット」）。この画面は表示領域に
  余裕があり、アイコンより語のほうが誤解が無いため（Tile・ウィジェットは逆にアイコン、BL-168）。
- **角度はこの画面でだけ出す。** セサミが返す生の値で利用者が意味を読み取りにくいため、
  既定では出さず、uuid・APIキーを直接入力するこの画面に限って情報量を許す。
- 画面が再開するたび（`ON_RESUME`）に読み直す。ウィジェット・ウォッチからの操作で変わった分を
  反映するため（BL-159と同じ理由）。
- **この表示のために状態取得のリクエストは送らない。** 最後に分かった値をそのまま出すだけで、
  Sesame Web APIのリクエスト回数を消費しない（BL-142の方針を維持する）。

### mobileホーム画面ウィジェット

スマートフォンのホーム画面から、wearのTileと同じ表示・操作ルールでSesameの状態を確認・操作するウィジェット
（BL-121で表示と設定、BL-122でタップ操作、BL-123でデモ、BL-124でヘルプ・ドキュメント）。Data Layerを経由せず、
mobile内の保存値と`mobile.command.SesameDeviceCommandExecutor`（BL-120）を使うため、ウォッチを持たない利用者も
使える。「ウィジェットとwearで機能差を大きくつけない」ことを方針とし、判定ロジックは`core.display`に1つだけ置く
（BL-119）。

- **wearのTileと揃えている点**:
  - 表示文言・状態アイコン・状態色・テキスト色（`core.display.SesameTileContent`）と、状態色を右下だけに使う構成
    （左に「更新」と「変更」、右上にデバイス名と経路アイコンの帯、右下に状態。BL-205 / BL-206）。
  - 操作ルール（`core.display.SesameTileActions`）: 施錠中→解錠、解錠中→施錠、一部解錠→全施錠、通信中・状態不明→操作なし。
  - 確認画面: 解錠のみ（`SesameCommandConfirmation`）、左＝キャンセル・右＝解錠／全解錠の並びと配色。
  - 選択肢と対象の展開（`core.display.SesameDeviceTargets`）: 2台以上で先頭に「全デバイス」、0台ならデモのみ、
    全デバイスは各uuidへ個別に実行。全デバイスの状態集約規則（1台でも未取得なら状態不明）。
  - デモ: 登録0台のときだけ提示し、Sesame APIへ送らない。確認画面の有無・状態文言は実デバイスと同じ。
  - 「更新」のタップは状態取得のみで、到達実績によらずBLEを試す（BL-204）。デバイス名の帯は表示専用（BL-210）。
    同一uuidへの2秒以内の重複は1回（`CommandDebouncer`を経路間で共有）。
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
  - サイズ: 既定はTile相当（4x2）で、**横2マス×縦1マスまで**縮められる（BL-128 / BL-174）。
    `SesameWidgetLayout`（Android非依存、ユニットテスト対象）が表示領域（dp）から
    `FULL`/`MEDIUM`/`COMPACT`を決め、`SesameWidget`は`SizeMode.Responsive`で候補サイズ
    （50x50 / 110x50 / 200x172）を提示して`LocalSize`を受け取る。
    `FULL`のしきい値は幅200dp・高さ172dpで、どちらかを下回れば`MEDIUM`以下。幅は「左列（96dp）＋
    間隔（6dp）＋状態表示」を横に並べて成立する下限、高さは状態アイコン・状態文言・最終取得時刻・
    操作文言の4行が入る下限（外周・内側のパディング28dpと4行分の約108dpで合計約136dp）に、
    デバイス名の帯（約26dp）と間隔（6dp）を足した約168dpから決めた（BL-205）。
    高さは当初100dpとしていたが、4x1（約128dp）でも`FULL`が選ばれて操作文言が縦に見切れたため、
    余裕を見て140dpへ引き上げ（BL-158）、BL-205で帯のぶん172dpへ引き上げた。既定の4x2は一般的な端末で
    約250dpになるため`FULL`のまま。
    `FULL`に届かない場合は幅だけで`MEDIUM`（110dp以上）と`COMPACT`（それ未満）を分ける。
  - `MEDIUM`（2マス×1マス相当、BL-174）は、左1マス（60dp）にデバイス名チップ（タップで状態取得）と
    「◀ ▶」（対象デバイスの順送り、BL-175）を縦に並べ、右1マスに状態アイコンと状態文言を出す。
    最終取得時刻・電池残量・操作文言は高さが足りないため出さない。タップの挙動は`FULL`と同じ
    （`WidgetTapAction`の判定どおり）。**高さ1マスのときだけ順送りにする**のは、選択画面を開く
    「変更」チップを置く余地が無いため（2026-09-20、ユーザー確認済み。`FULL`は従来どおり「変更」）。
  - `COMPACT`（1マス相当）は状態アイコンと状態文言だけを出し、デバイス名・「変更」・最終取得時刻・
    操作文言は出さない。未設定時の文言は「タップして設定」ではなく「設定」にする。
    **BL-174で縮小の下限を2マス×1マスへ上げたため、通常の操作でこの表示にはならない**が、
    `minResizeWidth`より狭い表示領域を渡すランチャーと、BL-174より前に1マスで置かれた既存の
    インスタンスのために保険として残している（2026-09-20、ユーザー確認済み）。
  - **ウィジェット一覧へは「4 × 2」と「2 × 1」の2種類を並べる**（BL-186）。Androidは1つの
    `appwidget-provider`へ初期サイズを1つしか持たせられないため、置いた直後から小さく使いたい
    利用者のために2つ目のproviderを宣言する（`sesame_widget_small_info.xml`、
    `targetCellWidth=2` / `targetCellHeight=1`）。表示・操作は完全に同じで、違うのは初期サイズだけ。
    実装は`SesameWidgetSmall`（`SesameWidget`を継承しただけ）と`SesameWidgetSmallReceiver`。
    **同じ`GlanceAppWidget`の実装クラスを2つのレシーバへ割り当ててはいけない**
    （`GlanceAppWidgetManager`がクラスからレシーバを引く対応表を片方で上書きし、
    `getGlanceIds`が一方を取りこぼして再描画が届かなくなる）。そのため
    `SesameWidgetUpdater.updateAll`は両方のクラスを走査する。
    対象デバイスの割り当ては appWidgetId ごとで、appWidgetId は provider をまたいで一意のため
    2種類が混在しても取り違えは起きない。
  - `sesame_widget_info.xml`の`minWidth`/`minHeight`はAPI 30以下で既定の配置サイズを決めるため
    250x110dpのままにし、縮小の下限は`minResizeWidth`（110dp＝Androidの算出式 70dp×マス数−30dp
    による2マス分。`SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP`と同値）と`minResizeHeight`（50dp）で
    指定する。
  - **複数台を横に並べる表示（4x1等）は採らない**（2026-09-18、ユーザー確認済み）。wear側に無い機能に
    なり、以降の表示変更で両方を追従させる必要が出るため。
  - 対象デバイスの順送り（BL-175）: 巡回する並びは選択画面と同じ`SesameDeviceTargets.choices`で、
    端まで行ったら反対側へ回り込む。判定はAndroid非依存の`mobile.widget.WidgetDeviceCycle`
    （ユニットテスト対象）が持ち、受信は`WidgetCommandReceiver`の`ACTION_CYCLE_DEVICE`。
    割り当てを保存して当該インスタンスだけを再描画する。**Sesame Web APIは呼ばない**
    （保存済みの状態を出すだけで、月間リクエスト回数を消費しない）。
    割り当て済みのデバイスが選択肢から消えている場合（削除済み）は先頭の選択肢へ戻す。

- 実装方式はJetpack Glance（`androidx.glance:glance-appwidget` 1.2.0）。Glanceは推移的に
  `work-runtime` 2.7.1（`room-runtime` 2.2.5・`sqlite` 2.1.0を伴う）を持ち込むため、`work-runtime`を
  明示して2.10.5へ引き上げている（2026-09-16にユーザー判断）。2.11系は`kotlin-stdlib`をコンパイラ
  （2.0.21）より新しい2.1.20へ上げるため採らない（BL-130と同じ基準）。Glanceの導入で既存依存の版が
  変わるのは`compose-runtime`の1.7.6→1.7.8のみ。
- Glanceの部品は3ファイルに分かれる（BL-174）。`SesameWidget`（`GlanceAppWidget`本体と`FULL`/`COMPACT`の
  並べ方）、`SesameWidgetMedium`（`MEDIUM`の左1マスと右1マス）、`SesameWidgetChips`（両者が共用する
  チップ・文字スタイル・寸法の定数）。1ファイルへ置くとdetektの`TooManyFunctions`（上限11）に達するため。
  `WidgetUnlockConfirmActivity`も同じ面の部品として`SesameWidgetChips`の色・角丸の定数を使う。
- `mobile.widget.SesameWidget`（`GlanceAppWidget`）/ `SesameWidgetReceiver`（`GlanceAppWidgetReceiver`）:
  構成はTileと揃え、左列（幅96dp）に「更新」チップと「変更」チップ（中立色
  `SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB`）、右側の上にデバイス名の帯（`SesameWidgetChips.NameHeader`、
  中立色、経路のベクターアイコンとデバイス名をこの順に横に並べる。BL-205 / BL-209）、その下の残り全域に状態アイコン・
  状態文言・操作文言を中央寄せで置き、状態色（`SesameTileContent.backgroundColorArgb`/`statusTextColorArgb`）は
  右下の状態表示にだけ使う。BL-205より前は左上がデバイス名チップ（タップで状態取得）だった。
  デバイス名の帯は表示専用でタップを受けず、背景を押せるチップより暗い`SesameTileContent.NAME_HEADER_COLOR_ARGB`
  （0xFF262626）にしてボタンと区別する（BL-210。BL-205〜BL-209の間は「更新」と同じ状態取得で、
  チップと同じ中立色だったためボタンに見えた）。
  背景は暗色（0xFF121212）で角丸16dp。未設定時は「タップして設定」のみを表示し、全面タップで選択画面を開く。
  `onDeleted`で割り当てを消す。PendingIntentをインスタンス・操作の種類ごとに区別するため、各Intentの
  dataへ操作名とappWidgetIdを入れる。
- タップ操作（BL-122）: 右側は`mobile.widget.WidgetTapAction.forModel`（Android非依存）で決める。
  提示コマンドは`core.display.SesameTileActions`、確認の要否は`SesameCommandConfirmation`（Tileと同じ）で、
  施錠中→解錠確認画面、解錠中→即施錠、一部解錠→即全施錠、通信中・状態不明→操作なし、未設定→選択画面。
  左側の「更新」（`MEDIUM`ではデバイス名）は状態取得（GET）のみ、「変更」は選択画面を開く。
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
- `mobile.state.RemovedDeviceCleaner`（Android依存、ユニットテスト対象外）: 資格情報を削除したデバイスの
  残存状態を消す（BL-160）。`CredentialsSettingsScreen`の削除操作から呼び、(1)`LockStateStore`のロック状態、
  (2)ホーム画面ウィジェットの対象デバイス割り当て、(3)ウォッチへ同期済みのDataItem
  （`SesameWearProtocol.statusDataItemPath`、`wear://`のURIで`deleteDataItems`）を消す。
  端末内の保存値は同期的に、DataItemの削除だけは呼び出し元のコルーチンスコープでベストエフォートに行う。
  消さないと、同じuuidを登録し直したときに一度も取得していない状態で削除前の施錠状態・失敗文言が表示される。
  ウィジェットの割り当ては、未登録uuidなら表示自体は「タップして設定」へ倒れるが、再登録で黙って
  結び付き直すのを避けるため消す。
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
  既定の配置サイズ（該当端末が無い）で、BL-149へ残していたが、いずれも該当機材が無く検証手段が無いため
  2026-09-22にユーザー判断でBL-149をクローズし、未確認のまま運用する。
- エミュレータ検証（BL-182 / BL-183 / BL-184 / BL-185 / BL-186、2026-09-20、
  Androidエミュレータ `nocompanion` = Pixel 6相当 / Android 15）: BL-173〜BL-181の表示・操作を
  Claude Codeがadb経由のUI操作で確認した。**実機ではなくエミュレータ単独**で行っている
  （実機が手元に無いためユーザーが指定。Wear OSのシステムイメージは入っていない）。
  実資格情報・実Sesameデバイスは使わず、ダミー資格情報2台
  （Entrance=`aaaa1111` / Garage=`bbbb2222`）とモックAPI（BL-132、`10.0.2.2:8080`）で状態を作っている。
  確認できたのは次のとおり。
  (1) ウィジェットは横2マス×縦1マスまで縮められ、**それ未満へは縮まらない**
  （右ハンドルを画面左端まで引いても2マスで止まる）。
  (2) 2マス×1マスで左に「デバイス名」と「◀ ▶」、右にアイコンと状態文言が省略・見切れなく出る
  （当初は「▶」が描画されずデバイス名も省略されていた。BL-183 / BL-184として修正し再確認済み）。
  (3) 「◀」「▶」で対象が選択画面と同じ順に切り替わり、端で回り込む
  （全デバイス → Entrance → Garage）。**切り替えでモックAPIへのリクエストは1件も発生しない。**
  (4) 4x2（FULL）は従来どおり左列に「変更」が出て、タップで選択画面が開く。
  (5) アプリ画面が上部バー＋カード一覧＋追加ボタンの構成になり、状態が
  「施錠中 ・ 電池100% ・ 角度0」と「1分前 ・ 🌐 インターネット」の2行へ分かれて折り返しが無い。
  (6) 追加・編集が全画面ダイアログで開き、入力が不正な間は「保存」が押せず、保存すると
  一覧へ反映されて「保存しました」がSnackbarで出る。
  (7) 削除で確認ダイアログが出て、キャンセルすると消えない。
  (8) 上部バーの⋮メニューに「操作の経路」「Bluetoothで直接操作」「ヘルプ」が並び、
  副題に現在の方針（自動（Bluetooth優先））と許可状況（Bluetooth：未許可（インターネット経由で動作中））が出る。
  経路を切り替えると保存され、メニューの副題が「常にインターネット経由」へ変わる
  （当初はダイアログの行頭の文字が左端で欠けていた。BL-185として修正し再確認済み）。
  (9) 経路のベクターアイコン（BL-176）が、アプリ画面のカードでは語と併記、ウィジェットのFULLでは
  「たった今 🔋100%」と同じ行の先頭に出る（**行は増えていない**）。
  (10) 端末をダークテーマにするとアプリ画面が暗色になり、ステータスバーの時刻・電池が判読できる。
  (11) ウィジェット一覧に「Sesami Wear（小）2 × 1」と「Sesami Wear 4 × 2」が並び、
  小さい方を置くと最初から2マス×1マスで配置される。2種類を同時に置いても別々のデバイスを
  対象にでき、状態取得の結果が双方へ反映される（BL-186）。
  **未確認のまま残るのは、ウォッチ側（Tile・Complication・状態一覧）の🔗／🌐**
  （Wear OSのシステムイメージが無く、スマートフォンとのペア設定を要するため
  エミュレータ単独では作れない）**と、Bluetooth経由（🔗／Bluetoothアイコン）を伴う表示**
  （実Sesameデバイスが必要。BL-165に含む）。
- 実機検証（BL-156 / BL-157 / BL-158 / BL-159 / BL-160、2026-09-18、Pixel 8 Pro + Pixel Watch 2）:
  BL-149で起票した5件の修正を、Claude Codeがadb経由で確認した。実資格情報は使わず、ダミー資格情報2台
  （DevA=`MOCKA1` / DevB=`MOKB2`。uuidは`input text`が1文字落とした実際の値）とモックAPI（BL-132）で
  状態を作っている。ウォッチのタイルカルーセルは`input swipe`で移動できないため、Tileのタップは
  `SesameActionActivity`（exported）を`am start`で直接起動して再現した。確認できたのは次のとおり。
  (1) BL-157: 「全デバイス」の施錠でモックへPOSTが2件届き、`lock_states`の2台が同一ミリ秒
  （施錠時1789722340853 / ...854、解錠時は両方1789722371145）で更新された。修正前は片方だけが
  更新され、もう一方は古い状態と`lastFailure`が残っていた。
  (2) BL-156: ウィジェットのタップ（アプリはバックグラウンド）でモックへPOSTが届き、
  `Vibrator: Took 0 ms to wait for the actuator activation.`と`waitForComplete`が2回記録された。
  `Ignoring incoming vibration`は0件（修正前は施錠・解錠のたびに記録されていた）。
  (3) BL-158: ウィジェットを幅4マス×高さ1マスへ縮めるとCOMPACT（アイコンと状態文言のみ）で描画され、
  操作文言の見切れは無い。未設定時の文言も「設定」になる。
  (4) BL-159: 設定画面を開いたまま（`force-stop`せず）ホームへ退避し、ウォッチから「全デバイス」を
  操作してから開き直すと、表示が「23 回」から「25 回」へ更新された（SharedPreferencesの値と一致）。
  (5) BL-160: DevAを削除すると`lock_states`から`MOCKA1`だけが消え（`MOKB2`は残る）、
  ウィジェットの割り当てが`{"66":"MOCKA1"}`から`{}`になり、ウィジェットの表示が未設定へ戻った。
  DevBの削除で`MOKB2`も消えた。ウォッチのDataItem削除は成功時に出力が無いため、
  `DataLayerBestEffort`の失敗ログが1件も出ないことをもって確認としている（DataItemの中身は未確認）。
  後始末として、ダミー資格情報の削除、検証用ウィジェットの撤去、BL-149の検証で残っていた
  孤立ロック状態（`MCKUD` / `MOCKUUID1` / デモ）の削除、モックサーバーの停止、ウォッチの
  `screen_off_timeout`の復元（30000）、本番URLでのデバッグ版の入れ直しを行った。
- 以下の実機検証記録に出てくる経路アイコン📶 / ☁は**当時の表示**で、現在は🔗 / 🌐へ
  変更されている（BL-173、2026-09-20）。記録は当時観測した内容のまま残す。
- 実機検証（BL-172 / BL-165、2026-09-20、Pixel 8 Pro + Pixel Watch 2）: BL-166〜BL-171の経路表示・状態表示と、
  BL-153の権限表示・BL-167の経路方針を、Claude Codeがadb経由のUI操作で確認した。実資格情報・実Sesameデバイスは
  使わず、ダミー資格情報4台（Genkan=`alphadev1` / Kaisha=`bravodev2` / Souko=`charlie3` / Kura=`delta4`）と
  モックAPI（BL-132）で状態を作っている。確認できたのは次のとおり。
  (1) Tileの右チップは「🔒施錠中 / ☁たった今 / タップで解錠」「🔓解錠中 / ☁たった今 / タップで施錠」
  「🔒施錠中 / ☁認証エラー / タップで解錠」のいずれも、アイコン・状態文言・経路つき最終取得時刻・操作文言の
  4要素が円形画面のセーフエリアへ省略・見切れなく収まる。「☁認証エラー」はアイコン＋5文字で、
  最長ケースとして挙がっていた「📶23時間前」と同じ文字数のため、幅の要件は満たしている。
  (2) ウィジェットのFULL（4x2）は「☁たった今 🔋100%」を1行で表示し、状態文言・操作文言と合わせて3行に収まる。
  12文字の「認証エラー（設定を確認） 🔋100%」も1行で、省略・見切れは無い。
  (3) 幅4マス×高さ1マス（既定のグリッドで約134dp）と1x1ではCOMPACT（アイコンと状態文言のみ）で描画され、
  操作文言を持たないため見切れは起きない（BL-158の修正が維持されている）。
  (4) スマートフォンのデバイス一覧は「施錠中 ・ 電池100% ・ 角度0 ・ たった今 ・ インターネット」を表示する。
  Pixel 8 Proの既定のフォントサイズでは画面幅に収まらず2行へ折り返すが、省略・見切れは無い。
  (5) ウォッチのアプリ本体は「セサミの状態」の一覧になり、1台3行（デバイス名 / 「🔒施錠中 🔋100%」 /
  「☁1分前」）で描画される。4台登録すると縦スクロールで全台を確認できる。角度は出ない（BL-169 / BL-170）。
  状態が無いデバイスは「❓状態不明 / 未取得」になる。
  (6) 経路の方針を「常にインターネット経由」にすると、到達確認のスキャンを行わなくなる。到達実績の無い
  デバイス（`charlie3` / `delta4`）を含む「全デバイス」の状態取得でも`dumpsys bluetooth_manager`の
  スキャン回数は増えず、方針が「自動」のときだけ1回1.5秒のスキャン（`probeMillis`＝1,500と一致）が
  記録された。表示は☁のまま変わらない。
  (7) ウォッチのアプリ本体を開いてもモックAPIへリクエストは1件も届かず、「今月のAPI呼び出し」も31回のまま
  増えない（BL-170の方針どおり、この画面から状態取得を送っていない）。
  (8) BL-165の(4)については、`pm revoke`でBLUETOOTH_SCAN / BLUETOOTH_CONNECTを取り消すと、デバッグ用
  ブロードキャスト（`SesameBleDebugReceiver`）が`status result=PERMISSION_DENIED`を返し、同じ状態で
  ウィジェットの状態取得はWeb API経由で成功した。権限がある状態では同じ経路が`result=NOT_FOUND`を返し、
  BLEスキャン自体は動いている。
  (9) BL-165の(5)のうち表示は、資格情報設定画面が権限なしで「Bluetooth：未許可（インターネット経由で
  動作中）」＋「設定」、権限ありで「Bluetooth：許可済み（近くにいるときは直接操作します）」へ変わる。
  説明ダイアログからの許可操作そのものは、adbの`pm grant`で代替したため未確認。
  未確認のまま残るのは、📶（BLE経由）のアイコンを伴う表示と、BLEで届かなかったときのトースト
  （BL-172の(7)）である。`SesameBleAccess`は**直近にBLEで到達できた実績があるデバイスにだけBLEを試す**
  設計のため、実Sesameデバイスが無いと到達実績を作れず、`onFallbackToWebApi`（トースト）の経路へ入らない。
  いずれも実Sesameデバイスを要するためBL-165へ残す。
  後始末として、ダミー資格情報4台の削除、経路の方針の「自動」への復帰、Bluetooth権限の取り消し、
  検証用ウィジェットとTileの撤去、モックサーバーの停止、ウォッチの`screen_off_timeout`の復元（30000）、
  本番URLでのデバッグ版の入れ直しを行った。
- 実機検証（BL-165 / BL-182、2026-09-20夜、Pixel 8 Pro + Pixel Watch 2 + 登録済みのSesame 5 2台）:
  実Sesameデバイスと実資格情報を要する範囲を、Claude Codeがadb経由で検証した（資格情報の入力は
  利用者本人が行い、エージェントは値を扱っていない）。デバッグ版は本番URLのまま（モック差し替えなし）。
  (1) BLE直接操作は成功する。`SesameBleDebugReceiver`経由で`op=status`が`result=SUCCESS`を返し、
  `isInLockRange=true` / `position=-180` / `batteryPercentage=100`を取得した。`op=unlock`（3,918ms）で
  `position=-270`（解錠域）、`op=lock`（2,689ms）で`position=-180`（施錠域）へ実際に動いた。
  (2) 所要時間の実測は、状態取得が2,437 / 2,992 / 3,140ms（間隔15秒）。**連続実行すると
  `CONNECTION_FAILED` / `NOT_FOUND`になる**ため、計測は間隔を空けて行う必要がある。
  (3) **設計値（BL-152）は実測に対して短すぎ、BLE経路は実運用で一度も選ばれていなかった。**
  到達確認（1,500ms）はSesameの真横でも6回中2回しか成功せず、成立しても本番の操作は1,800msで
  打ち切られて`route=BLE op=STATUS result=NG`となり、毎回Web APIへ倒れる。見直しはBL-189で実施した。
  (4) BLE経路はAPI呼び出し回数を消費しない。BLEの操作4回の前後でカウントは52のまま変化せず、
  実装上も`recordApiCall`はWeb API経路（`SesameApiAccess`）にだけ配線されている。
  (5) BL-153の権限UIは、説明ダイアログからの許可まで確認できた（`BLUETOOTH_SCAN` /
  `BLUETOOTH_CONNECT`が`granted=true, USER_SET`。前回は`pm grant`で代替していた部分）。
  (6) **BL-168のトーストは表示されない。** フォールバック自体は起きているが
  （`route=BLE op=STATUS result=NG`）、`NotificationService`が「Suppressing toast from package ...
  by user request.」として抑止する。アプリを前景にすると表示される（`Creating surface for consumer
  VRI[Toast]`）ため、原因は通知が無効な端末での背景トーストの抑止である。対応はBL-190。
  (7) BL-182の(5)のうち🌐は確認できた。Tileの右チップが「施錠中 / 🌐6分前 / タップで解錠」、
  ウォッチのアプリ本体の状態一覧が2台とも「🔒施錠中 🔋100%」「🌐n分前」で、いずれも省略・見切れは無い。
  未確認のまま残るのは、Complicationの経路アイコンと🔗（BLE経由）の表示、BL-165の(3)（機内モード）と
  (6)〜(9)（経路の切り替えとウィジェットの打ち切り）で、いずれもBL-189の修正を入れた実機での
  再検証が必要である。BL-165の(1)の「公式アプリの表示と一致すること」（電池残量100%・施錠中）も
  突き合わせが未実施。
- 実機検証（BL-189 / BL-190、2026-09-20夜、Pixel 8 Pro + Pixel Watch 2 + 登録済みのSesame 5 2台）:
  BL-189 / BL-190の修正を入れた実機で再検証した。
  (1) BLE経路が実際に選ばれて成功する（`route=BLE op=STATUS detail=SUCCESS` / `result=OK`）。
  この操作では「今月のAPI呼び出し回数」が増えない（56→56）。到達確認（4,000ms）は1回目で成功し、
  保存したアドレスへの直接接続で探索を飛ばせている。
  (2) 経路アイコンは、ウォッチの状態一覧が「玄関上＝🔗たった今」「玄関下＝🌐1時間前」、Tileの
  右チップが「施錠中 / 🔗たった今 / タップで解錠」で、いずれも省略・見切れが無い（BL-182(5)）。
  (3) 通知は両方向とも出る。経路を「常にインターネット経由」へ変えた操作で
  「操作の経路が変わりました」「玄関上：Bluetoothで届かないため、インターネット経由で操作します」、
  「自動」へ戻した操作で「玄関上：Bluetoothで直接操作できるようになりました」。
  チャネルは`route_change`（`importance=2`＝LOW、無音）、`AUTO_CANCEL`、通知IDは1001の上書き。
  (4) デバイスカードは「Bluetooth：圏内（11分前に確認）」「Bluetooth：未確認」を表示し、
  経路の行（「11分前 ・ Bluetooth」）と別の行に収まる。
  (5) ⋮メニューの副題は「オン（端末の設定で通知が許可されていません）」で、ダイアログの確定ボタンが
  「通知を許可する」になる。**検証中に、既定のオンのまま未許可の端末から許可へ進む導線が無いことが
  分かり**、確定ボタンを「オンにする」／「通知を許可する」／「オフにする」の3通りへ直した。
  (6) 通知をオフにすると出なくなる。オフの状態で経路がインターネット経由からBLEへ変わる操作を
  行っても通知は1件も出ない。同じ経路が続く間も出ない（Web API経由の操作を続けても、
  「今月のAPI呼び出し回数」だけが増えて通知は増えない）。デバイスカードは「Bluetooth：圏内」
  「Bluetooth：圏外（3分前に確認）」「Bluetooth：未確認」の3通りを実機で確認した。
  **BL-190の完了条件はすべて満たしたため、BACKLOGからは削除している。**
  (7) BL-165(3)の機内モードも確認した。**機内モードをオンにしてもWi-Fiは残る**ため、あわせて
  Wi-Fiも切り（`Active default network: none`、`ping`も到達不可）、その状態で`op=unlock`が
  `result=SUCCESS`となり、API呼び出し回数は67のまま増えなかった。施錠して戻した状態
  （`isInLockRange=true` / `position=-181`）も確認している。
  検証中に、**短時間にBLEスキャンを繰り返すとAndroidのスキャン制限に当たり`NOT_FOUND`が続く**
  ことも観測した（数分にわたり`lock result=NOT_FOUND`が返り、1分ほど間隔を空けると復帰した）。
  到達確認の回数設計に影響するためBL-191へ記録している。
  未確認のまま残るのは、ウィジェットのタップが打ち切られないこと（BL-189）、
  同じ経路が続く間は通知が出ないこと（`SesameRouteChangeTrackerTest`では検証済み）、
  Complicationの経路アイコン。
- 実機検証（BL-165 / BL-189 / BL-172 / BL-182、2026-09-21、Pixel 8 Pro + Pixel Watch 2 +
  登録済みのSesame 5 2台）: 未確認のまま残っていた項目をadb経由で検証した。
  (1) **ウィジェットのタップは打ち切られない**（BL-189 / BL-165(9)）。BLE経路が成立した状態で、
  解錠がタップから2,476ms（`route=BLE op=UNLOCK detail=SUCCESS`）、施錠が2,693ms
  （`op=LOCK detail=SUCCESS`）で完了し、`WidgetCommandReceiver`の打ち切りログ
  （`command timed out before the broadcast deadline`）は出ない。上限は9,000msで、実測は約3割。
  (2) **BLEの実測値は公式アプリの表示と一致する**（BL-165(1)）。`op=status`が
  `isInLockRange=true` / `position=-174` / `batteryPercentage=100`を返し、同時刻の公式アプリ
  （`co.candyhouse.sesame2`）の一覧も玄関上・玄関下とも「電池100%・施錠」で一致していた。
  (3) **セサミ公式アプリを前面で開いていると、BLEで見つけられないことがある**（新規の知見）。
  公式アプリを開いた10時09分から10時19分まで、探索はすべて`detail=NOT_FOUND`（到達確認も5回連続で
  失敗）だったが、`am force-stop`で止めた直後は`CONNECTION_FAILED`（＝探索では見つかる）に変わり、
  以降は15秒間隔で9回連続してBLE経路が成立した。Sesameが同時に1台としかつながれないことによる。
  ただし確実に妨げるわけではなく、公式アプリを前面で開いたままでも3回中2回は成功した。
  2026-09-20の検証で「真横でも6回中2回しか成功しない」と記録した挙動は、これが原因だった可能性が高い。
  利用者が自力で切り分けられないため、通知とヘルプで案内するようにした（BL-192の実装）。
  (4) **BL-191（失敗直後のBLE再試行）は、この回では確認できなかった。** 公式アプリを終了した状態では
  `CONNECTION_FAILED` / `LOGIN_FAILED`が1回も再現せず、判定に必要な失敗を作れなかった
  （同日11時に、失敗を意図的に作って確認した。次の実機検証の記録を参照）。
  (5) **Complicationの経路アイコンは確認できない**（BL-182(5)）。経路アイコンは`LONG_TEXT`枠の
  `detailLabel`にだけ載る実装（`SHORT_TEXT`は状態文言のみ）で、利用者のPixel Watch 2の文字盤には
  `LONG_TEXT`枠が無い。2026-09-21のユーザー判断で「確認不可」として打ち切った。
  なお、文字盤エディタはadbから開けない（Wear OS 5では長押しの合成イベントが効かず、
  `cmd watchface`相当のシェル入口も無い。スマホのPixel Watchアプリのカスタマイズにも
  コンプリケーションの項目が無い）。
  (6) **端末の「バックグラウンドデータの制限」が掛かっていると、モバイル回線ではウィジェットからの
  Web API呼び出しが約0.4秒で`GaiException`になる**（BL-192として起票）。
  `cmd netpolicy list restrict-background-blacklist`に本アプリのUIDが含まれていた。
  Wi-Fi（非従量制）では成功し、前景のアプリからも成功するため、切り分けが難しい。
  (7) 到達確認は`PROBE_INTERVAL_MILLIS`（5分）ごとにしか走らないため、短い間隔で操作を繰り返しても
  探索は行われない。検証で「何度操作しても到達実績が付かない」ように見える場合はこれが理由になる。
- 実機検証（BL-191、2026-09-21 11時台、Pixel 8 Pro + 登録済みのSesame 5 2台）: 失敗の理由によって
  到達実績を消すかどうかを分ける挙動（BL-191）を確認した。**BLEの失敗は実環境では狙って起こせない**
  ため（同日10時台の検証では9回連続で成功し、`CONNECTION_FAILED`を1回も作れなかった）、
  デバッグ版の非暗号化の保存値（`sesami_wear_ble_reachability.xml`）へ細工して各分岐を再現している。
  実資格情報は扱わず、操作はすべて状態取得（デバイス名タップ）で、施錠/解錠は行っていない。
  (1) **`CONNECTION_FAILED`では到達実績が残り、次の操作でもBLEを試す**（本項目の主眼）。
  セサミ公式アプリを起動して接続を掴ませ、`am force-stop`で終了させた直後に状態取得を行うと
  `route=BLE op=STATUS detail=CONNECTION_FAILED` / `result=NG`となり、保存値は
  `reachableAtEpochMillis`を保ったまま`consecutiveFailures`が1になった。**その直後の操作でも
  `route=BLE op=STATUS`のログが出て**、到達確認（最短5分）を待たずに再試行していることを確認した。
  (2) **`NOT_FOUND`では従来どおり到達実績を消し、次の操作はBLEを試さない**。保存済みBLEアドレスを
  存在しない値へ差し替えると、直接接続に失敗したうえ探索でも見つからず`detail=NOT_FOUND`となり、
  保存値から`reachableAtEpochMillis`が消えた。次の操作のログには`route=BLE`の行が1行も出ず、
  インターネット経由だけで処理された（`consecutiveFailures`は1のまま増えない）。
  (3) 到達確認（`recordProbe`）は成否によらず`consecutiveFailures`を0へ戻すことも保存値の推移で
  確認した。連続3回での打ち切り（`MAX_CONSECUTIVE_FAILURES`）は、3回目に達する前に`NOT_FOUND`で
  実績が消えるため実機では再現しておらず、ユニットテストでの検証にとどまる。
  (4) 検証中、インターネット経由の状態取得が`GaiException`で失敗する事象が再現した（BL-192、
  端末の「バックグラウンドデータの制限」による）。BLE側の判定には影響しない。
- 実機検証（BL-193 / BL-194、2026-09-21 12時台、Pixel 8 Pro + Pixel Watch 2 +
  登録済みのSesame 5 2台）: BLE経路の復帰性（BL-193）とバックグラウンドデータ制限の表示（BL-194）を
  確認した。実資格情報を用いる操作は**状態取得（ウィジェットのデバイス名タップ）だけ**に限り、
  施錠/解錠は一度も行っていない。分岐の再現には、デバッグ版の非暗号化の保存値
  （`sesami_wear_ble_reachability.xml`）と`cmd netpolicy`を使った。
  (1) **保存済みBLEアドレスを失った状態からBLE経路へ復帰できる**（BL-193の完了条件）。
  現用のアドレス（`ble_addresses`）と到達実績だけを消し、予備（`ble_last_addresses`）を残した
  状態で状態取得を行うと、到達確認が成功して`reachableAtEpochMillis`が付き、現用のアドレスが
  復元された（12:43:43・12:47:47の2回）。直後の操作では`route=BLE op=STATUS`のログが出て、
  BLEを先に試すようになることも確認した。
  (2) **失敗で現用のアドレスを捨てても予備は残る**（2段構えの実装）。12:44:09の
  `route=BLE op=STATUS detail=NOT_FOUND`の直後、`ble_addresses`から対象uuidが消える一方で
  `ble_last_addresses`には残っていた。
  (3) **予備のアドレスへ直接つなぐ分岐（`detail=LAST_KNOWN`）の成功は観測できていない。**
  今回成功した到達確認3回はすべて探索（`detail=SCAN`）で、探索が外れたとき（12:40:04・12:42:33・
  12:45:05）は予備のアドレスへの直接接続も失敗した（`detail=MISS`）。BL-191で観測した
  「直接接続は成功するのに探索だけ当たらない」状況は、同じ端末・同じ位置でも今回は再現しなかった。
  (4) **BLEが数分単位で届かなくなる時間帯がある**（BL-195として起票）。12:34:08の
  `detail=SUCCESS`の直後から12:42まで探索も直接接続も通らず、12:43:43に復帰し、12:44:09は
  `detail=NOT_FOUND`、12:47:47にまた復帰した。スマートフォンの位置は動かしていない。
  (5) **「通信エラー（端末の設定を確認）」はバックグラウンドデータの制限で実際に出る**（BL-194）。
  `cmd netpolicy add restrict-background-blacklist <UID>`でデバッグ版を制限し、Wi-Fiを切って
  モバイル回線（5G）だけにした状態で状態取得を行うと`status failed: GaiException`となり、
  保存値の`lastFailure`が`BACKGROUND_RESTRICTED`になった。ホーム画面ウィジェット（4x2）には
  「通信エラー（端末の設定を確認）」が1行で省略・見切れなく表示された。BLEで成功してしまうのを
  避けるため、経路設定を`WEB_API_ONLY`へ固定しても同じ結果になることを確認している。
  (6) **制限を解除すると同じ操作が成功する**（BL-194の完了条件）。
  `cmd netpolicy remove restrict-background-blacklist <UID>`の直後のタップで、
  `lastRoute=WEB_API`のまま成功し`lastFailure`が消えた（12:39:04）。
  (7) タイルは同じ失敗を「通信エラー」（5文字）と表示し、省略・見切れは無かった
  （ウォッチにはBL-192を含むデバッグ版を入れ直してから確認した。古いウォッチ側アプリは
  `BACKGROUND_RESTRICTED`を知らないため失敗なしとして扱う）。ヘルプの
  「モバイル回線のときだけ失敗する」も全文が折り返して表示された。Complicationの`LONG_TEXT`枠は
  利用者の文字盤に該当枠が無いため今回も未確認（BL-149と同じ理由）。
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
  minResizeWidth 110dp / minResizeHeight 50dpにより横2マス×縦1マスまで縮められる（BL-128 / BL-174）。
  `widgetFeatures=reconfigurable`、`initialLayout`はGlance既定の読み込み中レイアウト。

### ウィジェットの電池残量表示

ホーム画面ウィジェットのFULLレイアウトで、電池残量を**最終取得時刻と同じ行**へ併記する
（「3分前 🔋85%」、BL-171）。経路アイコンはBL-176でこの行の先頭へ置いたが、BL-205で
デバイス名の帯へ移した（下記「経路の可視化」）。

- **行は増やさない。** 高さ予算はほぼ使い切っており（BL-158。BL-205以降は最小サイズ172dpに対して
  約168dp）、行を足すと最小サイズで操作文言が見切れる。
- COMPACT（1マス相当）では出さない。アイコンと状態文言だけに絞るという目的が崩れるため。
- 「全デバイス」対象では**最も少ない台の値**を代表値にする。失敗（最悪を出す）・鮮度（最も古い値を
  出す）と同じく、利用者が対処すべき側を見せる。1台も分かっていなければ出さない。
- 電池の書き方（`🔋85%`）は`core.display.SesameTileContent.batteryLabel`が持ち、
  ウォッチの状態一覧（BL-170）と共通にしている。

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
- `KEY_BATTERY_PERCENTAGE` / `KEY_POSITION` / `KEY_LAST_ROUTE`: 電池残量（%）・サムターンの角度・
  最後に使った経路（BL-166）。いずれも後から追加した任意のキーで、値が無ければ載せない。
  旧バージョンのmobileが同期したDataItemにはキーが無いため、wear側は未取得として扱う（互換維持）。
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

### セサミの状態一覧（ウォッチのアプリ本体）

ウォッチのアプリ本体（`wear.MainActivity`）は初回実装以降「Sesami Wear」と表示するだけの
スタブだったが、**登録済みセサミの状態一覧**にした（BL-170）。Tileは操作のための面で表示余白が無く、
電池残量や経路までは入らないため、その置き場にする。

- 1台につき3行（デバイス名 / 「🔒施錠中 🔋85%」 / 「🔗3分前」）。
  円形画面では行が長いと行頭・行末が見切れるため（BL-114）、スマートフォンの1行表示
  （`core.display.SesameDeviceStatusLine`）はそのまま使わず、短い行へ分ける。
  各行が幅の目安（全角11文字相当）に収まることは`SesameStatusListContentTest`で固定している。
- **角度は出さない。** 生の値で意味を読み取りにくく、狭い画面では情報量が勝ちすぎるため
  （スマートフォンの設定画面でのみ出す、BL-169）。
- 施錠状態のアイコン・文言はTileと同じもの（`core.display.SesameTileContent`）を使い、
  同じ状態が別の言い方で出ないようにする。**スマートフォンとの接続状態は見ない**
  （この画面は最後に同期された値を見るためのもので、つながっていなければ値が古くなるだけであり、
  それは最終取得時刻の行が示す）。
- **この画面から状態取得のリクエストは送らない。** 開くたびにリクエストが飛ぶと、月間リクエスト
  上限（BL-141）を画面を開いた回数だけ消費するため（BL-142で自動取得を廃止した理由と同じ）。
- 画面が再開するたび（`ON_RESUME`）に読み直す。Tileで操作してからアプリへ戻ったときに追随させるため。
  このために`wear`へ`lifecycle-runtime-compose`を追加した（mobileが使っているものと同じ版で、
  compose-uiが推移的に持ち込む`lifecycle-runtime`と同一バージョンのため依存グラフは変わらない）。

### Tile

- `wear.tile.SesameTileService`（`androidx.wear.tiles.TileService`実装）: 現在のレイアウトは
  タイル端から`CONTAINER_PADDING_DP`（16dp）内側へ寄せた`Row`（左列＋右列）構成。
  - 左列（`LEFT_COLUMN_WIDTH_DP`=56dp固定、高さいっぱい。中身が2文字だけになったためBL-209で76dpから狭め、
    右列へ幅を回した）: 「更新」チップ（タップで
    `SesameStatusRefreshActivity`を起動し状態更新をリクエスト。スマホ側は到達実績によらずBLEを試す、BL-204）と
    デバイス変更チップ（タップで`TileConfigurationActivity`を起動）を`weight(1f)`で均等分割、間に
    `CHIP_SPACING_DP`（6dp）のSpacer。BL-206より前は「更新」の位置がデバイス名チップだった。
  - 右列上（BL-206）: デバイス名の帯（トップレベル関数`buildNameHeader`）。暗色の背景
    （`SesameTileContent.NAME_HEADER_COLOR_ARGB`=0xFF262626、押せるチップの0xFF424242より暗い。BL-210）に、経路の
    ベクターアイコン（12dp、白）とデバイス名（`CAPTION2`・1行・末尾省略）をこの順に横に並べる。
    アイコンを名前の前に置くのは、`Row`が子を先頭から順に測り後ろの子へ残り幅しか渡さないため
    （名前が先だと長い名前でアイコンが右端から見切れた。BL-209）。上下の余白は3dpに詰め、
    帯の高さは約22dp。表示専用でタップは受けない（BL-210。BL-206〜BL-209の間は「更新」と同じ状態取得だったが、
    ボタンに見えたうえ「更新」と機能が重複していたため、ユーザーの選択で外した）。
    経路アイコンはスマホのウィジェットと同じ`ic_route_bluetooth` / `ic_route_internet`（Material Icons、
    wearの`res/drawable`へ複製）を`onTileResourcesRequest`で登録し（`RESOURCES_VERSION`を"2"へ）、
    `LayoutElementBuilders.ColorFilter`で白に着色する。経路が分からなければアイコンは出さない。
  - 右列下（残り全域、`weight(1f)`）: 状態チップ。状態アイコン・状態文言・最終取得時刻・操作ラベルを
    中央寄せで表示し、タップで施錠/解錠コマンド（またはMIXED時は「全施錠」）を実行する。
    帯のぶん高さが減るため、状態アイコンの書体を`DISPLAY1`から`DISPLAY2`へ一段下げた（BL-206）。
    最終取得時刻の行には経路の絵文字を付けない（`SesameTileStatus.detailLabel`は経路を含まず、
    経路は`SesameTileStatus.route`で別に持つ）。
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

### 撮影モード（デバッグ版限定）

掲載スクリーンショット（BL-198）を撮るために、ウォッチ・ホーム画面ウィジェット・スマートフォンの
カード一覧へ撮影用のデバイスと状態を出すモード（BL-212）。**mobileのデバッグ版にだけ含まれ、
リリース版（Google Playへ配信するAAB）には実装を載せない。** デモモード（登録0台のときに体験用の
1台を出す、利用者向けの機能）とは別物で、開発者が撮影のためだけに使う。

- **実物から切り離す。** モード中は、登録済みデバイス（資格情報）・ロック状態・ウィジェットの割り当ての
  保存先を撮影用の別ファイル（`sesami_wear_showcase_*`、非暗号化のSharedPreferences）へ差し替える。
  登録済みの資格情報と実物の状態へは一切書き込まない。
- **操作を抑止する。** 施錠/解錠・状態取得の実行口（`mobile.command.SesameDeviceCommands`）を
  撮影用の`ShowcaseDeviceCommands`へ差し替え、Sesame Web APIもBLEも呼ばない。施錠/解錠は撮影用の
  状態を切り替えてウォッチ（DataItem）とウィジェットへ知らせるだけ、状態取得は取得時刻を今へ進めるだけ。
  撮影用でないuuid（実物）への操作は何もせず失敗を返す。撮影用の状態に直近の失敗を設定している場合は、
  失敗の表示を撮れるよう操作も失敗させる。
- **APIキー・秘密鍵を保存しない。** 撮影用のデバイスもカード一覧から追加・編集・削除できるが、
  保存の直前に`ShowcaseCredentialsKeyValueStore`がAPIキーと秘密鍵を固定のダミー値
  （`showcase-dummy` / 0が32桁）へ置き換える。実物の値を誤って入力しても残らない。
- **見本。** `ShowcasePresets`が3台（玄関・勝手口・ガレージ）を持つ。uuidは
  `00000000-0000-4000-8000-00000000000N`で実物と衝突せず、表示名はTileのチップ（5文字）に収まる。
- **ウォッチへの反映。** モードの切り替え（`ShowcaseSync.setActive`）で、そのときの保存先から
  デバイス一覧と各状態をDataItemへ同期し直す。抜けるときは撮影用デバイスの状態のDataItemを消し、
  実物の一覧・状態を同期し直す。ウォッチ側の変更は無い。スマートフォンから同期するため、
  `SesameWearDebugReceiver`（ウォッチ自身がDataItemを書く）で起きていた、スマートフォンの同期と
  衝突して表示名が反映されない問題（BL-198）が起きない。
- **Tileの対象デバイスはウォッチ側の保存値のため差し替わらない。** 実物のuuidを対象にしたTileは
  モード中に撮影用デバイスへ設定し直し、モードを抜けたら戻す必要がある（「全デバイス」は設定不要）。
- **ビルドタイプでの出し分け。** main側は差し替え口だけを持つ。
  - `mobile.state.SesameDeviceStores`: 画面・ウィジェットが開く保存先を選ぶ（`MainActivity`・
    `SesameWidgetRepository`・`DeviceCard`・`RemovedDeviceCleaner`が使う）。
  - `SesameDeviceCommandExecutorFactory.create`: `ShowcaseMode.commandsOrNull`が返せば撮影用の実行口を、
    返さなければ実物の実行口を返す。実物の実行口は`SesameDeviceStores`を通さず実物の保存先を直接開く。
  - `mobile.showcase.ShowcaseMode`は`src/debug`に実装、`src/release`に同名・同じ関数のスタブ
    （常に「撮影モードではない」・nullを返す）を置く。関数を増やすときは両方へ足す。
    リリース版のクラスはスタブの1つだけになる（`compileReleaseKotlin`の出力で確認）。
- 撮影用の実行口と保存先のテストは`src/testDebug`（デバッグ版の単体テストにだけ含まれる）。

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
本節は調査の結論と、実装に着手する際の前提をまとめたもので、実装そのものは未着手（BL-151〜BL-154）。
前提となっていた鍵の同一性の実証（BL-150）は2026-09-19に完了している。

#### 調査の結論（論点1〜5）

1. **鍵の同一性（最重要）**: 本アプリが保持するsecretKey（SESAME Biz由来、16進数32文字、BL-058）は、
   BLE操作に必要なsecret keyと**同一である**。`meronepy/gomalock`のREADMEは`SECRET_KEY`を
   16進数32文字とし、入手元としてQRコードリーダー（マネージャー権限以上のQRから抽出）と
   SESAME Bizの**両方**を挙げており、両者は同じ値に収束する。`homy-newfs8/libsesame3bt-core`は
   Sesame 5で`set_keys("", SESAME_SECRET)`と公開鍵を空にしてsecretだけを渡す。
   **2026-09-19にBL-150（人手検証）で実証済み**。本アプリへ登録済みのSesame 5に対し、
   同じsecretKeyをgomalock 2.1.0（Python 3.14.6、Windows 11）へ渡してBLE接続したところ、
   `is_logged_in`が`True`となり、角度（`position`）と電池残量（`battery_percentage`）を
   取得できた。施錠/解錠は実行していない（状態取得までで完了条件を満たすため）。
   - 併せて、**クラウドへ接続せずにBLEのみで完結する**ことも確認した（論点2）。PCの
     ネットワークを切断した状態で同じ手順を再実行し、同じ結果が得られている。
   - 手順とスクリプトは[../../BLE_KEY_VERIFICATION.md](../../BLE_KEY_VERIFICATION.md)にある。
     同文書のスクリプトはgomalock 2.1.0のAPIでそのまま動作する。
   - 現行ヘルプの「secretKeyは16進数32文字です。Sesameアプリの『鍵をシェア』QRコードの値では
     ありません」は、QRの生の`sk`（base64・先頭1バイトがモデル番号）を指した表現であり、
     QRリーダーが**抽出したあと**の値とは矛盾しない。同一性が確認できた現在も、この文言は
     「生のQRの値ではない」という意味で正しいため、変更は不要と判断する。
2. **クラウド依存**: 公式SDKのREADMEはAmplify（AWS Cognito）の初期化を「OS3の登録やクラウド機能を
   利用するには」必要としており、**既登録デバイスのBLE操作のみの最小構成は明記されていない**。
   非公式実装（gomalock / libsesame3bt-core / ha-sesame-ble）はいずれもクラウドへ一切接続せず
   BLEのみで施錠/解錠まで到達している。gomalockについては2026-09-19のBL-150で、ネットワーク
   切断下でも状態取得が成立することを実機で確認した。公式SDKについては2026-09-19のBL-151の計測で、
   **Amplify（AWS Cognito）がライブラリの`api`依存として不可避に付いてくる**ことを確認した
   （下記「公式SDK取り込みの実測」）。
3. **secretKeyの保持場所**: **現行方針を変更しない。** secretKeyは`mobile`のみが保持し、
   BLEの実行主体も`mobile`とする。`wear`は従来どおりData Layer経由でコマンドの意図だけを送る。
   この結果、**BLEはスマートフォンがSesameの電波圏内にあるときしか使えない**。
4. **権限と審査**: API 31以上は`BLUETOOTH_SCAN`（`android:usesPermissionFlags="neverForLocation"`を
   宣言）と`BLUETOOTH_CONNECT`で足り、位置情報権限は不要。minSdkが26のためAPI 30以下向けに
   `ACCESS_FINE_LOCATION`が必要だが、`android:maxSdkVersion="30"`を付けて旧端末限定にできる。
   `uses-feature android.hardware.bluetooth_le`は`required="false"`とする（BLE非搭載端末でも
   Web API経由で動くため、配信対象を狭めない）。Google Playのデータセーフティ申告と権限の
   用途説明の更新が必要（BL-154）。
5. **実装方式**: 当初は公式SDK（`CANDY-HOUSE/SesameSDK_Android_with_DemoApp`、MIT、JitPack配布）の
   取り込みを既定としていたが、2026-09-19のBL-151の計測により**取り込みを断念し、BLE部分の自前実装へ
   切り替える**（下記「公式SDK取り込みの実測」）。AES-CMACは`core.crypto.AesCmac`を流用できる。
   `core`はAndroid非依存の制約があるため、BLE実装は`mobile`の新パッケージ（`mobile.ble`）へ置く。

#### 公式SDK取り込みの実測（2026-09-19、BL-151）

BL-151の着手条件だった2点（AABサイズへの影響、Amplify初期化の要否）を実測した。**結論として公式SDKの
取り込みは断念する。** 計測は`v3.0.266-902`（JitPackのビルド成功を確認済みの最新タグ）で行い、計測用の
変更はすべて破棄している。

| 観点 | 実測結果 |
| --- | --- |
| JitPackの座標 | `com.github.CANDY-HOUSE:SesameSDK_Android_with_DemoApp:v3.0.266-902`。リポジトリ単位で1つのAARとして公開されており、**モジュール単位（`:sesame-sdk`）の座標は存在しない** |
| Amplify依存 | **不可避。** `sesame-sdk/build.gradle`が`api`で`com.amplifyframework:aws-api` / `aws-auth-cognito` / `core-kotlin`（いずれも2.38.1）を宣言し、JitPackが生成したPOMにも`compile`スコープで載る。加えて`aws-iot-device-sdk-java`・Room・Navigation・RxJava・Tyrus（WebSocket）も`api`依存。ライブラリの`BuildConfig`にAWS Cognitoのidentity pool ID・API Gatewayのキー・APIサーバーURLが埋め込まれる |
| 依存アーティファクト総量 | 95個 / 45.8MB → **200個 / 75.1MB**（+105個、+28.0MB、1.64倍）。本アプリのリリースAABは現状5.09MB（5,339,047バイト）であり、R8後でも同程度に収まる見込みは無い |
| ビルド可否 | **通らない。** (1) Amplifyが core library desugaring を要求する（本アプリは未導入）。(2) SDKとAWS系アーティファクトのKotlinメタデータが2.2.0で、本プロジェクトのKotlin 2.0.21コンパイラが読めない（`Module was compiled with an incompatible version of Kotlin`）。解消にはプロジェクト全体のKotlinを2.2系へ上げる必要があり、Composeコンパイラ・detekt 1.23.7・ktlint・`wear`モジュールへ波及する。あわせて`kotlin-stdlib`が2.0.21→2.2.10へ引き上げられ、「stdlibをコンパイラより新しくしない」方針（BL-130）に反する |

「クラウドへ接続しない」という本アプリの前提に対し、AWS Cognito・AWS IoT・API Gatewayの資格情報を含む
ライブラリを同梱することは、たとえ実行時に初期化しなくてもデータセーフティ申告の説明を難しくする。
サイズ・ビルド互換性・前提の3点がいずれも許容できないため、論点5の「許容できない場合はBLE部分だけの
自前実装へ切り替える」条件に該当すると判断した。

#### 経路の優先順位と切り替え条件

施錠/解錠・状態取得のいずれも、次の順に試す。

1. **BLE**: Bluetoothが有効で、必要な権限が許可されており、対象デバイスがスキャンで見つかり、
   所定時間内に接続できた場合。
2. **Web API**: 上記以外のすべて（圏外、Bluetoothオフ、権限未許可、スキャン・接続の失敗）。

- **経路は利用者へ見せる**（2026-09-19のユーザー判断で方針を変更、BL-168）。当初は
  「利用者は経路を意識しない」としていたが、BLEで届かなかったときに利用者が原因を切り分けられない
  という問題があった。表示の具体は下記「経路の可視化」を参照する。
- **経路の方針は利用者が選べる**（BL-167）。「自動（Bluetooth優先）」と「常にインターネット経由」の
  2つで、全デバイス共通。**「Bluetooth固定」は用意しない**（圏外で一切操作できなくなり、利用者が
  締め出されるため）。「常にインターネット経由」を選ぶと、BLEの探索・接続・到達確認をいずれも
  行わない（電力とスキャン回数を消費しない）。既定は「自動」。
  方針は`core.SesameRoutePolicy`、保存は`mobile.ble.SesameRoutePolicyStore`（非暗号化
  SharedPreferences、到達実績と同じファイルの別キー）。`SesameBleAccess`が操作のたびに読み直すため、
  設定画面で変えた直後から効く。
- **実行時間の制限が最大の制約**（BL-137）。ウィジェットのタップは`FLAG_RECEIVER_FOREGROUND`により
  約10秒で打ち切られ、現状はSesame APIのタイムアウトを接続3秒・読み書き3秒・全体6秒、
  受信全体を`withTimeoutOrNull`の8秒で囲んでいる。「BLEを試して失敗→Web API」を直列に行うと
  この枠を超えるため、BLEの探索・接続に与える上限は合計2秒程度に抑える。
- 上記でも枠が厳しいため、**直近のスキャン結果（uuidごとに「最後にBLEで到達できた時刻」）を
  保存し、一定時間内に到達実績がある場合だけBLEを先に試す**設計を既定とする。到達実績が無ければ
  Web APIから始め、成功後にバックグラウンドでスキャンして到達実績を更新する。
- **失敗しても、圏内にいた証拠がある間は到達実績を残す**（BL-191）。探索で見つからなかった場合と、
  見つかったが接続・ログインで失敗した場合を区別し、後者では次の操作でもBLEを試す。
  連続3回の失敗で打ち切る。詳細は下記「実行時間の設計値」を参照する。
- 状態取得はBLEなら上限を消費しないため、BLEで到達できる間は自動取得の再開（BL-142で廃止した
  鮮度ベースの取得）を検討できる。ただしBL-152の完了までは自動取得を再開しない。

#### BLEクライアントの実装（BL-151、2026-09-19）

公式SDKを採らない判断（上記「公式SDK取り込みの実測」）を受け、`mobile.ble`へ自前実装した。
プロトコルは`meronepy/gomalock`（Python、MIT）と`homy-newfs8/libsesame3bt-core`（C++、MIT）を参照している。
**この段階では経路の自動切り替えを行わない**（Web APIとの使い分けはBL-152）。

| クラス | 役割 | テスト |
| --- | --- | --- |
| `core.crypto.AesCcm` | RFC 3610のAES-CCM。AndroidのJCEが`AES/CCM/NoPadding`を持たないため`AES/ECB/NoPadding`の上へ自前で組む | RFC 3610の公開テストベクタ（`AesCcmTest`） |
| `mobile.ble.SesameBleProtocol` | サービス・キャラクタリスティックUUID、企業識別子、MTU、op code / item code / result code | 定数のみ |
| `mobile.ble.SesameBlePacketCodec` / `SesameBlePacketAssembler` | 20バイト単位のパケットへの分割と、受信パケットの組み立て。ヘッダ1バイトで「先頭 / 終端 / 終端が暗号化か」を表す | `SesameBlePacketCodecTest` |
| `mobile.ble.SesameBleSession` | セッション鍵の導出（`AesCmac(secretKey, sessionToken)`）と、送受信それぞれのカウンタを持つAES-CCM暗号化・復号 | `SesameBleSessionTest` |
| `mobile.ble.SesameBleMessage` | 応答・通知の解釈と、コマンド・履歴タグの組み立て | `SesameBleMessageTest` |
| `mobile.ble.SesameBleMechStatus` | `MECH_STATUS`通知（7バイト）の解釈。施錠/解錠範囲、角度、電池電圧・残量 | `SesameBleMechStatusTest` |
| `mobile.ble.SesameBleAdvertisement` | アドバタイズの製造者データ（19バイト）の解釈。uuidでの突き合わせに使う | `SesameBleAdvertisementTest` |
| `mobile.ble.SesameBlePermissions` | 必要な実行時権限の判定（API 31以上とAPI 30以下で異なる） | `SesameBlePermissionsTest` |
| `mobile.ble.SesameBleScanner` | サービスUUIDでフィルタしたスキャンと、uuid一致の判定 | Android依存のため対象外 |
| `mobile.ble.SesameBleConnection` | GATT接続・サービス探索・通知の有効化・分割送信 | Android依存のため対象外 |
| `mobile.ble.SesameBleMessageReader` | 受信チャネルから目的のメッセージが届くまで読み進める | Android依存のため対象外 |
| `mobile.ble.SesameBleClient` | スキャン → 接続 → ログイン → コマンド → 切断の統括 | Android依存のため対象外 |

設計上の判断:

- **AES-CCMは`core.crypto`へ置く。** BLE固有の要素を含まない暗号プリミティブで、`AesCmac`と同じ
  位置づけになるため。BLEの実装そのものは`mobile.ble`にあり、`core`のAndroid非依存制約は維持している。
- **接続は都度張って都度切る（常時接続しない）。** 操作はウィジェット・Tileのタップ起点で散発的であり、
  常時接続はバックグラウンド実行の制約と電力消費の両面で割に合わないため。
- **BLEアドレスは保存しない。** Androidでは端末ごと・起動ごとに変わりうるため、毎回スキャンして
  アドバタイズのuuidで突き合わせる。
- **状態取得は追加のコマンドを送らない。** ログイン直後にデバイスが`MECH_STATUS`を通知するため、
  それを待つだけで足りる。
- **復号に失敗した通知は捨て、受信カウンタを進めない。** ノイズや取りこぼしでカウンタがずれると
  以降すべて復号できなくなるため。
- **検証用の入口はデバッグビルドにのみ置く。** `mobile/src/debug`の`SesameBleDebugReceiver`（および
  同ソースセットのマニフェスト宣言）で、リリースビルドとPlayへ配信するAABには含まれない。
  adbから施錠/解錠を起動できるため`src/main`へ移してはならない。実機での検証手順はBL-165。

権限はまだマニフェストへ宣言していない（BL-153で追加する）。そのため現時点では
`SesameBleClient`が常に`PERMISSION_DENIED`を返す。

#### 経路選択の実装（BL-152、2026-09-19）

`mobile.command.SesameDeviceCommandExecutor`が、施錠/解錠（`execute`）と状態取得（`refreshStatus`）の
両方で経路を選ぶ（状態取得は下記「利用者の『更新』によるBLE確認」でBL-204により変更）。**利用者からは経路が見えない**（表示・操作・結果の見え方は同じで、どちらで実行したかは
`Log.w`の診断ログにのみ残る）。

| クラス | 役割 | テスト |
| --- | --- | --- |
| `mobile.ble.SesameBleReachability` | uuidごとの「最後にBLEで到達できた時刻」と「最後に到達確認をした時刻」を非暗号化SharedPreferencesへ保存し、BLEを先に試すか・到達確認をするかを決める | `SesameBleReachabilityTest` |
| `mobile.command.SesameBleAccess` | BLE経路の呼び出し口。到達実績の更新と診断ログもここで行う。BLEの実行そのものはラムダで受け取り、Android非依存のまま検証できる | `SesameDeviceCommandExecutorTest` |
| `mobile.command.SesameRouteAccess` | Web APIとBLEの経路一式をまとめた引数。既定値ではBLEが常に「使えない」を返す | 同上 |

判定の流れ:

1. 到達実績が`REACHABLE_TTL_MILLIS`（30分）以内にあるデバイスは**BLEを先に試す**。成功すれば
   Sesame Web APIを呼ばないため、月間リクエスト上限（BL-141）を消費せず、`ApiUsageCounter`も増えない。
2. 到達実績が無い、またはBLEが失敗した場合はWeb APIで実行する。BLEが失敗したときは到達実績を消し、
   次回はWeb APIから始める。
3. Web APIで実行するときは、**その通信と並行して**スキャンだけの到達確認を行う（接続もログインもしない）。
   `PROBE_INTERVAL_MILLIS`（15分）に1回までに絞る。並行して行うため、利用者から見た所要時間は変わらない。
   DESIGN当初案の「成功後にバックグラウンドでスキャン」を、`BroadcastReceiver`の`goAsync`の寿命の外へ
   処理を逃がさないために「同じコルーチンスコープ内で並行実行」へ変更している。
4. デモ用デバイスはBLE経路に一切触れない（`execute`が先に分岐するため）。
5. 資格情報を削除したデバイスの到達実績は`RemovedDeviceCleaner`が消す。

実行時間の設計値（`SesameDeviceCommandExecutorFactory.BLE_TIMEOUTS`、BL-189で実測へ合わせ直した）:

| 段階 | 上限 | 根拠 |
| --- | --- | --- |
| 全体 | 3,000ms | ウィジェットのタップは`WidgetCommandReceiver`の9秒で打ち切られ、Web APIだけで最大6秒（`SesameApiClient`のcallTimeout）かかる。BLEを試してから倒れても 3.0 + 6.0 = 9.0秒に収まる |
| スキャン | 1,200ms | 保存済みアドレスが無い・使えないときの保険。通常はこの経路を通らない |
| 接続 | 1,600ms | 保存済みアドレスへ直接つなぐ。探索を挟まないぶんをここへ回している |
| ログイン | 1,200ms | `INITIAL`通知の受信・`LOGIN`の応答まで |
| コマンド | 800ms | 暗号化コマンドの応答まで |
| 到達確認（全体） | 4,000ms | Web APIの通信（最大6秒）と並行して走るため、利用者から見た所要時間には影響しない。ただし完了を待つため、これを超えるとWeb APIが先に終わったときの待ちが伸びる |
| 到達確認の直接接続 | 1,500ms | 探索が外れたときに、最後に成功したアドレスへつないで確かめるぶん（BL-193）。到達確認の全体から差し引くため、上の4,000msは変わらない |

旧値（全体1,800ms / スキャン900ms / 到達確認1,500ms）は実測に基づかない見積もりで、
2026-09-20の実機検証（BL-165）で**実運用ではBLE経路が一度も選ばれていない**ことが判明した。
実測は次のとおり（Pixel 8 Pro + Sesame 5、デバッグビルドの`SesameBleDebugReceiver`経由）。

- BLE1往復（探索＋接続＋ログイン＋応答）: 状態取得 2,437 / 2,992 / 3,140ms（間隔15秒）、
  施錠 2,689ms、解錠 3,918ms。**連続実行すると`CONNECTION_FAILED` / `NOT_FOUND`になる**
  （直前の接続が残っている間は繋ぎ直せないため、間隔を空ける必要がある）
- 到達確認（1,500ms）は、Sesameの真横でも6回中2回しか成功しない
- 到達実績が立っても、本番の操作は1,800msで打ち切られて`route=BLE op=STATUS result=NG`となり、
  毎回Web APIへ倒れる

**もう1つの原因は、保存済みアドレスへの直接接続が成立しないことだった**（BL-189の再検証で判明）。
Sesame 5のBLEアドレス（例 `E9:38:...`）は先頭オクテットの上位2ビットが`11`の
**static random address**で、`BluetoothAdapter.getRemoteDevice(address)`は公開アドレスとして扱うため、
接続要求が相手に届かない。`getRemoteLeDevice(address, BluetoothDevice.ADDRESS_TYPE_RANDOM)`
（API 33以上）へ差し替えて解決した。API 33未満では種別を指定できないため、保存済みアドレスを使わず
探索へ倒す（誤った種別で繋ぎにいくより確実）。種別の判定は先頭オクテットの上位2ビットで行う
（11＝static random。01＝resolvableは接続のたびに変わるため保存しても探索へ戻るだけで実害は無い）。

支配的なのは探索（スキャン）だったため、BL-189で**利用者を待たせる経路から探索を外した**。

- `mobile.ble.SesameBleAddressCache`が、見つけたBLEアドレスをuuidごとに覚える
- `mobile.ble.SesameBleConnector`が、保存済みアドレスへの直接接続を先に試し、駄目なときだけ探索する。
  直接接続に失敗した保存値は捨てる（アドレスが変わったか圏外かのどちらか）
- アドレスを見つける役は、Web APIの通信と並行して走る到達確認（`SesameBleClient.probeReachable`）が担う。
  待たせる相手が居ないため上限を4,000msまで広げられる
- `PROBE_INTERVAL_MILLIS`は15分→5分（到達確認が失敗しても取り返しやすくする）。
  `REACHABLE_TTL_MILLIS`（30分）は変えていない
- BLEアドレスは端末を識別しうる値のため**ログへ出さない**

修正後の実測（2026-09-20夜、同じ構成）:

- 到達確認（4,000ms）は1回目で成功し、アドレスが保存される
- 次の操作は探索を飛ばして直接接続し、`route=BLE op=STATUS detail=SUCCESS` / `result=OK`となる。
  このとき**「今月のAPI呼び出し回数」は増えない**（56→56。BL-165(6)が初めて成立した）
- 上限を2,600msにした段階では`CONNECTION_FAILED` / `LOGIN_FAILED`が出たため、3,000msへ広げた。
  広げた後の成功率は3回中2回（残り1回は`NOT_FOUND`でインターネット経由へフォールバックし、
  操作自体は成功する）
- どの段階で失敗したかは診断ログの`detail=`（`SUCCESS` / `NOT_FOUND` / `CONNECTION_FAILED` /
  `LOGIN_FAILED`）で切り分けられる。`route=...`の成否だけでは分からなかったため追加した

**失敗の理由で、到達実績を消すかどうかを分ける**（BL-191、2026-09-21）。当初は失敗すれば必ず
到達実績を消していたため、繋げなかっただけ（`CONNECTION_FAILED` / `LOGIN_FAILED`）でも次の
到達確認まで（最短5分）BLEを試さなくなっていた。実測では圏内（Sesameの目の前）でもこの2つが
起きるため、次のように変更した。

- `mobile.ble.SesameBleAttempt` / `SesameBleFailure`が、BLEの結果を経路選択が使う2値
  （`NOT_REACHED` / `REACHED_BUT_FAILED`）へ落とす。`SesameBleClient.Result`は6種類あるが、
  経路選択が知りたいのは「圏内にいた証拠があるか」だけのため
- `NOT_FOUND`（探索で見つからない）と`PERMISSION_DENIED`（権限が無い）は`NOT_REACHED`で、
  従来どおり到達実績を消してインターネット経由へ切り替える
- 接続以降の失敗（`CONNECTION_FAILED` / `LOGIN_FAILED` / `COMMAND_REJECTED`）は
  `REACHED_BUT_FAILED`で、到達実績を残して**次の操作でもBLEを試す**
- 成功しない状態が続いたときに毎回BLEの上限（3,000ms）を払い続けないよう、
  `SesameBleReachability.MAX_CONSECUTIVE_FAILURES`（3回）連続で失敗したら到達実績を消す。
  成功と、到達確認の成否の記録（`recordProbe`）で数え直す
- 実績の**時刻は成功したときだけ**更新する。圏内の失敗で延命すると、圏外になっても
  `REACHABLE_TTL_MILLIS`（30分）で気づけなくなるため

全体の上限で打ち切られた場合も`CONNECTION_FAILED`になるため、探索の途中で打ち切られたときは
圏外なのに「圏内」と判定しうる。外れた場合の損は連続3回ぶんのBLE試行で頭打ちになる。
2026-09-21に実機で確認済み（本節の実機検証の記録を参照）。`CONNECTION_FAILED`の直後の操作でも
BLEを試すこと、`NOT_FOUND`ではBLEを試さずインターネット経由へ切り替わることを実測した。

ウィジェットのタップが打ち切られないことの確認は未実施で、BL-189へ「進行中・実機確認待ち」として残す。

**保存済みアドレスを一度失ってもBLE経路へ復帰できるようにする**（BL-193、2026-09-21）。
2026-09-21のBL-191の実機検証で、同じ端末・同じ位置で**保存済みアドレスへの直接接続は成功する一方、
到達確認の探索は3回とも当たらない**（10時52分・11時04分・11時10分、`reachableAtEpochMillis`が
付かない）ことを観測した。`SesameBleConnector.connect`は直接接続に失敗すると保存値を捨てるため、
`NOT_FOUND`が一度出ると「アドレス無し → 探索が当たらない → 到達実績が付かない → BLEを試さない」から
戻れなくなっていた。BL-189の`PROBE_INTERVAL_MILLIS`短縮（15分→5分）では解決していない。

- `SesameBleAddressCache`が**現用**（`load`）と**最後に成功したアドレス**（`loadLastKnown`）の
  2段で持つ。保存先は同じSharedPreferencesの別キー（`ble_addresses` / `ble_last_addresses`）で、
  `save`は両方へ、`remove`は現用だけを消す
- 現用を捨てる挙動は変えない。古いアドレスへ毎回接続を試みて**利用者を待たせないため**で、
  予備のアドレスを使うのは利用者を待たせない到達確認の中だけにする
- `SesameBleConnector.probeReachable`が、探索が外れたときに予備のアドレスへ直接つないで圏内かを
  確かめる。つながれば接続はすぐ閉じ、現用の記録として書き戻して次の操作で探索を飛ばせるようにする
- 予備のアドレスがある場合は、その接続ぶん（1,500ms）だけ探索を短くし、**到達確認全体の4,000msは
  変えない**。到達確認はWeb APIと並行するが完了を待つため、伸ばすと利用者の待ちも伸びる
- 予備のアドレスが古いままでも捨てる契機は設けない。探索が当たれば`save`が両方を上書きして
  自然に直るうえ、外れたときの損は到達確認の中の1,500ms（最短5分に1回、Web APIと並行）で
  頭打ちになるため
- 実装の置き場は`SesameBleConnector`側にした。`SesameBleClient`は関数数がdetektの
  `TooManyFunctions`の上限（11）に達しており、これ以上増やせないため
- 採用しなかった案: 到達確認のスキャン時間をさらに延ばす（完了を待つため利用者の待ちが伸び、
  BL-189の間隔短縮と同じく効かない可能性がある）、`SesameBleScanner.findDevice`のフィルタ条件を
  緩める（原因の切り分けに実機のスキャン結果が要り、この段階では判断できない）

実機での確認（保存済みアドレスが無い状態からの復帰）はBL-193へ「人手検証」として残す。

状態の自動取得（BL-142で廃止した鮮度ベースの取得）は、BL-152の完了後も**再開しない**。
BLEで到達できる間は上限を消費しないが、再開の可否は実測（BL-165）と運用実績を見てから判断する。

#### 権限の宣言と要求UI（BL-153、2026-09-19）

**権限は任意で、拒否されてもアプリは従来どおりWeb API経由で動く。** 実装とUIの両方でこれを担保する。

`mobile/src/main/AndroidManifest.xml`の宣言（論点4と一致）:

| 宣言 | 内容 |
| --- | --- |
| `BLUETOOTH_SCAN` | `android:usesPermissionFlags="neverForLocation"`。位置情報の推定に使わないことを宣言し、API 31以上で位置情報の許可を不要にする |
| `BLUETOOTH_CONNECT` | 接続に必要 |
| `ACCESS_FINE_LOCATION` | `android:maxSdkVersion="30"`。API 30以下のBLEスキャンにのみ必要 |
| `uses-feature android.hardware.bluetooth_le` | `required="false"`。BLE非搭載端末でもWeb API経由で動くため、配信対象を狭めない |

UIは資格情報設定画面（`mobile.credentials.BlePermissionSection`）へ置く。

- 常時出すのは**1行だけ**（`Bluetooth：未許可（インターネット経由で動作中）`など）。
  資格情報設定画面は縦スクロールしないため、長い説明を常時表示すると入力フォームが見切れる。
- 「設定」をタップすると**説明ダイアログ**（Androidが推奨するrationaleの形）を出してから権限を要求する。
  ダイアログには、何に使うのか・**許可しなくても従来どおり動くこと**・（API 30以下では）位置情報を
  近くの機器を探すためだけに使い収集も送信もしないことを書く。
- 一度拒否された権限は再要求してもダイアログが出ないことがあるため、要求済みかを
  `SesameBlePermissionAskedStore`（非暗号化SharedPreferences、到達実績と同じファイルの別キー）へ
  覚えておき、2回目以降は端末の「アプリ情報」画面を開く導線へ切り替える。
- 画面の再開（`ON_RESUME`）で許可状況を読み直し、設定画面で許可して戻ったときに追随させる。

文言と状態の決定は`mobile.ble.SesameBlePermissionPrompt`（Android非依存）が持ち、
`SesameBlePermissionPromptTest`で検証する。許可状況の問い合わせは`mobile.ble.SesameBlePermissions`。

#### 経路の可視化（BL-168、2026-09-19）

**当初の「利用者は経路を意識しない」方針を変更した。** BLEで届かなかったときに、利用者が
「Bluetoothにしたのにリクエスト回数が減らない」という状況の原因を切り分けられないため
（2026-09-19、ユーザー判断）。

表示できる面積が大きく違うため、面ごとに見せ方を変える。

| 面 | 見せ方 |
| --- | --- |
| Tile | 右上のデバイス名の帯（暗色、BL-210）へ、名前と並べてMaterialのベクターアイコンを置く（BL-206。BL-168では最終取得時刻の行へ絵文字を前置していた） |
| Complication | 「最終取得時刻」の行へ経路アイコンを**前置**する（`🔗3分前`、`🌐認証エラー`、`SesameTileStatus.detailLabelWithRouteIcon`）。**行は増やさない** |
| ホーム画面ウィジェット（4x2） | 右上のデバイス名の帯（暗色、BL-210）へ、名前と並べてMaterialのベクターアイコンを置く（BL-205。BL-176では最終取得時刻の行の先頭だった） |
| スマートフォンのアプリ画面 | ベクターアイコンと語を併記する（「Bluetooth」「インターネット」、BL-176） |

- アプリ画面のカードでは、**経路（アイコン＋語）を必ず1行に収める**。理由の文言が長いとき
  （「通信エラー（電波状況を確認）」）に語だけが押し出され、「Bluet／ooth」と途中で折り返って
  いたため、理由側へ`weight(1f, fill = false)` / `maxLines = 1` / `TextOverflow.Ellipsis`を、
  語へ`softWrap = false`を与えた（2026-09-21）。収まらないときは**理由側の末尾を省略する**。
  行を増やす方向では直さない（BL-172(4)の「1行で出る」を維持するため）。
- ベクターアイコンの実体は`mobile/src/main/res/drawable/ic_route_bluetooth.xml` /
  `ic_route_internet.xml`で、Google Material Icons（Apache-2.0）の`bluetooth` / `public`。
  出典はREADME.md「ライセンス」へ記載している（Googleは表示を義務付けていないが出典として残す）。
  どのアイコンを使うかの対応は`mobile.ui.SesameRouteIcon`が持ち、Composeは`Icon`＋`painterResource`、
  Glanceは`Image`＋`ImageProvider`＋`ColorFilter.tint`で描く。色は描画側で与えるため、
  ドローアブル自体は白で塗っている。
- ウィジェットは経路を**文言へ前置せず**`SesameWidgetModel.Configured.route`として別に持ち、
  描画側（`SesameWidgetChips.NameHeader`）が画像とデバイス名を横に並べる（Tileと揃えて画像が先。BL-209）。
- **経路アイコンは状態色の上に置かない**（BL-205 / BL-206、2026-09-23）。BL-176では最終取得時刻の行
  （状態色の背景）に置いていたが、施錠中＝緑・解錠中＝赤の上では白いアイコンが背景に埋もれ、
  BluetoothとインターネットのどちらかがUI上で判別できなかった（ユーザー指摘）。状態によって色の
  変わらない中立色（`CHIP_NEUTRAL_COLOR_ARGB`）のデバイス名の帯へ移し、白で描く（BL-210で帯をボタンと
  区別するため、さらに暗い`NAME_HEADER_COLOR_ARGB`へ変えた。白とのコントラストは上がる）。
  改善方法は、(1) 中立色の帯へ移す、(2) 今の位置で暗い丸のバッジを敷く、(3) 語を添える、の3案から
  ユーザーが(1)を選んだ。2x1（`MEDIUM`）の右1マスには従来どおり経路を出さない（面積が足りない）。
  Tileの右列は約98dp（BL-209で左列を狭める前は約78dp）で、名前が長いと名前の末尾が省略される。
  アイコンは名前の前に置くため常に表示される（BL-209。名前が先だった間はアイコンが右端で見切れた）。

- **アイコンは🔗（Bluetooth）／🌐（インターネット）。** 当初は📶／☁だったが、📶は携帯電話の
  電波強度として広く使われており、Bluetoothでの直接操作を表すものとして読み取れないという
  指摘があり変更した（BL-173、2026-09-20）。Unicodeに「Bluetooth」の絵文字は存在せず
  （ロゴはルーン文字の合字で絵文字フォントに無く、端末によっては豆腐になる）、搭載率の高い
  Emoji 1.0の範囲から選んでいる。Complicationが絵文字のままなのは、`SHORT_TEXT`/`LONG_TEXT`が
  テキストしか持てず、画像を埋め込めないため。TileはBL-206でベクターアイコンへ移した
  （絵文字は🔗がグレー、🌐が青で描かれ、緑・赤の背景では見分けにくかった）。
- 行を増やさないのは、3つの面がいずれも表示余白を使い切っており、過去に文言が収まらず省略された
  事例があるため（BL-102 / BL-104 / BL-158）。アイコンは1コードポイントに収まるものだけを使い、
  区切りの空白も入れない。この制約は`SesameRouteLabelTest`で固定している。
- 「全デバイス」対象の集約表示では、**全デバイスが同じ経路のときだけ**アイコンを出す
  （`SesameRouteLabel.commonRoute`）。混在しているのに片方のアイコンを出すと、出ていない側の
  デバイスについて誤解を与えるため。
- 経路が分からない（一度も操作・取得していない）場合は何も前置しない。
- 文言・アイコンは`core.display.SesameRouteLabel`が持ち、wearとmobileで食い違わないようにする。

**BLEを試したのに届かずWeb APIへ倒れた場合は、スマートフォンでトーストを出す。**

- 出すのは**実際にBLEを試して失敗したときだけ**。到達実績が無い・権限が無い・方針が
  「常にインターネット経由」の場合はそもそも試していないため出さない（毎回出ると煩わしい）。
- 判定は`mobile.command.SesameBleAccess`が行い、表示は
  `SesameDeviceCommandExecutorFactory`がメインスレッドへ渡し直して行う。ウィジェットのタップも
  ウォッチからのコマンドもバックグラウンドのコンポーネントで動くため。

#### 経路の変化と到達状況の通知（BL-190、2026-09-20）

**BL-168のトーストは、設計意図の場面では表示されない。** 2026-09-20の実機検証で、
背景から出したトーストがシステムに抑止されることを確認した。

```text
NotificationService: Suppressing toast from package com.sesamiwear.mobile.debug by user request.
```

Android 12以降は、**通知が無効なアプリの背景からのトーストを抑止する**。本アプリは
`POST_NOTIFICATIONS`を宣言していなかったため（トーストを選んだ理由がまさにそれだった）、
Android 13以降では通知が既定で無効＝トーストも出ない、という関係になっていた。
アプリを前景にして同じ操作をすると表示されるため、抑止が原因であることは切り分け済み。

置き換えの構成:

| クラス | 役割 |
| --- | --- |
| `mobile.notification.SesameRouteNotifier` | 通知の組み立てと送信。チャネルは`route_change`（`IMPORTANCE_LOW`、音を鳴らさない）。複数台の一括操作で積み上がらないよう**1つの通知を上書き**する |
| `mobile.ble.SesameRouteChangeTracker` | uuidごとに前回の経路を覚え、**変わった瞬間だけ**知らせる。記録が無い初回は知らせない |
| `mobile.ble.SesameRouteNotificationStore` | 通知のオン・オフ（既定はオン）。⋮メニューの「経路が変わったときの通知」から切り替える |
| `mobile.credentials.RouteNotificationState` | 上記の設定UI。オンにする操作の中で`POST_NOTIFICATIONS`を要求し、端末側で通知が切られている場合は副題でその旨を示す |

- `POST_NOTIFICATIONS`をマニフェストへ追加した。**許可されなくてもアプリは従来どおり動く**
  （通知が出ないだけ）。データセーフティ申告・権限の用途説明の更新はBL-154に含める。
- **インターネット経由へ切り替わった通知には、対処を1行足す**（BL-192、2026-09-21）。
  `core.display.SesameRouteLabel.OFFICIAL_APP_HINT`（「セサミ公式アプリを開いていると…」）を
  `BigTextStyle`の**展開時の本文にだけ**足す。折りたたみ時の1行（`setContentText`）は従来のまま。
  Sesameは同時に1台としかBLEでつながれず、**セサミ公式アプリが接続を掴んでいる間は探索から
  見つからないことがある**（2026-09-21の実機検証）。利用者からは「近くにいるのにインターネット
  経由のまま」としか見えないため、気づける場所に置く。同じ定数をヘルプ「Bluetoothで届かないとき」
  でも使い、通知とヘルプで文言が食い違わないようにする。
- トーストは**前景でのみ機能する補助**として残す。前景で操作しているときの即時性のため。
- 経路が変わったかどうかの判定は`SesameBleAccess`が実際に使った経路（`onRouteUsed`）を起点にする。
  「BLEを試して失敗した瞬間」だけを表す`onFallbackToWebApi`（トースト用）とは別物で、
  BLEを試していない場合も含めた結果を表す。

**BLEで届いているかどうかは、アプリのデバイスカードへ常時出す。**

- 1行を足して「Bluetooth：圏内（3分前に確認）」「Bluetooth：圏外（5分前に確認）」
  「Bluetooth：未確認」を出す（`core.display.SesameBleConnectionLabel`、
  状態の判定は`SesameBleReachability.status`）。
- 経路（「最後にどちらで動いたか」）と到達状況（「次にBLEを使えそうか」）は別物で、
  圏内でもまだ一度もBLEを使っていなければ経路はインターネットのままになる。
- 方針が「常にインターネット経由」または権限が無い場合は、到達確認そのものを行わないため
  「Bluetooth：使用しない設定」を出す（「未確認」と書くと、待てば変わるように読めてしまう）。
- 判定基準は経路選択（`preferBle`）と同じものを使う。表示と実際の挙動を食い違わせないため。

#### 利用者の「更新」によるBLE確認（BL-204、2026-09-23）

**状態取得（「更新」）だけは、到達実績の有無にかかわらずBLEを試す。** 到達実績は操作の契機でしか
更新されないため、実績が切れた後は利用者がSesameの近くにいてもインターネット経由のままになり、
利用者の側からBLEへ戻す手段が無かった（2026-09-23のユーザー指示「BLE接続をユーザ任意のタイミングで」）。

- 実装は`mobile.command.SesameBleAccess.checkStatus`。`SesameDeviceCommandExecutor.refreshStatus`が使う。
  状態取得の呼び出し元はウィジェットの「更新」（2x1ではデバイス名）と、ウォッチのTileの「更新」
  （`PATH_STATUS_REQUEST`）だけで、いずれも利用者の操作である（BL-142で自動取得を廃止済み）。
  そのため自動で電力やスキャン回数を消費することはない。Data Layerのプロトコルは変えていない。
- 流れは「BLE（上限3,000ms）→ 届かなければWeb API」。**圏外と判定した場合（`NOT_REACHED`）は、
  Web APIと並行する到達確認（4,000ms）を前回の確認からの間隔（5分）によらず行う。**
  利用者を待たせる経路の探索は1,200msしかなく、BLEアドレスをまだ覚えていないと近くにいても
  外れうるため、長めの探索と最後に成功したアドレスへの接続（BL-193）でアドレスを覚え直し、
  次の「更新」や施錠/解錠でBLEへ戻れるようにする。所要時間は3.0 + 6.0 = 9.0秒で、従来の設計値
  （ウィジェットの9秒枠）と同じ。
- 見つかったが接続・ログインで失敗した場合（`REACHED_BUT_FAILED`）は、到達確認を強制しない
  （アドレスは分かっている）。到達実績の扱いはBL-191の規則どおり。
- 方針が「常にインターネット経由」の場合と、BLEを使えない場合（権限が無い・Bluetoothが無効、
  `SesameBleOperations.isAvailable`）は試さず、従来の経路選択（`tryStatus`）に任せる。
  試しても必ず失敗する場面で、待ち時間とフォールバックの通知を出さないため。
- **施錠/解錠の経路選択は変えない。** 到達実績の無いデバイスへBLEを試すと、圏外のときに操作の
  応答が最大3秒遅れるため。「更新」でBLEに届けば到達実績が立ち、以降の施錠/解錠もBLEを先に試す。
- 「BLE確認」を「更新」とは別のボタンに分ける案は採らなかった。ユーザーの指示は「別枠を設けられるなら
  分けたい、無理なら更新で必ずBLEを試す」で、Tileの右列は約78dpしかなく、名前帯へさらにボタンを
  並べる余地が無いため（ウィジェットとTileの見た目も揃える方針）。

実機検証（BL-208、2026-09-23、Pixel 8 Pro + Pixel Watch 2 + 登録済みのSesame 5、**ユーザーが実施**）:
Claude Codeが両端末へデバッグ版（本番API接続）をインストールし、手順書（Skill `release-verification-prep`）に
沿ってユーザーが操作・判定した。22項目すべてで期待どおりに動いた。

- 「更新」の経路（BL-204）: Sesameの近くでウィジェット・Tileの「更新」、デバイス名の帯のタップのいずれでも
  BLEで取得され、API呼び出し回数は増えなかった。圏外ではインターネット経由へ倒れて回数が1増え、近くへ
  戻って「更新」すると**すぐに**BLEへ戻った（BL-204の目的）。その後の施錠/解錠もBLEで実行された。
  「常にインターネット経由」ではBLEを試さず、2秒以内の連続タップは従来どおり無視された。
- 表示（BL-205 / BL-206）: 帯とボタンの配置、4x2ウィジェットの4行とTileの操作文言が見切れないこと、
  緑・赤いずれの背景でも経路マークが白く判別できること、Tileの図柄がスマホと同じであること、
  コンプリケーションが従来どおり絵文字であること、2x1ウィジェットが変わらず、縦が足りない4x2では
  2x1へ切り替わることを確認した。
- 指摘2件を起票した。Tileでデバイス名が長いと経路マークが見切れる・左列が広い（BL-209）、
  デバイス名の帯が「更新」「変更」と同じ色で押せるボタンに見える（BL-210）。

実機検証（BL-211、2026-09-23、Pixel 8 Pro + Pixel Watch 2 + 登録済みのSesame 5、**ユーザーが実施**）:
BL-209 / BL-210の対応を、BL-208と同じ分担（インストールと手順書をClaude Code、操作と判定をユーザー）で
確認した。11項目すべてで期待どおりに動いた。

- Tile（BL-209 / BL-210）: 長い名前（10文字以上）でも帯の経路マークが名前の前に出て見切れず、名前だけが
  末尾省略された。左列56dpの「更新」「変更」は1行に収まって円形画面の縁で欠けず、右列の状態チップも
  見切れなかった。帯はボタンより暗い色で見分けられ、タップしても状態取得は走らなかった。「更新」は従来どおり。
- ウィジェット（4x2）: 経路マークが名前の前に出て長い名前は末尾省略され、帯は暗い色でボタンと見分けられた。
  帯のタップでは何も起きず、「更新」と状態チップ（施錠/解錠）は従来どおり動いた。

#### 段階的移行案

| 段階 | 内容 | 項目 |
| --- | --- | --- |
| 1 | 保持中のsecretKeyでBLE接続・状態取得ができることを実機で確認する | BL-150（人手検証、2026-09-19完了） |
| 2 | `mobile.ble`にBLEクライアントを実装する。デバッグビルドの隠し設定でBLE単体を検証できるようにし、この段階では経路の自動切り替えを入れない | BL-151（2026-09-19完了。実機検証はBL-165） |
| 3 | 経路選択（BLE優先・Web APIフォールバック）を実装し、実行時間の制限内へ収める | BL-152（2026-09-19完了。設計値の検証はBL-165） |
| 4 | BLE権限の要求UIとマニフェストを整備する | BL-153（2026-09-19完了） |
| 5 | データセーフティ申告・ストア掲載情報・利用者向けドキュメントを更新する | BL-154（人手検証） |

`-PsesameApiBaseUrl`のようなモック差し替えがBLEには存在せず、**検証は実機必須**になる
（本リポジトリにCIは無く、ローカル実行が唯一の品質ゲート）。経路選択の判定ロジックは
Android非依存のクラスへ切り出し、ユニットテストで検証できる範囲を最大化する。

#### 参照する外部実装（2026-09-18時点、BL-146の調査結果）

- 公式: `CANDY-HOUSE/SesameSDK_Android_with_DemoApp`（Kotlin、MIT、更新継続、JitPack配布
  `com.github.CANDY-HOUSE:SesameSDK_Android_with_DemoApp:<tag>`。取り込みは断念したが、
  プロトコルの参照元としては引き続き有効）。BLE実装は
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
  - ログへの秘密情報出力を禁止する（`rules/guardrails-unified.v1.md` 3.3 / `rules/guardrails-app.v1.md` 4.2）。実装上、
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

- 初回実装時の出力要件・追加実装時の出力要件・要件トレーサビリティ要件は `.github/copilot-instructions.md` の
  該当セクション（「開発プロセス要件」「出力要件」）に従う。自律ループ実行モードでは
  Skill `autonomous-loop`「出力要件の読み替え」に従い、本文書とEXECUTE.md/BACKLOG.mdの更新をもって充足する。
<!-- COPILOT_RECORDS:END -->

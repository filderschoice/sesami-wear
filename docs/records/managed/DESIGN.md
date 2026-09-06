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
  - HTTP非成功時は共通して`core.api.SesameApiException`を送出する。
  - MockWebServerを用いた単体テストで、レスポンスパース（施錠中/解錠中/未知フィールドの無視）・
    リクエストボディ・HTTPメソッド・パス・ヘッダー・異常系の例外送出を検証済み。
  - 上記フィールド構成・署名仕様は参考実装pysesame3
    (<https://github.com/mochipon/pysesame3>)のソースコード（2026-08-19時点mainブランチ、
    `cloud.py`/`const.py`）を読んで判明した内容であり、CANDY HOUSE公式ドキュメントそのものは
    未参照だが、実機（Sesame 5 + Hub 3）でのGET状態取得・POST施錠/解錠の疎通確認が完了し、
    SesameStatusのデコードエラーが発生しないことを確認済み（BL-010、人手検証）。

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
- `mobile.credentials.CredentialsSettingsScreen`: 複数デバイスの一覧・追加・編集・削除ができる
  Compose画面（BL-049）。現在の構成:
  - 入力欄（表示名/uuid/apikey/secretKey）はラベルのみのシンプルな見た目とし、secretKey欄は
    `PasswordVisualTransformation`でマスキング表示する（BL-023, BL-059）。
  - 詳細な取得手順の説明はヘルプボタン（`TextButton`）タップで開く`AlertDialog`（`HelpDialog`）へ
    集約し、`https://biz.candyhouse.co/biz/developer`（SESAME Biz 開発者ページ）へ遷移する
    `TextButton`（`Intent.ACTION_VIEW`）を含む（BL-057, BL-059）。uuid・apikey・secretKeyは
    いずれもこのページから取得する（Sesameアプリの「鍵をシェア」QRコードは使わない運用）。
  - 保存ボタンは`enabled = isInputValid`で制御し、保存成功時は「保存しました」を
    `LaunchedEffect`と`delay`で2秒間表示する（BL-024）。デバイス0件時は
    「まだSesameが登録されていません」を表示する（BL-056）。
  - `Modifier.safeDrawingPadding()`でステータスバー等のシステムUIとの重なりを防止（BL-045）。
  - `Column(verticalArrangement = Arrangement.spacedBy(8.dp))`で入力欄・ボタン間隔を統一し、
    保存ボタンは`Modifier.fillMaxWidth()`で表示（BL-059）。
  - **未確認事項**: `biz.candyhouse.co`は動的サイトのためWebFetchでの実ページ内容確認はできて
    おらず、公式ドキュメントの記述とユーザーからの実機確認報告のみを根拠にしている。

### Data Layer APIプロトコル定義（`core.SesameWearProtocol`）

`mobile`/`wear`間で共有するメッセージパス・DataItemパス・ペイロードキーの定義（Android非依存）。

- `PATH_LOCK_REQUEST` / `PATH_UNLOCK_REQUEST`: 施錠/解錠コマンド送信（BL-006）。
- `PATH_COMMAND_RESULT`: コマンド結果返送（BL-006）。
- `PATH_STATUS_REQUEST`: 状態取得リクエスト、Fire-and-forget（BL-061）。結果は返さず
  `STATUS_DATA_ITEM_PATH`のDataItem変更として非同期に届く。
- `STATUS_DATA_ITEM_PATH` / `KEY_IS_LOCKED` / `KEY_UPDATED_AT_EPOCH_MILLIS`: ロック状態の同期
  （BL-015）。`statusDataItemPath(uuid)`でデバイスごとに一意なパスを生成する（BL-050）。
- `encodeDeviceUuid` / `decodeDeviceUuid`: 施錠/解錠/状態取得コマンドの対象デバイスuuidを
  メッセージペイロードへUTF-8バイト列としてそのまま載せる（BL-048）。
- `DEVICE_LIST_DATA_ITEM_PATH` / `KEY_DEVICE_LIST_JSON`: 登録済みデバイス一覧（`SesameDeviceSummary`
  のリスト、機密情報を含まない）をmobile→wearへ同期するパス・キー（BL-052）。
- `ALL_DEVICES_TARGET_UUID`（`"__all_devices__"`）: 「登録済み全デバイス」を表す特別な値
  （BL-071）。実際のSesame uuidと衝突しない固定文字列で、wear側のデバイス割当ストアへ実uuidの
  代わりに保存し、Tile/Complication表示・コマンド送信時に判定する。

### mobile側コマンド処理

- `mobile.messaging.SesameMessageListenerService`（`WearableListenerService`実装、BL-013）:
  受信パスからコマンド判定し、対象デバイスuuidに対応する資格情報で`SesameCommandHandler`
  （Android非依存、`SesameApiClient.sendCommand()`を呼ぶ）を実行する。資格情報未設定時は
  `FAILURE`を返す。`PATH_STATUS_REQUEST`受信時は`SesameApiClient.getStatus()`を呼び、成功時に
  `SesameStatusSyncer`でDataItemへ同期する（結果はwear側へ返送しない、BL-061）。
- `mobile.messaging.CommandDebouncer`（Android非依存、時刻取得を注入可能）: 同一デバイスuuidへの
  2秒以内の重複コマンドを無視する（BL-062、Tile連打による多重送信・多重ハプティクスの防止）。
- `mobile.messaging.SesameStatusSyncer`: `DataClient.putDataItem`ラッパー。コマンド送信成功時は
  「送信したコマンドが意図した状態」（LOCK成功→施錠、UNLOCK成功→解錠）をそのまま同期する簡略化
  ロジック。`PATH_STATUS_REQUEST`経由ではSesame APIのGET結果をそのまま同期する（BL-015, BL-061）。
- `mobile.messaging.SesameDeviceListSyncer`: 登録済みデバイス一覧（uuid/displayNameのみ、
  apikey/secretKeyは含めない）を`DEVICE_LIST_DATA_ITEM_PATH`へ同期する（BL-052）。
- **未確認事項**: 状態同期はコマンド送信成功時と`PATH_STATUS_REQUEST`経由（Tile/Complication
  表示時にDataItemが30秒以上古い場合、またはデバイス名チップタップ時）に限られ、定期ポーリングは
  行わない。Sesame純正アプリでの操作等、他経路による状態変化はTileが再表示・更新要求されるまで
  反映されない。

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
- `wear.tile.SesameTileContent`（Android非依存）: 状態→表示文言・アイコン・背景色・テキスト色の
  マッピング。`statusLabel`/`actionLabel`は`isAllDevices`パラメータ（デフォルト`false`）を持ち、
  全デバイス選択時は「全施錠中」等の文言に切り替わる。
- `wear.tile.SesameTileActions`（Android非依存）: Tile状態→提示コマンドの決定。MIXED状態はタップで
  「全施錠」を提示する（迷ったら安全側の方針、UNLOCKのみ確認画面を挟む既存UXと組み合わせて安全側は
  確認不要のまま維持、BL-071）。
- `wear.tile.SesameTileStateResolver`（Android非依存、Tile/Complication共通）: 対象uuidが
  `ALL_DEVICES_TARGET_UUID`の場合は登録済み全デバイスの状態を`TileDisplayStateResolver
  .resolveAggregate`で集約し、それ以外は単一デバイスの状態を解決する。いずれもDataItemが古い場合
  （30秒以上）の自動状態取得リクエストを行う（BL-071でSesameTileService/
  SesameComplicationDataSourceServiceの重複ロジックを集約）。
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
- `wear.action.SesameActionTargetResolver`（Android非依存）: コマンド送信・状態更新の対象uuid一覧を
  解決する。全デバイス時は登録済み全uuidのリスト、それ以外は単一uuid。`SesameActionActivity`/
  `SesameStatusRefreshActivity`が全デバイス選択時にループで各デバイスへ個別にlock/unlock/
  status-requestメッセージを送信する（mobile側は既存の単一デバイス処理をそのままN回受けるだけで
  対応でき、mobile側の変更は不要だった、BL-071）。
- `wear.action.SesameStatusRefreshActivity`: `PATH_STATUS_REQUEST`をFire-and-forgetで送信するのみの
  軽量Activity（施錠/解錠は行わない）。Tileのデバイス名チップタップから起動する（BL-063）。

### ハプティクス

- `wear.haptics.HapticPattern`（SUCCESS/FAILURE）/ `SesameHapticPatternResolver`（Android非依存、
  `SesameCommandResult`→`HapticPattern`）/ `SesameHapticPlayer`（`Vibrator`/`VibratorManager`
  ベースの振動再生）: 施錠/解錠の成否をハプティクスパターンで区別して通知する（BL-008, BL-016）。

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
- **未確認事項**: 図案はXMLパスの手書きによるものであり、視覚的な洗練度はデザイナーによる最終調整を
  前提としていない。Google Play Console提出に必要な高解像度アイコン画像（512x512 PNG）はXMLベースの
  生成が技術的に困難なため未対応（BL-034、人手検証で対応する）。

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
- **未確認事項**: 「スマートフォンへインストール後、ペアリング済みのWearデバイスへ自動的に
  ウォッチ用アプリをインストールする」機能（Google Playの自動プッシュインストール）が2成果物構成で
  実際に機能するかは、Play Console経由での確認が必要（BL-038、BL-097、人手検証）。

### GitHub公開対応

- LICENSE（MIT License、Copyright 2026 filderschoice）を新規作成し、READMEへライセンスセクション
  （pysesame3への謝辞含む）を追加した。pysesame3はプロトコル仕様（コマンドコード・署名アルゴリズム・
  レスポンス構造）を参照した独自実装であり、ソースコードの逐語的コピーではないため侵害リスクは
  ないと判断した（同リポジトリのライセンスがMIT Licenseであることも確認済み）。
- README.md / `docs/store/STORE_LISTING.md`へ「本アプリは個人による非公式アプリであり、CANDY HOUSE
  株式会社とは提携・協力関係にない」旨を明記した。
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
として用意済み。実際の公開URLでのホスティングとPlay ConsoleのData safety申告への反映はBL-033
（人手検証）、実際のPlay Console提出はBL-034（人手検証）で行う。

## 設計方針

### アーキテクチャ方針

- secretKeyは機密性が高いためWatch単体には保持させない（PLAN.md記載方針を踏襲）。
  - `mobile`: apikey/secretKey/uuidを保持し、AES-CMAC署名生成とSesame API呼び出しを担当する。
  - `wear`: Tile/AppからWearable Data Layer API（MessageClient）で「lock」「unlock」等の意図のみを
    `mobile`へ送信する。`mobile`が実行し、結果（成功/失敗）を`wear`へ返す。
  - `core`: `mobile`/`wear`双方から参照する非機密のプロトコル定義（`SesameWearProtocol`等）・
    暗号・APIクライアント・状態解決ロジックを配置する。secretKey等の機密値やAndroid依存コードは
    置かない。

### モジュール構成・パッケージ方針

- ルートパッケージ: `com.sesamiwear`（`core` / `mobile` / `wear` 配下にサブパッケージ）。
- `mobile`: `com.android.application`。`applicationId=com.sesamiwear.mobile`、`minSdk=26`、
  `versionCode`は1始まりの系列。スマートフォン向け成果物。
- `wear`: `com.android.application`。`applicationId`は`mobile`と同値
  （`com.sesamiwear.mobile`）、`minSdk=26`、`versionCode`は1001始まりの独立系列（BL-090）。
  `AndroidManifest.xml`に`uses-feature android:name="android.hardware.type.watch"`
  （`required`属性を付けず既定値`true`）と
  `com.google.android.wearable.standalone=false`（スマホ連携必須アプリのため）を設定済み。
- 依存バージョンは`gradle/libs.versions.toml`（Version Catalog）で一元管理する
  （AGP 8.13.0 / Kotlin 2.0.21 / Compose BOM 2024.12.01 / Wear Compose 1.4.1 等）。

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

## Copilot実装指示

- 初回実装時の出力要件・追加実装時の出力要件・要件トレーサビリティ要件は `CLAUDE.md` の
  該当セクション（「開発プロセス要件」「出力要件」）に従う。自律ループ実行モードでは
  `CLAUDE.md`「出力要件の読み替え」に従い、本文書とEXECUTE.md/BACKLOG.mdの更新をもって充足する。
<!-- COPILOT_RECORDS:END -->

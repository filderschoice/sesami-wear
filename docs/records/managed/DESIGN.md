<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用設計記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.design.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
# 実装用プロンプト設計書

## 目的

- 本文書は、Sesami Wear（Pixel WatchからSesame 5スマートロックを操作するAndroid/Wear OSアプリ）を
  同一要件で再実装させるための最新版プロンプトである。実装済み機能・設計意図・制約・品質観点を統合する。

## 対象システム概要

- 対象: Android（スマホ側）+ Wear OS（Watch側）の2アプリ構成。CANDY HOUSE Sesame 5 + Hub 3の
  クラウドAPI（`https://app.candyhouse.co/api/sesame2/{uuid}`）経由で施錠/解錠・状態取得を行う。
- 前提環境: JDK 17、Android SDK（compileSdk/targetSdk 35、build-tools 35.0.0）、Gradle 8.10.2
  （Gradle Wrapper経由）。詳細は `CLAUDE.md`「本リポジトリの品質ゲート定義」段階Bを参照。

## 実装済み機能要件

- REQ-001（BL-001）: Android/Wear OSマルチモジュールGradleプロジェクトの雛形。
  `core`（純Kotlin/JVMライブラリ）・`mobile`（Android application）・`wear`（Android application,
  Wear OS）の3モジュール構成。ktlint/detekt/lintの品質ゲートが空実装に対して成功することを確認済み。
- REQ-002（BL-002）: `com.sesamiwear.core.crypto.AesCmac`としてRFC 4493準拠のAES-CMACを実装。
  `javax.crypto.Cipher`（AES/ECB/NoPadding）でAES-128単一ブロック暗号化を行い、サブキー生成・
  メッセージのブロック分割・最終ブロックのパディング/XORをRFC仕様どおりに実装した。
  RFC 4493 Appendix Aの公開テストベクタ4件と、鍵長不正時の異常系1件で検証済み。
  Sesame APIコマンド固有のバイト列組み立て（cmd種別・タイムスタンプの直列化）はBL-004で
  `AesCmac.compute()`を呼び出す形で実装する（本関数はSesame非依存の汎用CMAC実装）。
- REQ-003（BL-003, BL-012）: `com.sesamiwear.core.api.SesameApiClient`としてSesame状態取得（GET）を実装。
  OkHttp + kotlinx.serialization.jsonでx-api-keyヘッダー付きGETリクエストを送信し、
  `SesameStatus`（batteryVoltage/position/CHSesame2Status。isInLockRange/isInUnlockRangeは
  CHSesame2Statusからの計算プロパティ）へデコードする。HTTP非成功時は`SesameApiException`を送出する。
  MockWebServerを用いた単体テスト4件（施錠中/解錠中のレスポンスパース、未知フィールドの無視、
  異常系の例外送出）で検証済み。フィールド構成は参考実装pysesame3
  （https://github.com/mochipon/pysesame3）のソースを参照して修正した（BL-012）。
  **未確認事項**: pysesame3自体もこのレスポンス構造を将来変更されうるものとして注意書きしており、
  実機疎通確認（BL-010、人手検証）で最終確認する必要がある。
- REQ-004（BL-004）: `SesameApiClient.sendCommand()`として施錠/解錠（POST）を実装。
  `SesameCommand`（LOCK=82 / UNLOCK=83）と`SesameCommandSigner`（internal、署名生成）を追加した。
  署名生成ロジックはpycryptodome（`CMAC.new(key, ciphermod=AES)`）による独立計算と一致することを
  固定タイムスタンプのテストで確認済み（鍵はRFC 4493のダミー鍵、実資格情報ではない）。
  MockWebServerでリクエストボディ（cmd/history/sign）・HTTPメソッド・パス・ヘッダーを検証し、
  HTTP非成功時は`SesameApiException`を送出することを確認済み。
- REQ-005（BL-006）: Data Layer APIメッセージングのコアロジックを実装。
  `core.SesameMessageSender`（送信抽象化インターフェース）、`core.SesameCommandResult`
  （成功/失敗を1バイトのペイロードへ変換）、`wear.messaging.SesameCommandSender`
  （lock/unlock意図の送信、Android非依存）、`mobile.messaging.SesameCommandHandler`
  （受信パスからコマンド判定しSesameApiClientで実行、Android非依存）を実装し、単体テスト9件
  （core 3件、mobile 4件、wear 2件）で検証済み。`wear.messaging.MessageClientSesameMessageSender`
  （MessageClientベースの薄いアダプタ）も実装したが、これ自体はAndroid Google Play Services依存の
  ためユニットテスト対象外（BL-011で人手検証）。
  **未完了**: Mobile側の`WearableListenerService`実サービスクラス（`onMessageReceived`から
  `SesameCommandHandler`を呼び出しWear側へ結果を返す部分）とAndroidManifestへの登録、
  apikey/secretKey/uuidの取得元（BL-005実装後に確定）との結線はBL-013へ切り出した
  （BL-006時点では資格情報の永続化方式が未確定のため、ロジック本体とMessageClientアダプタの
  実装に限定し、実サービスとの統合は後続タスクとした）。
- REQ-006（BL-007）: Wear OS Tileを実装。`core.TileDisplayState`（LOCKED/UNLOCKED/IN_PROGRESS/
  DISCONNECTED/UNKNOWNの5状態）と`core.TileDisplayStateResolver`（スマホ接続状態・コマンド実行中
  フラグ・ロック状態から表示状態を決定）、`wear.tile.SesameTileContent`（状態→表示文言マッピング）を
  Android非依存で実装し単体テスト9件（core 6件、wear 3件）で検証済み。`wear.tile.SesameTileService`
  （`androidx.wear.tiles.TileService`実装）も実装し、AndroidManifestへ
  `androidx.wear.tiles.action.BIND_TILE_PROVIDER`のintent-filter付きで登録した。
  **技術メモ**: `androidx.wear.tiles:tiles:1.4.1`は内部で`androidx.wear.protolayout`パッケージへ
  移行済みで、`LayoutElementBuilders`/`TimelineBuilders`/`ResourceBuilders`/`material.Text`等は
  `androidx.wear.protolayout`（`tiles-material`とは別に`protolayout-material`依存が必要）を使う必要が
  あった。`TileBuilders`/`RequestBuilders`/`TileService`自体は引き続き`androidx.wear.tiles`パッケージ。
  `ListenableFuture`の`Futures.immediateFuture()`には`com.google.guava:guava`（推移的に入る
  `listenablefuture:1.0`は`Futures`ヘルパーを含まないため）の明示的な追加が必要だった。
  **未完了**: 本タスクではステータス表示のみを実装し、Tileタップ時のクリックアクション
  （施錠ワンタップ実行、解錠ホールド確認）はBL-013の資格情報結線を前提とするためBL-014へ切り出した。
  また、実データ（スマホ接続状態・実際のロック状態）との結線もBL-013/BL-014で行う（現状は常に
  `UNKNOWN`状態を表示するプレースホルダー実装）。Android Studioでのプレビュー確認は本環境で
  自動実行できないため対象外とし、実機確認はBL-011（人手検証）に委ねる。

## 設計方針

### アーキテクチャ方針

- secretKeyは機密性が高いためWatch単体には保持させない（PLAN.md記載方針を踏襲）。
  - `mobile`: apikey/secretKey/uuidを保持し、AES-CMAC署名生成とSesame API呼び出しを担当（BL-002〜005）。
  - `wear`: Tile/AppからWearable Data Layer API（MessageClient）で「lock」「unlock」の意図のみを
    `mobile`へ送信する（BL-006〜009）。`mobile`が実行し、結果（成功/失敗）を`wear`へ返す。
  - `core`: `mobile`/`wear`双方から参照する非機密のプロトコル定義（Data Layerのメッセージパス定数
    `SesameWearProtocol` 等）を配置する。secretKey等の機密値やAndroid依存コードは置かない。

### モジュール構成・パッケージ方針

- ルートパッケージ: `com.sesamiwear`（`core` / `mobile` / `wear` 配下にサブパッケージ）。
  リポジトリ名由来の既定値であり、安全性に関わらない判断のため確認質問を挟まず採用した。
- `mobile`: `applicationId=com.sesamiwear.mobile`、`minSdk=26`。
- `wear`: `applicationId=com.sesamiwear.wear`、`minSdk=30`（Wear OS 3.0+相当）。
  `AndroidManifest.xml`に`uses-feature android:name="android.hardware.type.watch"`と
  `com.google.android.wearable.standalone=false`（スマホ連携必須アプリのため）を設定済み。
- 依存バージョンは `gradle/libs.versions.toml`（Version Catalog）で一元管理する
  （AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01 / Wear Compose 1.4.1 等）。

### UI/UX方針（PLAN.md要件、未実装分を含む）

- Tileはロック状態（施錠中/解錠中）をアイコン・色でひと目表示する（BL-007）。
- 施錠はワンタップ即実行、解錠は誤操作防止のためホールド確認 or 確認ダイアログを挟む（BL-007）。
- 通信中は明確な処理中表示＋ボタン無効化で二重送信防止（BL-007）。
- 成功/失敗をハプティクスパターンで区別する（BL-008）。
- スマホ未接続時はTile上で明示し操作不可にする（BL-007）。
- Complicationで常時ロック状態を文字盤表示する（BL-009）。

## 非機能要件

- 性能: 未定義（現時点で計測対象の実装なし）。
- 信頼性: 通信失敗時はサイレント失敗を避け、Tile上に明示する（PLAN.md UX要件）。
- セキュリティ/プライバシー:
  - secretKey/apikey/uuidは`mobile`側のみで保持し、`EncryptedSharedPreferences`等で暗号化保存する
    （BL-005で実装予定。現時点では保存機構自体が未実装）。
  - ログへの秘密情報出力を禁止する（`rules/guardrails-unified.v1.md` 3.3/4.2）。
  - 実資格情報を用いた検証はBACKLOGで`区分: 人手検証`として自動実行対象から除外する
    （BL-010, BL-011）。AES-CMACの自動テストはRFC 4493公開テストベクタとダミー鍵のみを用いる。

## 実装制約

### 技術制約

- Wear API: 施錠/解錠（POST）はsecretKeyによるAES-CMAC署名付きコマンドが必須。素のJSON送信では
  通らない（PLAN.md記載）。状態取得（GET）はx-api-keyヘッダーのみで可能。
- 参考実装: pysesame3（https://github.com/mochipon/pysesame3）、chanshige/sesame（PHP）などのOSSに
  CMAC署名ロジックの実装例があり、Kotlin/Java向けの軽量ラッパーが少ないためロジック移植が前提
  （BL-002で対応）。
- 施錠/解錠APIの詳細仕様（pysesame3 pysesame3/cloud.py `SesameCloud.getSign`/`sendCmd`、
  pysesame3/const.py `CHSesame2CMD`のソースを参照して判明。BL-004実装時の根拠）:
  - エンドポイント: `POST {OFFICIALAPI_URL}/{uuid}/cmd`（状態取得のGETと異なり`/cmd`サフィックスが付く）
  - ヘッダー: `x-api-key`（GETと共通）
  - リクエストボディ（JSON）: `cmd`（int、LOCK=82 / UNLOCK=83 / TOGGLE=88 / CLICK=89）、
    `history`（任意の文字列タグをBase64エンコードしたもの、履歴に残る）、`sign`（後述の署名、32文字hex文字列）
  - 署名生成: 現在のUnixタイムスタンプ（秒、Int）を4バイト・リトルエンディアンにエンコードし、
    そのうちインデックス1〜3（先頭バイトを除いた3バイト、約256秒単位に丸められたタイムスタンプ）を
    メッセージとしてAES-CMAC（secretKeyを鍵）を計算し、その結果16バイト全体を32文字のhex文字列化した
    ものが`sign`（先頭バイトのみを使う実装ではなく、CMAC出力全体をそのまま使う点に注意）。
  - **未確認事項**: 上記はpysesame3（2026-08-19時点のmainブランチ）のPythonソースコードを読んで判明した
    内容であり、CANDY HOUSE公式ドキュメントそのものは未参照。実機疎通確認（BL-010、人手検証）で
    最終確認する必要がある。
- Androidアイコンリソース: `mobile`/`wear`とも実アイコン（mipmap）は未作成で、暫定的に
  `@android:drawable/sym_def_app_icon`（システム標準アイコン）を参照している。配布前に専用アイコンへの
  差し替えが必要（自動検証の対象外、Play Store提出前の対応事項として残る）。

### 運用制約

- 実機（Pixel Watch / Sesame 5 + Hub 3）を用いる検証と、実資格情報を用いるAPI疎通確認は
  自律ループ実行モードの自動実行対象外とし、`区分: 人手検証`としてBACKLOGへ計上する。

## Copilot実装指示

- 初回実装時の出力要件・追加実装時の出力要件・要件トレーサビリティ要件は `CLAUDE.md` の
  該当セクション（「開発プロセス要件」「出力要件」）に従う。自律ループ実行モードでは
  `CLAUDE.md`「出力要件の読み替え」に従い、本文書とEXECUTE.md/BACKLOG.mdの更新をもって充足する。
<!-- COPILOT_RECORDS:END -->

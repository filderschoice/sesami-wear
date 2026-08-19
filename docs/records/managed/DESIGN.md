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
  (<https://github.com/mochipon/pysesame3>)のソースを参照して修正した（BL-012）。
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
- REQ-007（BL-005）: 資格情報（uuid/apikey/secretKey）の保存機能と設定画面を実装。
  `core.SesameCredentials`（secretKeyはBase64文字列で保持し`secretKeyBytes`で復号）、
  `core.SesameKeyValueStore`（永続化抽象インターフェース）、`core.SesameCredentialsStore`
  （save/load/clearロジック本体、Android非依存）を実装し単体テスト4件で検証済み。
  `mobile.credentials.EncryptedSharedPreferencesKeyValueStore`（`androidx.security.crypto`の
  `EncryptedSharedPreferences`＋`MasterKey`ベースの実装、薄いアダプタのためテスト対象外）と
  `mobile.credentials.CredentialsSettingsScreen`（uuid/apikey/secretKeyの入力・保存Compose画面）を
  実装し、`MainActivity`から呼び出す構成にした。ログ出力（`android.util.Log`等）は一切使用しておらず、
  平文の資格情報がログへ出力される経路はない。
- REQ-008（BL-013）: BL-006で切り出していたWearableListenerServiceの実サービス化と資格情報結線を実装。
  `mobile.messaging.SesameMessageListenerService`（`WearableListenerService`実装、
  `SesameCredentialsStore`から資格情報を読み込み`SesameCommandHandler`を構築、資格情報未設定時は
  `FAILURE`を返す）をAndroidManifestへ`MESSAGE_RECEIVED`アクション・`pathPrefix="/sesami-wear/"`の
  intent-filter付きで登録した。`wear.messaging.SesameCommandSenderProvider`
  （`Wearable.getMessageClient(context)`から`SesameCommandSender`を構築するファクトリ）も実装した。
  いずれもAndroid依存の薄いアダプタのためユニットテスト対象外（BL-011で人手検証）。
  **技術メモ**: `core.SesameApiClient`のコンストラクタが`OkHttpClient`型をデフォルト引数として公開APIに
  含んでいたため、`core/build.gradle.kts`のokhttp依存を`implementation`から`api`へ変更する必要があった
  （`implementation`のままだとmobileモジュールから`OkHttpClient`型が解決できずコンパイルエラーになった）。
- REQ-009（BL-014）: Tileのクリックアクションを実装。`core.api.SesameCommandConfirmation`
  （UNLOCKのみ確認要求、単体テスト2件）、`wear.tile.SesameTileActions`（Tile状態→提示コマンドの決定、
  単体テスト3件）、`wear.action.SesameActionCommandParser`（Intent Extra文字列→SesameCommand、
  単体テスト3件）をAndroid非依存で実装。`wear.action.SesameActionActivity`（Tileタップで起動、
  LOCKはワンタップ即送信・UNLOCKは確認ボタン後に送信、Fire-and-forget方式でコマンド送信して終了）、
  `wear.messaging.SesameConnectedNodeProvider`（`NodeClient.connectedNodes`から接続先ノードID取得、
  薄いアダプタ）を実装し、AndroidManifestへ`SesameActionActivity`（`exported=false`）を登録した。
  `SesameTileService`にも`ActionBuilders.LaunchAction`によるクリック設定を組み込んだ。
  **未完了**: 現状Tileは常に`UNKNOWN`状態を返すプレースホルダーのため、`SesameTileActions`が
  `null`（タップ不可）を返し続け、実際にはまだタップできない。Tileへの実データ結線はBL-015へ切り出した。
  また、コマンド送信はFire-and-forgetのため、成功/失敗のリアルタイム反映とハプティクスはBL-008で扱う。
- REQ-010（BL-008）: ハプティクスフィードバックを実装。`wear.haptics.HapticPattern`（SUCCESS/FAILURE）、
  `wear.haptics.SesameHapticPatternResolver`（`SesameCommandResult`→`HapticPattern`、Android非依存、
  単体テスト2件）、`wear.haptics.SesameHapticPlayer`（`Vibrator`/`VibratorManager`ベースの振動再生、
  Android依存の薄いアダプタ）を実装し、`android.permission.VIBRATE`をAndroidManifestへ追加した。
  **未完了**: `SesameHapticPlayer`を呼び出す箇所（Wear側でのPATH_COMMAND_RESULT受信リスナー）が
  まだ存在しない。BL-014でコマンド送信をFire-and-forgetにしたため、結果受信の仕組み自体が未実装で
  あることに起因する。受信リスナーとハプティクス再生の橋渡しはBL-016へ切り出した。
- REQ-011（BL-009）: Complicationでロック状態を文字盤表示する機能を実装。
  `wear.complication.SesameComplicationContent`（`TileDisplayState`→短い表示文言、Android非依存、
  単体テスト2件）と`wear.complication.SesameComplicationDataSourceService`
  （`ComplicationDataSourceService`実装、`ShortTextComplicationData`を返す）を実装し、
  AndroidManifestへ`BIND_COMPLICATION_PROVIDER`権限・`ACTION_COMPLICATION_UPDATE_REQUEST`
  intent-filter・`SUPPORTED_TYPES=SHORT_TEXT`のmeta-data付きで登録した。
  **未完了**: SesameTileServiceと同様、実データ（スマホ接続状態・ロック状態）との結線はBL-015で
  行う（現状は常にUNKNOWN状態を表示するプレースホルダー）。
- REQ-012（BL-015）: TileとComplicationへの実データ結線を実装。DataClient（DataItem）で
  Mobile→Wear間にロック状態を同期する方式を採用: `core.SesameWearProtocol`に
  `STATUS_DATA_ITEM_PATH`/`KEY_IS_LOCKED`/`KEY_UPDATED_AT_EPOCH_MILLIS`を追加し、
  `core.SesameStatusSnapshot`（同期スナップショット）と`core.SesameStatusSnapshotFactory`
  （DataMapの生値からスナップショットを構築、Android非依存、単体テスト2件）を実装した。
  Mobile側は`mobile.messaging.SesameStatusSyncer`（`DataClient.putDataItem`ラッパー）を追加し、
  `SesameMessageListenerService`でコマンド送信成功時に「送信したコマンドが意図した状態」
  （LOCK成功→施錠、UNLOCK成功→解錠）をそのまま同期する簡略化ロジックとした
  （実際のCHSesame2Statusとの整合はBL-010で確認する）。Wear側は
  `wear.messaging.SesameStatusSnapshotReader`（DataClient経由の読み取り、`SesameStatusSnapshotFactory`
  を利用）を追加し、`SesameTileService`と`SesameComplicationDataSourceService`の両方を
  `SesameConnectedNodeProvider`（接続確認）と組み合わせた実データ表示に置き換えた
  （`TileService.onTileRequest`はGuavaの`SettableFuture`でコルーチン結果をブリッジしている）。
  **未確認事項**: Mobile側の状態同期はコマンド送信成功時のみに限定しており、アプリ起動時や
  他経路（Sesame純正アプリでの操作等）による状態変化は反映されない。定期ポーリングやGET結果に
  基づく同期は将来の改善事項として残る。
- REQ-013（BL-016）: Wear側での結果受信とハプティクス再生の橋渡しを実装。
  `wear.messaging.SesameResultHandler`（path・payloadから`HapticPattern`を決定、
  PATH_COMMAND_RESULT以外はnull、Android非依存、単体テスト3件）と
  `wear.messaging.SesameResultListenerService`（`WearableListenerService`実装、判定結果を
  `SesameHapticPlayer`へ渡す薄いアダプタ）を実装し、AndroidManifestへ
  `MESSAGE_RECEIVED`アクション・`pathPrefix="/sesami-wear/result"`のintent-filter付きで登録した。
  これによりBL-008で実装したハプティクス再生ロジックの呼び出し元が揃い、施錠/解錠の
  Data Layer APIメッセージングとハプティクスフィードバックの結線が完了した。
- REQ-014（BL-017）: Tileの背景色による状態表示を実装。`wear.tile.SesameTileContent`に
  `backgroundColorArgb(state)`（施錠中=緑/解錠中=赤/通信中=黄/未接続・不明=グレー、単体テスト2件追加）
  を追加し、`SesameTileService`のBoxに`ModifiersBuilders.Background`として組み込んだ
  （クリックアクションと同じModifiersへ統合）。これによりPLAN.mdのUX要件
  「Tileはロック状態をアイコン・色でひと目表示」の色分け部分を満たした
  （アイコン自体は未実装、テキスト+背景色による表現に留まる）。
- REQ-015（BL-018）: README.mdを新規作成。アーキテクチャ概要（core/mobile/wearの役割分担）、
  前提環境、資格情報の取得・設定手順、品質ゲート5コマンド（`ktlintCheck`/`detekt`/`lintDebug`/
  `testDebugUnitTest`・`test`/`assembleDebug`）、プロジェクト構成、既知の未確認事項・制約
  （pysesame3参照によるAPI仕様推測、状態同期の限定範囲、アイコン未作成等）を記載した。
  `npx markdownlint-cli2 "**/*.md"`で検証済み。あわせてDESIGN.md内のbare URL（MD034違反）を
  Markdownリンク形式へ修正した。BACKLOG.md冒頭の`markdownlint-disable-file MD041`欠落は
  マーカー外の変更が必要なためBL-021として要確認登録に留めた。
- REQ-016（BL-019）: 実装済みUI/UX（`SesameTileService`/`SesameTileContent`、`SesameActionActivity`、
  `CredentialsSettingsScreen`、`SesameComplicationDataSourceService`）をPLAN.mdのUX要件と
  突き合わせてレビューした（実機を伴わないコードベース上のレビュー）。観点別の所見は以下のとおり。

  - **視認性**: Tileはテキスト＋背景色（BL-017）で状態を表現しているが、アイコンは未実装のまま
    （制約として許容、DESIGN.md既存記載のとおり）。Complicationは短いテキストのみで、
    `ShortTextComplicationData`の性質上これ以上の視覚的差別化は困難と判断し、対応不要とした。
  - **誤操作防止**: `SesameActionScreen`（`SesameActionActivity.kt`）の解錠確認ボタンのラベルが
    「ホールドで解錠」だが、実装は`Button(onClick = {...})`によるワンタップ確認であり、
    実際の操作方法（タップ）とラベルの文言（ホールド＝長押し）が食い違っている。
    ユーザーが長押しを試みて誤操作するリスクがあるため**要修正**と判定した（BL-022）。
  - **セキュリティ/プライバシー**: `CredentialsSettingsScreen`のsecretKey入力欄が
    `visualTransformation`未指定のため平文表示されており、肩越しの盗み見リスクがある。
    **要修正**と判定した（BL-023）。
  - **フィードバックの分かりやすさ**: `CredentialsSettingsScreen`の保存ボタンに、保存成功を示す
    フィードバック（トースト等）がなく、ユーザーが保存されたか判断できない。また
    uuid/apikey/secretKeyが空文字列のままでも保存できてしまい、入力バリデーションがない。
    **要改善**と判定した（BL-024）。`SesameActionActivity`側は送信中表示→ハプティクスという
    設計（BL-008/BL-016）がPLAN.mdの要件を満たしており、追加対応不要と判断した。
  - **アクセシビリティ**: Tileの`Text.Builder`にcontentDescriptionが設定されておらず、
    スクリーンリーダー等での状態読み上げに支障がある可能性がある。**要改善**と判定した（BL-025）。

  対応が必要と判定した項目はBL-022〜BL-025として個別タスクに分解しBACKLOGへ登録した
  （BL-020は本レビューによるタスク分解をもって完了とする）。
- REQ-017（BL-022）: BL-019のUI/UXレビューで判明した表記不整合を修正。`SesameActionActivity.kt`の
  解錠確認ボタンのラベルを「ホールドで解錠」から「タップして解錠」へ変更し、実際の操作方法
  （ワンタップ確認）と文言を一致させた。
- REQ-018（BL-023）: BL-019のUI/UXレビューで判明したセキュリティ/プライバシー上の指摘を修正。
  `CredentialsSettingsScreen`のsecretKey入力フィールドへ`PasswordVisualTransformation`を設定し、
  肩越しの盗み見を防ぐマスキング表示にした（表示/非表示切り替えトグルは今回のスコープ外）。

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
- 参考実装: pysesame3(<https://github.com/mochipon/pysesame3>)、chanshige/sesame（PHP）などのOSSに
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

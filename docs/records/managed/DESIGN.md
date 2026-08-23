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

- 対象: Android（スマホ側）+ Wear OS（Watch側）の構成。`wear`は`mobile`のdynamic feature
  （単一AAB・単一`applicationId`、BL-036）として統合されている。CANDY HOUSE Sesame 5 + Hub 3の
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
- REQ-019（BL-024）: BL-019のUI/UXレビューで判明した設定画面のフィードバック・バリデーション不足を修正。
  `mobile.credentials.CredentialsInputValidator`（uuid/apikey/secretKeyBase64のいずれかが空欄なら
  無効と判定、Android非依存、単体テスト5件）を実装し、`CredentialsSettingsScreen`の保存ボタンへ
  `enabled = isInputValid`として組み込んだ。保存成功時は「保存しました」というテキストを
  `LaunchedEffect` + `delay`で2秒間表示するフィードバックを追加した（Snackbar等の恒久的な
  UI基盤導入は見送り、シンプルな状態表示に留めた）。
- REQ-020（BL-025）: BL-019のUI/UXレビューで判明したTileのアクセシビリティ不足を修正。
  `SesameTileService.buildStatusBox`のModifiersへ`ModifiersBuilders.Semantics`を追加し、
  `SesameTileContent.statusLabel(state)`をcontentDescriptionとして設定した。これにより
  BL-019で挙げたレビュー観点（視認性・誤操作防止・セキュリティ/プライバシー・
  フィードバックの分かりやすさ・アクセシビリティ）の指摘事項（BL-022〜BL-025）はすべて対応済み。
- REQ-021（コードレビュー、2026-08-20）: `/code-review`スキルのバックグラウンド実行が長時間応答不能に
  なったため（詳細は`~/.claude/skills/managed-agent-execution/SKILL.md`参照）、サブエージェントを
  使わず全モジュールの主要ソースを直接読み通す形でレビューを実施した。最重要の指摘は
  `CredentialsInputValidator`のsecretKey検証不足によるクラッシュリスクで、BL-026として登録した
  （詳細は同タスクの記載を参照）。
  その他、緊急度は低いが認識しておくべき軽微な指摘:
  - `SesameMessageListenerService.createHandler`が、メッセージ受信のたびに
    `EncryptedSharedPreferencesKeyValueStore.create(context)`（`MasterKey`構築を含む）を
    毎回実行しており、非効率。資格情報が変わるのは設定画面での保存時のみなので、キャッシュ化の
    余地がある。
  - Mobile/Wear双方の複数箇所（`SesameMessageListenerService`、`SesameTileService`、
    `SesameComplicationDataSourceService`）で`CoroutineScope(Dispatchers.IO).launch { ... }`を
    呼び出しのたびに使い捨てで生成しており、各Androidコンポーネントのライフサイクルに紐付いていない。
    通常は各コンポーネントが非同期処理の完了まで生存するため実害は限定的だが、将来的には
    lifecycle-aware なスコープ管理への置き換えが望ましい。
  - `CredentialsSettingsScreen`の`credentialsStore.load()`呼び出しがComposeの`remember`ブロック内
    （コンポジション時、メインスレッド）で同期的に行われている。EncryptedSharedPreferencesの
    読み込みは軽量なため実用上の問題は小さいが、原則としてI/Oはメインスレッド外が望ましい。
  これら3点は現時点では新規タスク化せず、将来の改善事項として本記載に留める
  （CLAUDE.mdの「過剰実装を避ける」原則を踏まえ、実害の小さい指摘まで機械的にタスク化しない）。
- REQ-022（BL-026）: コードレビューで発見したsecretKey検証不足によるクラッシュリスクを修正。
  `core.SesameCredentials`へ`secretKeyBytesOrNull`（Base64デコード失敗時・デコード後16バイト
  （AES-128鍵長）でない場合にnullを返す、例外を投げない安全なアクセサ、単体テスト4件）を追加した。
  `mobile.credentials.CredentialsInputValidator`をこの`secretKeyBytesOrNull`を使う実装へ変更し、
  不正な鍵は設定画面で保存できないようにした（既存テストのダミー値`"secret"`は新しい検証で
  不正と判定されるため、有効な16バイトBase64値へ差し替えた）。
  `mobile.messaging.SesameMessageListenerService.createHandler`も同じ`secretKeyBytesOrNull`を
  使うよう修正し、過去に保存された不正な鍵が万一残っていても例外でクラッシュせず`FAILURE`へ
  フォールバックするようにした（防御的プログラミング、保存時バリデーションと二重の防御）。
- REQ-023（BL-027）: Google Play限定公開に向けてmobile/wearの実アイコンを作成した。南京錠をモチーフに
  した`drawable/ic_launcher_background.xml`（背景、単色`#1E3A5F`）・`ic_launcher_foreground.xml`
  （前景、白いシルエット）と、`mipmap-anydpi-v26/ic_launcher.xml`・`ic_launcher_round.xml`
  （Adaptive Icon定義）を両モジュールへ追加し、AndroidManifestの`android:icon`/`android:roundIcon`
  （Wear側Complicationサービスの`android:icon`も含む）を`@android:drawable/sym_def_app_icon`から
  差し替えた。詳細と未確認事項は「実装制約」セクションのAndroidアイコンリソース項を参照。
- REQ-024（BL-028）: リリースビルド用の署名設定を追加。mobile/wearのbuild.gradle.ktsで
  `local.properties`（`.gitignore`対象）から`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/
  `RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`を読み込み、存在する場合のみ`signingConfigs`の
  `release`を構築してbuildTypesへ適用する設計にした。`local.properties`未設定時は
  `signingConfigs`自体を作成せず、`assembleDebug`はもちろん`assembleRelease`もunsignedの
  まま成功することを確認済み。README.mdへKeystore生成コマンド例と設定手順を追記した。
  Keystoreの実際の生成はBL-032（人手検証）で行う。
- REQ-025（BL-029）: リリースビルドのProGuard/R8設定を整備。mobile/proguard-rules.pro・
  wear/proguard-rules.pro（build.gradle.ktsから参照されていたが実体が存在しなかったため新規作成）
  へ、kotlinx.serializationの`@Serializable`クラス・コンパイル時生成される`$$serializer`・
  companionの`serializer()`を保護するkeepルールを追加し、mobile/wear双方のリリースビルドを
  `isMinifyEnabled=true`へ変更した。難読化・縮小を有効化した状態で`assembleRelease`が
  成功することを確認済み（実行時のシリアライズ動作そのものはユニットテストで検証できないため、
  BL-010/BL-011の実機検証で最終確認する）。
- REQ-026（BL-035、ユーザー依頼）: リリースビルドを簡易実施するための`scripts/release-build.bat`を
  作成した。`scripts/version.properties`に現在のversionCode/versionNameを永続化し、引数なし実行時は
  versionCodeを1インクリメント、`-VersionCode`/`-VersionName`指定時はその値を固定使用する。
  bat自体は複雑な文字列処理を避けるため`scripts/release-build.ps1`（PowerShell）への薄い
  エントリポイントとし、`pwsh`が使える場合は優先、無ければWindows PowerShellへフォールバックする。
  mobile/wearのbuild.gradle.ktsは`findProperty("appVersionCode"/"appVersionName")`で
  Gradleプロパティによる上書きに対応させ（未指定時は既定値を維持し既存ビルドに影響しない）、
  release-build.ps1から`-PappVersionCode`/`-PappVersionName`として渡す。
  **技術メモ**: Windows PowerShell 5.1（`powershell.exe`）はBOM無しUTF-8のスクリプトファイル内の
  日本語コメントを正しく解釈できず、`} finally {`付近で構文エラー（トークン不正）になった。
  スクリプトへUTF-8 BOMを付与することで解消した（`pwsh`優先呼び出しに変更した上での保険的対応）。
  動作確認はPowerShellツールが本セッションで機能しなかったため、Bashツール経由での
  `powershell.exe`/`pwsh`直接呼び出しとGit Bashからの`.bat`直接実行で代替検証した
  （インクリメント・固定値指定の両方でversion.propertiesの更新とAABビルドの成功を確認済み）。
- REQ-027（BL-030）: Google Play Console提出用のストア掲載情報・プライバシーポリシーのドラフトを
  `docs/store/STORE_LISTING.md`・`docs/store/PRIVACY_POLICY.md`として新規作成した。
  ストア掲載情報にはアプリ名・短い説明（80文字以内）・詳細な説明（4000文字以内）・カテゴリ案・
  対象デバイスを記載し、mobile/wearが別applicationIdの独立アプリである制約（BL-031参照）に
  言及した。プライバシーポリシーには収集する情報（uuid/apikey/secretKey、利用者本人が入力する
  情報でありサーバー側では収集しない）、保存方法（mobile側のEncryptedSharedPreferencesのみ、
  wear側は保持しない）、送信先（CANDY HOUSE Sesame APIのみ、広告/分析SDK不使用）、削除方法、
  お問い合わせ先を記載した。お問い合わせ先は実際のメールアドレス確定前のため、
  プレースホルダーのまま「未確認」と明記した（本人確認なしに個人のメールアドレスを
  ドラフトへ記入することを避けた）。実際の公開URLでのホスティングとPlay ConsoleのData safety
  申告への反映はBL-033（人手検証）で行う。
- REQ-028（BL-036、ユーザー依頼）: `mobile`/`wear`が別々の`applicationId`を持つ独立2アプリ構成
  （BL-031）を見直し、`wear`を`com.android.dynamic-feature`へ変更して`mobile`へ統合した。
  詳細な実装内容・マニフェストマージ対応・依存重複の解消は「実装制約」セクションの
  「Google Play配布方式」項を参照。品質ゲート一式（ktlintCheck/detekt/lintDebug/
  testDebugUnitTest/assembleDebug）に加え、`:mobile:bundleDebug`/`:mobile:bundleRelease`
  （統合後のAAB生成）の成功を確認済み。実機でのインストール・自動プッシュ配信の動作確認は
  BL-038（人手検証）。
- REQ-029（BL-043）: `:mobile:installDebug`実行時に`:mobile:packageDebugBundle`が
  「Title for module 'wear' is missing in the base resource table」で失敗する問題を修正した。
  `wear/AndroidManifest.xml`の`dist:title`が参照する文字列リソース名が`mobile`（base）側と
  `wear`（feature）側の双方で`app_name`という同名になっていたため、bundletoolがbaseリソース
  テーブル内で`wear`モジュール固有のタイトルを一意に解決できなかったことが原因
  （BL-036時点では未発生を確認済みだったリグレッション、発生源コミットは未特定）。
  `wear`固有の一意な文字列リソース`wear_module_title`を`mobile`側の`strings.xml`にのみ追加し、
  `dist:title`の参照先をこちらへ変更した（**制約**: `dist:title`が参照する文字列リソースは
  baseモジュール側にのみ定義する必要があり、featureモジュール側に同名リソースが存在すると
  このエラーが再発する）。`wear`側の`app_name`（ランチャーラベル等の表示名）は分離して維持した。
  検証中、`installDebug`ではPixel 8 Pro（watch機能なしのスマホ実機）にも
  `com.sesamiwear.wear.MainActivity`がインストールされる現象を確認した。BL-039の
  `dist:device-feature`条件はGoogle Play正式配信でのみ評価されローカルの`installDebug`では
  評価されない制約による可能性があるが未確認のため、BL-044（人手検証）へ切り出した。
- REQ-030（BL-045、ユーザー報告）: `mobile`側`CredentialsSettingsScreen`（資格情報設定画面）が
  画面トップのステータスバーと重なって表示される問題を修正した。`Column`の`modifier`に
  WindowInsets対応（`Modifier.padding(16.dp)`のみ）が設定されておらず、Edge-to-edge表示で
  コンテンツがステータスバー下に描画されていたことが原因。`Modifier.safeDrawingPadding()`を
  内側の`padding(16.dp)`より外側に適用し、ステータスバー・ナビゲーションバー・ディスプレイ
  カットアウトを含む安全領域を確保した。Pixel 8 Pro実機での目視確認済み。
- REQ-031（BL-056、ユーザー報告）: `mobile`側`CredentialsSettingsScreen`の入力欄
  （uuid/apikey/secretKey）に説明が一切なく、初めて使うユーザーが取得元を判断できない
  問題を改善した。画面冒頭に3ステップの手順説明（`SetupInstructions`）を追加し、
  各`OutlinedTextField`に`supportingText`で個別の取得元を追加した。あわせて
  デバイス0件時に「まだSesameが登録されていません」というガイダンスを追加した。
  Pixel 8 Pro実機での目視確認済み。
- REQ-032（BL-057、ユーザー依頼の実現可能性調査に伴う発見）: QRコード自動入力機能の
  実現可能性を調査する過程で、CANDY HOUSE公式ドキュメント
  （github.com/CANDY-HOUSE/API_document）にapikey（x-api-key）は
  `partners.candyhouse.co`ではなく`biz.candyhouse.co`（SESAME Biz 開発者ページ）で
  発行すると明記されていることが判明した。README.md/docs/store/STORE_LISTING.md/
  docs/store/PRIVACY_POLICY.md/`CredentialsSettingsScreen`のURL記載を修正した。
  `PLAN.md`は原初依頼内容のため変更していない（矛盾時はDESIGN.mdを優先する既存方針の通り）。
  **未確認事項**: `biz.candyhouse.co`は動的サイトのため実際のページ内容（APIキー発行UIの
  存在）はWebFetchで確認できておらず、公式ドキュメントの記述のみを根拠にしている。
  なお、QRコード（`ssm://UI?t=sk&sk=...`形式）読み取りによる自動入力機能自体は、
  非公開のバイナリフォーマットへの依存となりCANDY HOUSE側の仕様変更で壊れるリスクが
  高いため見送った（実機でSesame 5のQRコードをスキャンし160バイトのデータを確認したが、
  SESAME 3/4向けの既知構造（99バイト）とは一致せず、Sesame 5固有の構造は非公開で不明）。
- REQ-033（BL-058、ユーザー報告による重大バグ修正）: mobile側で資格情報を入力しても
  「追加」ボタンが有効化されない不具合を調査した結果、`core.SesameCredentials`の
  secretKeyデコード処理がBase64を前提としていたことが根本原因と判明した。CANDY HOUSE
  公式ドキュメント（`API_document/SesameOS3/webapi.md`）のコード例
  （例: `'2ebc2c087c1501480834538ff72139bc'`）ではsecretKeyは**16進数文字列（32文字=16バイト）**
  として扱われており、Base64ではない。ユーザーが`biz.candyhouse.co`のデバイス情報から
  生成した32文字の値を入力しても16バイトのBase64（標準的には24文字）とは長さが一致せず、
  `secretKeyBytesOrNull`が常にnullを返しバリデーションが通らなかった。
  `SesameCredentials.secretKeyBase64`フィールドを`secretKeyHex`へリネームし、
  デコードを`Base64.getDecoder()`から`java.util.HexFormat.of().parseHex()`へ変更した
  （Java 17標準API、大文字小文字どちらも受け付ける）。`CredentialsInputValidator`・
  `CredentialsSettingsScreen`（UI文言・supportingText・手順説明含む）・関連する単体テスト
  （`SesameCredentialsTest`/`SesameCredentialsStoreTest`/`CredentialsInputValidatorTest`）を
  すべてhex形式に合わせて修正した。`core.crypto.AesCmac`・`core.api.SesameCommandSigner`
  自体は鍵の16バイト長のみを要求するロジックでエンコーディング形式に依存しないため
  変更不要だった。`core.api.SesameApiClient`の`Base64`使用箇所（`history`タグの
  エンコード）はsecretKeyとは無関係のため変更していない。
  **未確認事項**: uuidの取得元は引き続きQRコードとして案内しているが、apikey/secretKey
  共に`biz.candyhouse.co`から取得できる可能性があり、正確な取得手順（画面遷移）は
  実際にユーザーが確認した範囲（デバイス情報からsecretKeyを生成できること）以上には
  未確認。実機での施錠/解錠疎通確認（BL-010）で最終検証する。
- REQ-034（BL-059、ユーザー報告）: ユーザーからuuid・apikeyも`biz.candyhouse.co`から取得する
  値であるとの報告を受け（Sesameアプリの「鍵をシェア」QRコードは使わない運用）、
  `CredentialsSettingsScreen`の案内文言を修正した。あわせて、手順説明
  （旧`SetupInstructions`の3行テキスト）と各入力欄の`supportingText`により初期表示の
  情報量が多いという指摘を受け、詳細説明をヘルプボタン（`TextButton`）タップで開く
  `AlertDialog`（`HelpDialog`）へ集約し、初期表示は入力欄（表示名/uuid/apikey/secretKey）を
  ラベルのみのシンプルな見た目にした。`androidx.compose.material.icons`系の依存が
  プロジェクトになかったため、アイコンボタンではなくテキストボタンで実装した。
  README.md/docs/store/STORE_LISTING.md/docs/store/PRIVACY_POLICY.mdの取得元説明も
  `biz.candyhouse.co`に統一した。`HelpDialog`にはSESAME Biz開発者ページ
  （`https://biz.candyhouse.co/biz/developer`）へ遷移する`TextButton`（`Intent.ACTION_VIEW`）
  も追加した（ユーザー追加依頼）。あわせて`CredentialsForm`のレイアウトを
  `Column(verticalArrangement = Arrangement.spacedBy(8.dp))`で統一し、入力欄同士および
  secretKey欄と「追加」ボタンの間隔が狭すぎるという指摘（ユーザー追加依頼）を受け、
  ボタン群の前に追加のスペーサーを挟み、ボタン自体も`Modifier.fillMaxWidth()`で
  横幅いっぱいの目立つ形状に変更した。Pixel 8 Pro実機でヘルプダイアログ・リンク遷移・
  レイアウト調整後の表示をすべて確認済み。

- REQ-035（BL-060、ユーザー報告による重大バグ修正）: Tileの「タップして設定」表示をタップしても
  何も起きないという報告を受け実機ログを確認した結果、
  `ProtoTilesPlTileViewInstance: Activity constraints not met. Not launching LaunchAction
  Activity`という警告を確認した。Wear Tiles APIの`LaunchAction`で起動するActivityは
  `android:exported="true"`が必須という制約があり、`wear/AndroidManifest.xml`の
  `SesameActionActivity`（施錠/解錠実行画面）・`TileConfigurationActivity`・
  `ComplicationConfigurationActivity`がすべて`exported="false"`のままだったことが原因と
  判明した。これによりTile Configuration機能だけでなく、基本の施錠/解錠操作自体（BL-014、
  実機未検証のまま長期間残っていた）も実機では動作していなかった可能性が高い重大なバグ。
  該当する3つのActivityを`exported="true"`へ修正した。Pixel Watch 2実機でTileから
  TileConfigurationActivityが正しく起動し、mobile側で登録済みのデバイス一覧が表示され
  選択・割り当てできることを確認した。
- REQ-036（BL-061、ユーザー合意）: Tile/Complicationの初期状態が常に「状態不明」（UNKNOWN）になり、
  UNKNOWN状態ではタップ不可のためコマンドを一切送信できないデッドロックを解消した。
  `core.SesameWearProtocol`へ`PATH_STATUS_REQUEST`（状態取得リクエスト、Fire-and-forget、
  結果は返さずDataItem変更として非同期に届く）を追加し、`wear.messaging.SesameCommandSender`に
  `requestStatus`を追加した。`SesameTileService.onTileRequest`/
  `SesameComplicationDataSourceService.onComplicationRequest`は、Tiles APIのレスポンス
  タイムアウト制約を避けるため既存のDataItemスナップショットで即座に応答しつつ、mobile側へ
  状態取得リクエストを送信するよう変更した。mobile側の`SesameMessageListenerService`は
  `PATH_STATUS_REQUEST`を受信すると`SesameApiClient.getStatus()`でSesame APIのGETを呼び、
  成功時に`SesameStatusSyncer`でDataItemへ同期する（結果はwear側へ返送しない）。
  wear側に新規`SesameStatusListenerService`（`WearableListenerService.onDataChanged`）を
  追加し、`STATUS_DATA_ITEM_PATH`配下のDataItem変更を検知したら`TileService.getUpdater()`と
  `ComplicationDataSourceUpdateRequester.requestUpdateAll()`でTile/Complicationの再描画を
  リクエストする。これによりBL-015の既知の制約（状態同期がコマンド送信成功時のみ）も解消した。
  Pixel Watch 2実機でTileから施錠/解錠操作ができることを確認した。
- REQ-037（BL-062、ユーザー報告）: Tileの連打により`SesameActionActivity`が短時間に複数回
  起動され、施錠/解錠コマンドが重複送信されてハプティクスが連続再生される不具合を修正した。
  実機ログで同一時間帯に5つの別々のタスクとして起動されていることを確認した。PLAN.mdの
  UX要件「通信中は明確な処理中表示＋ボタン無効化で二重送信防止」に対応する仕組み
  （`TileDisplayState.IN_PROGRESS`）はBL-007時点で用意されていたが、実際に「送信中」を
  検知してこの状態にする実装が入っておらず（`isCommandInProgress`が常に`false`固定）、
  連打を防げていなかった。`mobile.messaging.CommandDebouncer`（時刻取得を注入可能にした
  Android非依存クラス、単体テスト4件）を新規実装し、`SesameMessageListenerService`の
  `handleCommandRequest`で同一デバイスuuidへの2秒以内の重複コマンドを無視するようにした。
  Tile側の`isCommandInProgress`を実際に機能させる根本対応（送信中状態の管理）は、
  実装複雑度と緊急性のバランスから見送り、mobile側でのデバウンスのみで対応した。
- REQ-038（BL-063、ユーザー報告、対応中）: Tileのデザインを「何を操作しているか」
  「今どの状態か」「他のデバイスへの切り替え方法」が分かるUXへ改善する対応。3回にわたり反復した。
  **1回目**: デバイス名・状態アイコン・操作ラベル・デバイス変更ボタンを追加したが、
  ユーザーから「テキストが中心に集まっている」との指摘を受けた。原因は`buildStatusBox`が
  返すBoxに幅・高さの明示指定がなく、内容サイズにしか広がらないままタイル中央に配置されて
  いたこと（Wear Tilesのレイアウトは明示的にサイズ指定しない限り自身の内容分しか占有しない）。
  **2回目**: ルートを`Box`（`DimensionBuilders.expand()`でタイル全面、背景色もここへ移動）→
  内側`Column`（同じくexpand）→上部＝デバイス名/中央＝状態表示（拡大）/下部＝デバイス変更
  の3段構成へ再設計したが、ユーザーから「左レイアウトの文字が画面に収まっていない、ステータス
  色は右側だけでいい、各領域を角丸の四角ボタンで表現したい」との再指摘を受けた。原因は、
  円形画面のセーフエリア（内接正方形、対角192dpの円に対し約136dp四方）を考慮せず、タイルの
  literal な端（座標0や高さ最大値）に要素を配置していたため、ラウンドベゼルでテキストが
  部分的に欠けていたこと。また状態色をルート`Box`全面に敷いていたため、左右の領域区切りが
  視覚的に分からなくなっていた。
  **3回目（現行）**: `buildConfiguredTile`を、タイル全体をタイル端から`CONTAINER_PADDING_DP`
  （12dp）内側へ寄せた`Row`（左列＋右チップ）構成へ変更。左列（`buildLeftColumn`、幅
  `LEFT_COLUMN_WIDTH_DP`=76dp固定、`DimensionBuilders.expand()`で高さいっぱい）は、
  デバイス名チップ・デバイス変更チップ（`buildChangeDeviceBox`）を`DimensionBuilders.weight
  (1f)`で高さ均等分割し、間に`CHIP_SPACING_DP`（6dp）のSpacerを挟む。右チップ
  （`buildStatusBox`）は`expand()`で残り全域を占有。各チップは共通ヘルパー
  `buildChipModifiers`で角丸背景（`ModifiersBuilders.Corner`、半径`CHIP_CORNER_RADIUS_DP`
  =12dp）・内側パディング（`CHIP_INNER_PADDING_DP`=6dp）を持つ「角丸の四角ボタン」として
  表現する。状態色（`SesameTileContent.backgroundColorArgb`）は右チップの背景にのみ適用し、
  左側2チップは中立色`CHIP_NEUTRAL_COLOR_ARGB`（0xFF424242）で統一することで、領域の区切りと
  ステータス色の意味をひと目で区別できるようにした。detektの`LongMethod`
  （`buildConfiguredTile`/`buildStatusBox`が60行超過）と`TooManyFunctions`（クラス内関数数が
  閾値11に到達）の両方に反復して抵触したため、(a)`buildLeftColumn`へデバイス名チップ構築を
  統合、(b)`buildConfigurationLaunchAction`（2箇所の呼び出し元へインライン化し関数自体を削除）、
  (c)`buildCommandClickable`は`buildStatusBox`と分離したまま維持、という組み合わせで
  関数数10・各関数60行未満のバランスに調整した。
  **4回目（現行）**: 3回目を実機確認したユーザーから3点の指摘を受けた。(1)角丸チップの一部が
  依然として見切れる→`CONTAINER_PADDING_DP`を12f→13fへ約1割増やした（後日の実機確認で
  「もう一押しいける」との追加指摘を受け16fへさらに拡大）。(2)左側チップの
  テキスト色が既定の黒のままで、暗い中立背景（`CHIP_NEUTRAL_COLOR_ARGB`）に対して視認できない
  →`Text.Builder.setColor`を全箇所へ明示設定。左側2チップは白系（新設
  `CHIP_NEUTRAL_TEXT_COLOR_ARGB`=0xFFFFFFFF）、右側の状態チップは新設
  `SesameTileContent.statusTextColorArgb(state)`（状態色の明度に応じてコントラストを確保：
  通信中の明るいアンバー背景のみ濃色0xFF212121、施錠中・解錠中・未接続・不明の各背景は
  白0xFFFFFFFF）で個別に設定した。(3)デバイス名タップで状態更新をユーザー契機でも実施したい
  →新規`wear.action.SesameStatusRefreshActivity`（`PATH_STATUS_REQUEST`をFire-and-forgetで
  送信するのみの軽量Activity、施錠/解錠は行わない）を追加し、`AndroidManifest.xml`へ
  `exported="true"`で登録（BL-060で判明したWear TilesのLaunchAction制約を踏まえた登録）、
  `buildLeftColumn`のデバイス名チップに`Clickable`を追加してこのActivityを起動する
  `LaunchAction`を設定した。
  **未完了**: 実機での見た目・動作確認はユーザーの都合がつき次第実施予定（BL-063完了条件）。
- REQ-039（BL-064、ユーザー報告、調査中）: BL-064（コマンド成功後にTileが自動更新されない
  問題）の再検証で、対策済みのはずの自動更新が依然として機能していないとユーザーから
  再度報告があった。実機ログにはアプリ側のログ出力が一切なく、システムログ
  （ActivityManager等）からは`SesameActionActivity`の起動しか確認できず、
  mobile側のコマンド処理・DataItem同期・wear側の再描画リクエストのどこで問題が
  発生しているか切り分けできなかった。原因特定のため、`mobile.messaging
  .SesameMessageListenerService`（コマンド受信・デバウンス判定・API実行結果・DataItem同期
  ・結果送信の各段階）、`wear.messaging.SesameResultListenerService`
  （メッセージ受信・ハプティクス再生・Tile/Complication再描画リクエスト）、
  `wear.tile.SesameTileService`（`onTileRequest`/`buildConfiguredTile`のtileId・
  デバイス割当有無・接続ノード有無・DataItemスナップショットの値）へ`android.util.Log`
  （`Log.d`）を追加した。DataItemの同期は`DataClient.putDataItem`のurgentフラグを
  使っていても、mobile→wear間の物理的な同期完了を保証しない（`await()`はローカル書き込みの
  完了のみを示す）ため、`SesameResultListenerService`がコマンド結果受信直後に行う
  Tile再描画リクエストが、DataItem同期が完了する前に発火し古いスナップショットを
  読んでしまう競合状態が有力な仮説である（未確認）。その場合でも、後続の
  `SesameStatusListenerService.onDataChanged`が同期完了時に再度Tile再描画をリクエストする
  設計になっているため理論上は自己修復するはずだが、実機で解消しない理由（Wear Tilesの
  `requestUpdate`が非表示タイルに対して抑制される可能性、システム側のレート制限など）は
  未確認のまま。**未完了**: 次回実機操作時にlogcatを取得し、上記仮説を検証する。
- REQ-040（BL-066、ユーザー報告による重大バグ修正、対応中）: mobile/wear双方の実機で
  「アプリアイコンが2つ表示される」「mobileの設定画面が開けない」との報告を受けた。原因は、
  mobile（baseモジュール）・wear（feature、`android.hardware.type.watch`限定配信）の両方の
  `MainActivity`がそれぞれ独自のLAUNCHER intent-filterを持っていたこと。baseモジュールは
  `dist:conditions`の対象外で常にウォッチ側にも同梱されるため、ウォッチ側では常に
  `mobile.MainActivity`（タップすると`FEATURE_WATCH`判定で即`finish()`するガード付き、
  事実上機能しない）と`wear.MainActivity`（「Sesami Wear」のプレースホルダー表示）の
  2アイコンが共存していた。またローカルの`installDebug`（bundletool経由のAPK Set生成）では
  `dist:conditions`が評価されないため、スマホ側にも`wear.MainActivity`のアイコンが重複表示
  されていた（BL-044として記録していたローカル制約の懸念が現実の恒常的なバグだったことが
  判明。BL-044はこの修正で解消するため削除した）。「設定が開けない」報告は、スマホ側で誤って
  `wear.MainActivity`（設定機能を持たない）をタップしていたことが原因と推測される。対応として、
  `wear/AndroidManifest.xml`の`MainActivity`からLAUNCHER intent-filterを除去し
  （`android:exported="false"`へ変更、`android:icon`/`roundIcon`指定も除去。本Activityを
  独立起動する必要はなくTile/Complicationが主要導線のため）、`mobile.MainActivity`のウォッチ
  実行時ガードを`finish()`のみから、explicit Intent（`Intent().setClassName(packageName,
  "com.sesamiwear.wear.MainActivity")`。mobileはwearへコンパイル時依存できないためクラス名
  文字列を使用）で`wear.MainActivity`へ委譲する形へ変更した（`ActivityNotFoundException`時は
  フォールバックで`finish()`のみ行う防御コード付き）。これによりmobile/wear双方の実機で
  アイコンが1つに統一され、スマホでは常にmobile.MainActivity（設定画面）、ウォッチでは常に
  wear.MainActivityが開く。
  **未完了**: 実機での見た目・動作確認はユーザーの都合がつき次第実施予定（BL-066完了条件）。
- REQ-041（BL-067、ユーザー報告による重大バグ修正、対応中）: BL-066の実機確認中、Tileの
  「デバイス変更」を行うと「Sesami Wear」という無関係な文字が表示され、Tileへ戻ると
  デバイスは切り替わっているものの施錠/解錠ボタン押下時の状態がおかしいとの報告があった。
  原因は、`wear.MainActivity`/`SesameActionActivity`/`SesameStatusRefreshActivity`/
  `TileConfigurationActivity`/`ComplicationConfigurationActivity`のいずれも
  `android:taskAffinity`を明示指定しておらずデフォルト（アプリ共通）のタスク親和性を
  共有していたこと。BL-066で`mobile.MainActivity`がウォッチ実行時に
  `wear.MainActivity`へ`startActivity`（`FLAG_ACTIVITY_NEW_TASK`なし）していたため、
  そのタスクが`wear.MainActivity`をルートとして残留し、後続のTile LaunchAction
  （`TileConfigurationActivity`等、システムが`FLAG_ACTIVITY_NEW_TASK`で起動）がタスク
  親和性の一致により同一タスクへ積み重なっていたと判明した。`TileConfigurationActivity`が
  `finish()`すると背後に残っていた`wear.MainActivity`が露出して「Sesami Wear」表示となり、
  古いActivityインスタンスが再利用されうる状態（新しいIntent Extraが反映されない可能性）が
  「ボタン押下時の状態がおかしい」の原因と推測される。対応として、上記5つのActivityすべてへ
  `android:noHistory="true"`（フォアグラウンドを外れた時点で即座に破棄しタスクに残留させない）
  と`android:excludeFromRecents="true"`を追加し、`mobile.MainActivity`の
  `wear.MainActivity`への`startActivity`へ`Intent.FLAG_ACTIVITY_NEW_TASK`を明示付与した。
  **未完了**: 実機での動作確認はユーザーの都合がつき次第実施予定（BL-067完了条件）。
- REQ-042（BL-068、ユーザー報告）: Tile追加時のアイコンが大きすぎるとの指摘を受けた。
  `wear.tile.SesameTileService`に`android:icon`指定がなく、`<application>`のicon
  （mobileの`ic_launcher`、リング装飾なしの通常のスマホ向けアイコン）へフォールバックして
  いたことが原因。既に`SesameComplicationDataSourceService`用に用意されていたwear専用
  アイコン（`ic_launcher_wear`、コンプリケーション風のリング装飾付き）がTile追加ピッカーでは
  使われていなかった。`SesameTileService`へ`android:icon="@mipmap/ic_launcher_wear"`を
  明示指定してComplicationピッカーと意匠を統一し、`ic_launcher_wear_foreground.xml`の
  全パスを`<group android:scaleX="0.5" android:scaleY="0.5" android:pivotX="54"
  android:pivotY="54">`で包んで中心基準で50%縮小した（個々のpath座標は変更せずグループ
  変換のみで対応）。`ic_launcher_wear`は両ピッカーで共用のため、Complicationピッカー側にも
  同様に縮小が反映される（スマホ側の`ic_launcher`/`ic_launcher_round`は変更対象外）。
  **未完了**: 実機での見た目確認はユーザーの都合がつき次第実施予定（BL-068完了条件）。
  実機確認後、ユーザーから「Tile表示時のアイコンは前のサイズでよかった、Tile追加登録時の
  アプリ選択画面のアイコンだけ変更したい」と指摘があった。両者を別サイズへ分離しようと
  `wear/AndroidManifest.xml`の`<application>`へ専用の縮小版アイコン（新規
  `ic_launcher_wear_picker`）を設定したが、`mobile`側の`<application>`icon
  （`ic_launcher`）と競合しマニフェストマージが失敗した（`Attribute application@icon
  ... is also present at [:wear] ...`）。base（mobile）とfeature（wear）は最終的に
  1つの`<application>`タグへマージされるため、Tile追加時の「アプリ選択」画面とTile表示時の
  アイコンは同一の`SesameTileService.icon`リソースしか持てず、Android/Wear OSの仕様上
  別サイズにする手段がないと判明した（`ic_launcher_wear_picker`関連ファイルは削除して
  ロールバック）。ユーザーへ選択肢（縮小再適用＋クリーン再インストール／両方とも元サイズへ戻す）
  を提示した結果、前者を選択（前回の確認はupdate-in-place installであり、Wear OSの
  Tileピッカーの表示キャッシュ遅延で「アプリ選択」画面だけ古いサイズに見えていた可能性を
  切り分けるため）。`ic_launcher_wear_foreground.xml`への50%縮小（groupによるscale変換）を
  再適用し、両実機でuninstall後に再インストールした。
  クリーン再インストール後、Tile追加ピッカー・Tile表示・Complicationピッカーいずれの
  アイコンサイズもユーザーが実機で確認し「サイズはいい感じになった」と確認済み（完了）。
- REQ-043（BL-070、ユーザー報告）: Tileの施錠/解錠チップをタップした後に遷移する
  `wear.action.SesameActionActivity`の確認画面（解錠時のみ表示、`SesameCommandConfirmation`
  参照）で、ボタンが小さくテキストが見切れているとの指摘を受けた。原因は
  `androidx.wear.compose.material.Button`（既定で円形・小サイズ）に「タップして解錠」という
  長いテキストを詰め込んでいたこと。左＝キャンセル、右＝施錠/解錠、の角丸チップ2つへ
  再設計した。デザインはTile側（`SesameTileService`）と統一するため、共通の中立色定数
  `SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB`（新規、`SesameTileService`が個別に持っていた
  同名の`private`定数をここへ集約し重複を解消）をキャンセルボタンへ、
  `SesameTileContent.backgroundColorArgb`/`statusTextColorArgb`（操作後に遷移する状態
  ＝LOCKED/UNLOCKEDに対応する色）を施錠/解錠ボタンへ適用し、角丸半径も同じ12dpに揃えた。
  角丸Boxは`androidx.compose.foundation`の`Modifier.clip(RoundedCornerShape)`
  `.background()``.clickable()`を組み合わせた自作コンポーネント
  （`SesameActionChip`）で実装し、Wear Compose Materialの円形`Button`は使わずテキストの
  見切れを解消した。ユーザーが実機で確認し「イメージどおりにできてた」と確認済み（完了）。

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
- `mobile`: `applicationId=com.sesamiwear.mobile`、`minSdk=26`。`com.android.application`。
  Google Play配布上のbase moduleであり、`wear`をdynamic featureとして含む（BL-036）。
- `wear`: `com.android.dynamic-feature`（BL-036、旧`com.android.application`から変更）。
  `applicationId`・署名設定・`versionCode`/`versionName`は持たず`mobile`から継承する。
  `minSdk=26`（BL-036で`mobile`と統一。旧30、Wear OS 3.0+相当。**未確認事項**:
  `androidx.wear.compose`/`androidx.wear.tiles`等がminSdk26の実機で正常動作するかは
  実機未検証、BL-038参照）。`AndroidManifest.xml`に
  `uses-feature android:name="android.hardware.type.watch"`と
  `com.google.android.wearable.standalone=false`（スマホ連携必須アプリのため）、
  `dist:module`（`dist:instant=false`、install-time delivery、`dist:fusing include=true`）を
  設定済み。`dist:title`が参照する文字列リソース（`wear_module_title`）は`mobile`側の
  `strings.xml`にのみ定義する（BL-043。`wear`側に同名リソースが存在するとbundletoolが
  base resource table内でタイトルを解決できずAABパッケージングが失敗する制約があるため）。
- 依存バージョンは `gradle/libs.versions.toml`（Version Catalog）で一元管理する
  （AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01 / Wear Compose 1.4.1 等）。

### UI/UX方針（PLAN.md要件、未実装分を含む）

- Tileはロック状態（施錠中/解錠中）をアイコン・色でひと目表示する（BL-007）。
- 施錠はワンタップ即実行、解錠は誤操作防止のためホールド確認 or 確認ダイアログを挟む（BL-007）。
- 通信中は明確な処理中表示＋ボタン無効化で二重送信防止（BL-007）。
- 成功/失敗をハプティクスパターンで区別する（BL-008）。
- スマホ未接続時はTile上で明示し操作不可にする（BL-007）。
- Complicationで常時ロック状態を文字盤表示する（BL-009）。

### 複数Sesameデバイス対応方針（BL-046〜BL-055、ユーザー依頼、未実装）

PLAN.mdは単一Sesameデバイスを前提とした要件だったが、複数台（3〜5台程度を想定）のSesame 5を
1つのアプリから操作したいという追加要件が発生した。既存の`SesameCredentials`/
`SesameCredentialsStore`/`SesameWearProtocol`/Tile/Complicationはすべて単一デバイス前提の設計
であり、対応には以下の方針でデータ層からUI層まで変更する。

- **Wear側UX**: 「複数Tileインスタンス方式」を採用する。1つのTileが1台のSesameデバイスに対応し、
  ユーザーがTileギャラリーから必要な台数分のTileを追加する（Google Wear OSの標準的なマルチ
  インスタンスパターン）。各Tileはそのデバイス専用の表示名とロック状態のみをシンプルに表示し、
  タップで即座に対象デバイスへコマンド送信する。3〜5台程度の規模であれば、スワイプでの
  Tile切り替えは許容範囲であり、単一Tile内にリスト表示する方式より視認性・操作の明確さ
  （「どのデバイスの」「どんな操作か」が一目で分かる）を優先した。Complicationも同様に、
  文字盤の複数スロットへそれぞれ異なるデバイスを設定する方式とする。
- **データモデル**: `core.SesameCredentials`をリスト化し、`displayName`（ユーザーが設定する
  Sesame名、例:「玄関」）を追加した（BL-046、実装済み）。デバイスの一意識別子は、別途`deviceId`を
  持たせず、Sesame API上で既に一意な`uuid`をそのまま用いる設計に変更した（概念の重複を避けるため、
  BACKLOG登録時の想定から簡素化）。`core.SesameCredentialsStore`は
  `List<SesameCredentials>`全体をkotlinx.serializationでJSON化し単一キーで保存する`saveAll`/
  `loadAll`/`remove(uuid)`に対応させた（BL-047、実装済み）。
- **メッセージプロトコル**: `core.SesameWearProtocol`へ`encodeDeviceUuid`/`decodeDeviceUuid`
  （施錠/解錠コマンドのメッセージペイロードへ対象デバイスの`uuid`をUTF-8バイト列として載せる）と
  `statusDataItemPath(uuid)`（デバイスごとに一意なDataItemパスを生成し状態同期の衝突を防ぐ）を
  追加した（BL-048、BL-050、実装済み）。メッセージパス自体（`PATH_LOCK_REQUEST`等）は変更しない。
- **mobile側**: `CredentialsSettingsScreen`を複数デバイスの一覧・追加・編集・削除ができるUIへ
  変更し（BL-049、実装済み）、`SesameMessageListenerService`はメッセージペイロードから
  デコードした`uuid`で対象デバイスの資格情報を選択してAPIを呼び出し、`SesameStatusSyncer`は
  `statusDataItemPath(uuid)`でデバイスごとに状態同期するよう変更した（BL-050、実装済み）。
- **wear側Tile Configuration機構の技術調査結果（BL-051、実装済み）**:
  `androidx.wear.tiles`（本プロジェクトは1.4.1系、`RequestBuilders.TileRequest`/
  `ComplicationRequest`ベースの旧世代Tiles API。新世代`androidx.wear.protolayout`への移行は
  対象外）には、Android AppWidgetの`android:configure`属性のような「タイル追加時に自動的に
  設定Activityを起動する」標準機構は存在しない（`TileService`の`onTileAddEvent`等は通知目的の
  コールバックであり、バックグラウンドからのActivity自動起動はAndroidのポリシー上一般に
  許可されないため確実な設定導線にならない）。そのため、**「Tile自体がタップで設定画面へ
  誘導する」パターン**を採用する: (1) `RequestBuilders.TileRequest`は`getTileId(): Int`を持ち、
  `TileService.onTileRequest(requestParams)`内で`requestParams.tileId`としてタイル
  インスタンス固有のIDを取得できる、(2) `tileId`（Int）をキーとして選択デバイスの`uuid`を
  ローカル永続化する（`SesameKeyValueStore`パターンに倣った実装、DataStore Preferences等の
  新規依存追加は不要と判断）、(3) 未設定のtileIdの場合、Tile上に「タップして設定」等の誘導
  表示を出し、タップで`ActionBuilders.LaunchAction`によりConfiguration Activity
  （通常のAndroid Activity、Intent extraで`tileId`を渡す）を起動する、(4) デバイス選択・保存後は
  `TileService.getUpdater(context).requestUpdate(SesameTileService::class.java)`で対象Tileの
  再描画を要求する。Complicationも`ComplicationRequest`から`complicationInstanceId`
  （インスタンス固有のInt ID）を取得できる想定で、同じパターンを適用する
  （実際のAPI形状の最終確認はBL-054で行う）。
  この結果に基づき、Configuration Activityを実装してtileIdごとに対象デバイスを永続化する
  （BL-052）。`SesameTileService`/`SesameActionActivity`等のコマンド送信経路と
  `SesameStatusSnapshotReader`をtileId・uuid対応へ変更し（BL-053）、
  `SesameComplicationDataSourceService`も
  complicationInstanceIdごとの対象デバイス対応へ変更する（BL-054）。
- **検証**: 実機（複数のSesame実機）での動作確認はBL-055（人手検証）とする。

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
- Androidアイコンリソース（BL-027で対応済み、以降デザイン検討・BL-036のdynamic-feature化を経て
  配置を更新済み）: VectorDrawableベースのAdaptive Icon
  （`drawable/ic_launcher_background.xml`・`ic_launcher_foreground.xml`、
  `mipmap-anydpi-v26/ic_launcher.xml`・`ic_launcher_round.xml`）を作成し、AndroidManifestの
  `android:icon`/`android:roundIcon`から参照する形へ置き換えた。背景色`#1E3A5F`（濃紺）は
  `mobile`/`wear`共通。前景（`#C99A46`のゴールド）は「南京錠+ワイヤレス波」をモチーフとし、
  無線で施解錠できるスマートロックであることを示す。`wear`のみ、Wear OSのTile/Complicationと
  地続きの意匠にするため、前景に下部が途切れたリング（`trimPathStart`/`trimPathEnd`/
  `trimPathOffset`で描画、不透明度55%）を追加している。
  **配置（BL-036以降）**: `mobile`固有のアイコンは`mobile/src/main/res/`の
  `ic_launcher_background.xml`/`ic_launcher_foreground.xml`/`ic_launcher.xml`/
  `ic_launcher_round.xml`のまま。`wear`固有のアイコン（リング意匠あり）は
  `ic_launcher_wear_background.xml`/`ic_launcher_wear_foreground.xml`/`ic_launcher_wear.xml`/
  `ic_launcher_wear_round.xml`にリネームした上で**`mobile/src/main/res/`側に配置**している
  （`wear`モジュール側には置いていない）。これはAGPの制約（dynamic featureの
  `AndroidManifest.xml`内で参照するリソースはbase module側に存在する必要があり、
  feature側に置くとAAPTのリンク時に`resource ... not found`で解決できない）による。
  `wear/AndroidManifest.xml`の`MainActivity`・各Serviceからは
  `@mipmap/ic_launcher_wear`等の名前でこのbase側リソースを参照する。
  **未確認事項**: 図案はXMLパスの手書きによるものであり、視覚的な洗練度はデザイナーによる最終調整を
  前提としていない。また、Google Play Console提出に必要な高解像度アイコン画像（512x512 PNG、
  Play Storeの掲載用アイコン）はXMLベースでの生成が技術的に困難なため未対応（BL-034、人手検証で対応する）。
- Google Play配布方式（BL-031で記録、BL-036で統合実施済み）: 本アプリはもともと`mobile`
  （`com.sesamiwear.mobile`）と`wear`（`com.sesamiwear.wear`）が別々の`applicationId`を持つ、
  互いに独立した2つのAndroidアプリとして構成されていた。BL-036でこれを見直し、`wear`を
  `com.android.dynamic-feature`へ変更して`mobile`（base module）へ統合し、単一の
  `applicationId`（`com.sesamiwear.mobile`）・単一AAB・単一のPlay Store掲載ページで公開する
  Google Play推奨の標準的なWear OSアプリ配布方式へ移行した。
  - **統合により解消した制約**: (1) Play Storeの掲載ページがmobile/wearの1ページに統合される。
    (2) 標準配布方式の「スマートフォンへインストール後、ペアリング済みのWearデバイスへ
    自動的にwearアプリをインストールする」機能（Google Playの自動プッシュインストール）が
    利用可能になる見込み（**未確認**: 実機・Play Console経由での動作確認はBL-038、人手検証）。
    (3) Play Consoleでのリリース管理（審査・段階公開・Data safety申告等）がアプリ単位で1回になる。
  - **実装詳細**: `wear/build.gradle.kts`から`applicationId`/`signingConfigs`/
    `versionCode`・`versionName`/`minifyEnabled`・`proguardFiles`を削除（すべてbaseから継承、
    dynamic featureモジュールは自身のbuildTypesでminifyEnabledを設定できない制約がある）。
    `dependencies`に`implementation(project(":mobile"))`を追加（AGPの制約でdynamic feature
    モジュールはbaseモジュールへの依存宣言が必須。欠けると`processDebugMainManifest`が
    `Collection is empty`で失敗する）。`mobile/build.gradle.kts`の`android`ブロックへ
    `dynamicFeatures += setOf(":wear")`を追加。ルート`build.gradle.kts`と
    `gradle/libs.versions.toml`に`com.android.dynamic-feature`プラグインを追加登録
    （未登録だとプラグイン解決エラーになる）。`wear/AndroidManifest.xml`に
    `xmlns:dist`名前空間と`dist:module`（`dist:instant=false`、install-time delivery、
    `dist:fusing dist:include=true`）を追加。install-time配信には
    `dist:conditions`/`dist:device-feature`（`dist:name="android.hardware.type.watch"`）を
    あわせて設定し、`wear`モジュールがwatchハードウェア機能を持つデバイスにのみ配信されるようにした
    （BL-039、詳細は次段落「ランチャーアイコン重複問題」参照）。
  - **マニフェストマージの副作用と対処**: dynamic feature化後は`mobile`/`wear`の
    `AndroidManifest.xml`が1つにマージされるため、`<application>`要素の属性は両モジュール間で
    一致している必要がある。`android:theme`が競合したため各モジュールの`MainActivity`へ
    個別の`android:theme`指定へ移行し`<application>`側から削除した。同名だが内容が異なる
    ランチャーアイコンリソース（`ic_launcher*`）はAABパッケージング時に
    `contain entry ... with different content`で衝突したため、上記のとおり`wear`固有分を
    リネームしてbase側へ配置した。
  - **依存ライブラリの重複**: リリースビルド（R8 minify有効）検証時、`wear`が直接
    `implementation`していたguavaと、`mobile`が`play-services-wearable`経由で間接的に持つ
    guavaが重複し、R8が`ListenableFutureが2重定義`エラーで失敗した。`wear`側のguava依存を
    `compileOnly`（コンパイル時参照のみ）へ変更し、`mobile`側に`implementation(libs.guava)`を
    追加してbaseモジュールが実行時クラスパスへguava実装（`Futures`/`SettableFuture`等、
    `wear`が実際に使用するクラス）を提供する構成にした。
  - **ビルドコマンドの変更**: dynamic feature化後、`wear`モジュール単体の`:wear:assembleDebug`/
    `:wear:installDebug`/`:wear:bundleRelease`等は実行できない（base moduleのapplicationId
    artifactを解決できず失敗する。`:wear:installDebug`に相当するタスク自体が存在しない）。
    ビルド・インストールは常にルートからの一括実行（`./gradlew assembleDebug`等）または
    `:mobile:`配下のタスク（`:mobile:installDebug`/`:mobile:bundleDebug`/
    `:mobile:bundleRelease`）経由で行う。`scripts/release-build.ps1`も
    `:mobile:bundleRelease`のみに変更した（`wear`分は統合されたAAB1本に含まれる）。
  - **検証範囲**: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug`、
    `:mobile:bundleDebug`、`:mobile:bundleRelease`（署名なし、R8 minify込み）の成功を確認済み。
    実機でのインストール・自動プッシュ配信の動作確認、署名済みリリースビルドでの検証はBL-038
    （人手検証）へ計上している。
  - **ランチャーアイコン重複問題（BL-039〜042）**: 2026-08-22の実機検証（スマホ+Pixel Watchへ
    `:mobile:installDebug`）で、`dist:module`のinstall-time配信にデバイス種別を絞る条件が
    なかったため、`wear`のMainActivity（Tile設定画面）がスマホにも、`mobile`のMainActivity
    （資格情報設定画面、baseモジュールのため常時全デバイスへ配信される）がウォッチにも入り、
    両デバイスのランチャーにアイコンが2つずつ表示される状態を確認した。対応として、
    (1) `wear`側は上記のとおり`dist:conditions`/`dist:device-feature`でwatch限定配信へ変更
    （BL-039、対応済み）。(2) `mobile`はbaseモジュールのためこの条件付け方式では配信自体を
    止められないため、`mobile/MainActivity.kt`の`onCreate()`冒頭へ
    `packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)`のガードを追加し、
    ウォッチ実機で起動された場合は資格情報設定画面を表示せず`finish()`するようにした
    （BL-040、対応済み。ランチャーアイコン自体は base モジュールである以上ウォッチ側にも残るが、
    タップしても機能しないUIが表示されることは防止した）。(3) あわせて、wear用ランチャーアイコン
    （`ic_launcher_wear_foreground.xml`）のコンプリケーション風リングがAdaptive Iconの
    セーフゾーン（108dp viewport中心から半径33dp）を超えて欠けて表示される別問題も判明し、
    リング半径を40から30、ストローク幅を5から4へ縮小（外周が中心から32dpとなりセーフゾーン内に
    収まる）して修正した（BL-041、対応済み）。実機での最終目視確認はBL-042（人手検証）として
    残っている。

### 運用制約

- 実機（Pixel Watch / Sesame 5 + Hub 3）を用いる検証と、実資格情報を用いるAPI疎通確認は
  自律ループ実行モードの自動実行対象外とし、`区分: 人手検証`としてBACKLOGへ計上する。

## Copilot実装指示

- 初回実装時の出力要件・追加実装時の出力要件・要件トレーサビリティ要件は `CLAUDE.md` の
  該当セクション（「開発プロセス要件」「出力要件」）に従う。自律ループ実行モードでは
  `CLAUDE.md`「出力要件の読み替え」に従い、本文書とEXECUTE.md/BACKLOG.mdの更新をもって充足する。
<!-- COPILOT_RECORDS:END -->

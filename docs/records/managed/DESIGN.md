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
- 参考実装: pysesame3、chanshige/sesame（PHP）などのOSSにCMAC署名ロジックの実装例があり、
  Kotlin/Java向けの軽量ラッパーが少ないためロジック移植が前提（BL-002で対応）。
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

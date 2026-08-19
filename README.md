# Sesami Wear

Pixel WatchからCANDY HOUSE Sesame 5（+ Hub 3）を操作するAndroid / Wear OSアプリです。
要件・API仕様の背景は [PLAN.md](PLAN.md)、実装済み内容の詳細設計は
[docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md) を参照してください。

## アーキテクチャ概要

Android application（`mobile`）とWear OS application（`wear`）、両者が参照する純Kotlin/JVM
ライブラリ（`core`）の3モジュール構成です。

- `mobile`: apikey / secretKey / uuidを保持し、AES-CMAC署名生成とSesame APIへのHTTP通信を担当する。
- `wear`: Tile / Complicationの表示と、施錠/解錠の意図（コマンド種別のみ）をWearable Data Layer API
  （`MessageClient`）でスマホ側へ送信する。secretKeyは保持しない。
- `core`: `mobile` / `wear` 双方から参照する非機密のロジック（AES-CMAC実装、APIクライアント、
  Data Layer APIのメッセージパス定数、状態解決ロジック等）を配置する。Android依存コードは置かない。

secretKeyは機密性が高いためWatch単体には保持させず、施錠/解錠の実行は常にスマホ側で行う方針です。

## 前提環境

- JDK 17
- Android SDK（compileSdk / targetSdk 35、build-tools 35.0.0）
- Gradle 8.10.2（リポジトリ同梱のGradle Wrapperを使用するため別途インストール不要）

## セットアップ手順

### 1. Sesame APIの資格情報を取得する

施錠/解錠・状態取得には次の3点が必要です（詳細は [PLAN.md](PLAN.md) の「API仕様」を参照）。

- `uuid`（sesame2_uuid）: Sesameアプリの「鍵をシェア」QRコードから取得する
  （マネージャー/オーナー権限のキーで発行すること。ゲストキーは不可）
- `secretKey`: 同QRコードから取得する制御コマンドの署名鍵
- `apikey`: partners.candyhouse.co で発行するx-api-key

### 2. アプリへ資格情報を設定する

`mobile` アプリを起動すると設定画面（`CredentialsSettingsScreen`）が表示されるので、上記3点を入力して
保存してください。資格情報は `androidx.security.crypto` の `EncryptedSharedPreferences` で暗号化保存され、
ログへは出力されません。

## ビルド・実行・テスト

すべてリポジトリルートでGradle Wrapper経由で実行します（`gradlew.bat` はWindows用）。

```bash
# プロジェクト構成の確認
./gradlew tasks

# コードスタイル（ktlint）
./gradlew ktlintCheck
# 違反を自動修正する場合
./gradlew ktlintFormat

# 静的解析（detekt）
./gradlew detekt

# Android Lint
./gradlew lintDebug

# 単体テスト（core / mobile / wear 全モジュール）
./gradlew testDebugUnitTest test

# デバッグAPKのビルド
./gradlew assembleDebug
```

上記5コマンドが本リポジトリの品質ゲート（段階B）です。詳細は
[CLAUDE.md](CLAUDE.md)「本リポジトリの品質ゲート定義」を参照してください。

Markdownドキュメントの品質チェックは別コマンドです（[CONTRIBUTING.md](CONTRIBUTING.md) 参照）。

```bash
npx markdownlint-cli2 "**/*.md"
```

## プロジェクト構成

```text
sesami-wear/
├── core/    # 純Kotlin/JVMライブラリ（AES-CMAC、APIクライアント、Data Layerプロトコル定義等）
├── mobile/  # Androidアプリ（資格情報保存、Sesame API通信、Data Layer受信）
├── wear/    # Wear OSアプリ（Tile、Complication、施錠/解錠アクション、ハプティクス）
├── PLAN.md  # 要件・API仕様メモ・アーキテクチャ方針
└── docs/records/managed/
    ├── DESIGN.md    # 実装済み内容を統合した設計書（最新版）
    ├── BACKLOG.md   # 未対応事項・課題・次ステップ
    └── EXECUTE.md   # 実施記録
```

## 既知の未確認事項・制約

- Sesame APIのレスポンス構造（`SesameStatus`）と施錠/解錠コマンドの署名・ペイロード仕様は、
  参考実装 [pysesame3](https://github.com/mochipon/pysesame3) のソースコードを読んで判明した内容であり、
  CANDY HOUSE公式ドキュメントそのものは未参照です。実機での疎通確認（BACKLOG.md BL-010）で
  最終確認が必要です。
- Mobile側からWear側への状態同期（Tile / Complicationの表示更新）は、コマンド送信成功時にのみ行われます。
  Sesame純正アプリでの操作など他経路による状態変化は反映されません。
- `mobile` / `wear` とも実アイコン（mipmap）は未作成で、システム標準アイコンを暫定的に使用しています。
  配布前に専用アイコンへの差し替えが必要です。
- 実機（Pixel Watch + Sesame 5 + Hub 3）を用いる動作確認は自動実行できないため、
  [docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md) に人手検証タスクとして記録しています。

## 関連ドキュメント

- [PLAN.md](PLAN.md): 要件・API仕様メモ・アーキテクチャ方針
- [docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md): 実装済み内容の統合設計書
- [docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md): 未対応事項・課題・次ステップ
- [CONTRIBUTING.md](CONTRIBUTING.md): 開発プロセス・ブランチ運用・Markdown品質チェック
- [CLAUDE.md](CLAUDE.md): Claude Code運用ルール（品質ゲート定義を含む）

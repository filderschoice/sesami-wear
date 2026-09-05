# Sesami Wear

Pixel WatchからCANDY HOUSE Sesame 5（+ Hub 3）を操作するAndroid / Wear OSアプリです。
要件・API仕様の背景は [PLAN.md](PLAN.md)、実装済み内容の詳細設計は
[docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md) を参照してください。

> 本アプリは個人による非公式プロジェクトであり、CANDY HOUSE株式会社とは提携・協力関係にありません。
> 「CANDY HOUSE」「Sesame」は同社の商標または登録商標です。公開されているSesame APIを利用して
> 実装していますが、CANDY HOUSE社による動作保証・サポート対象外です。

## アーキテクチャ概要

Android application（`mobile`）とWear OS向けdynamic feature（`wear`）、両者が参照する純Kotlin/JVM
ライブラリ（`core`）の3モジュール構成です。`wear`は`mobile`の`applicationId`・署名・バージョンを
継承する単一のAndroid App Bundleとしてビルドされます
（[docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md) 参照）。

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

施錠/解錠・状態取得には次の3点が必要です。いずれも biz.candyhouse.co（SESAME Biz 開発者ページ）
で確認・発行できます（[PLAN.md](PLAN.md) の「API仕様」にも記述がありますが、同ファイルは初回依頼時点の
メモであり取得元の記載が古いため、本セクションが最新です）。

- `uuid`（sesame2_uuid）: 対象Sesameデバイスの識別子
- `apikey`: x-api-key
- `secretKey`: 施錠/解錠コマンドの署名（AES-CMAC）に使う16進数32文字（16バイト）の鍵

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

## リリースビルド・Google Play公開

Google Play（限定公開含む）へ提出する署名付きビルドを作成するには、`local.properties`
（`.gitignore`対象、コミットしないこと）へ以下のキーを追加してください。

```properties
RELEASE_STORE_FILE=/absolute/path/to/release-keystore.jks
RELEASE_STORE_PASSWORD=<keystoreのパスワード>
RELEASE_KEY_ALIAS=<key aliasの名前>
RELEASE_KEY_PASSWORD=<keyのパスワード>
```

Keystore自体は以下のコマンドで生成できます（秘密鍵の生成のため、自律ループ実行モードの対象外・
人手作業です。詳細は[docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md) BL-032参照）。

```bash
keytool -genkeypair -v -keystore release-keystore.jks -alias <key aliasの名前> \
  -keyalg RSA -keysize 2048 -validity 10000
```

生成したKeystoreファイルは、パスワードマネージャー等の安全な場所に保管し、**絶対にリポジトリへ
コミットしないでください**。`local.properties`が存在しない、または上記キーが未設定の場合、
`assembleDebug`等の通常のビルドには影響しません（リリースビルドの署名のみ未設定のままになります）。

署名済みのAAB（Android App Bundle、Google Play提出用）は以下でビルドします。
`wear`は`mobile`のdynamic featureのため、`:mobile:bundleRelease`1本でwear分も含めてビルドされます
（`:wear:bundleRelease`という独立タスクはfeatureモジュール単体では実行できません）。

```bash
./gradlew :mobile:bundleRelease
```

### バージョン管理付きの簡易リリースビルド（`scripts/release-build.bat`）

`versionCode`/`versionName`の管理を含めてリリースビルドを簡易に行うバッチファイルを用意しています。
現在のバージョンは`scripts/version.properties`に記録され、ビルド成功時のみ更新されます。

```bat
rem versionCodeを1インクリメントしてビルドする（versionNameは維持）
scripts\release-build.bat

rem versionCode/versionNameを固定値で指定してビルドする（インクリメントしない）
scripts\release-build.bat -VersionCode 10 -VersionName 1.1.0
```

`pwsh`（PowerShell 7）が利用可能な場合はそちらを優先して実行します（無い場合はWindows PowerShellへ
フォールバックします）。ビルドが失敗した場合、`scripts/version.properties`は更新されません。
`-PappVersionCode`/`-PappVersionName`のGradleプロパティで`mobile`の`versionCode`/`versionName`を
上書きしており（`wear`は`mobile`から継承するため個別指定不要）、通常の`./gradlew assembleDebug`等
には影響しません。

`mobile`と`wear`は単一の`applicationId`・単一AABとしてGoogle Playへ登録します
（Wear OSアプリの標準的な配布方式、詳細は
[docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md) の実装制約を参照）。

Play Console提出用のストア掲載情報・プライバシーポリシーのドラフトは
[docs/store/STORE_LISTING.md](docs/store/STORE_LISTING.md) /
[docs/store/PRIVACY_POLICY.md](docs/store/PRIVACY_POLICY.md) を参照してください。

## プロジェクト構成

```text
sesami-wear/
├── core/    # 純Kotlin/JVMライブラリ（AES-CMAC、APIクライアント、Data Layerプロトコル定義等）
├── mobile/  # Androidアプリ（資格情報保存、Sesame API通信、Data Layer受信）。base module
├── wear/    # Wear OSアプリ（Tile、Complication、施錠/解錠アクション、ハプティクス）。
             # mobileのdynamic feature（applicationId/署名/バージョンをmobileから継承）
├── PLAN.md  # 要件・API仕様メモ・アーキテクチャ方針
└── docs/records/managed/
    ├── DESIGN.md    # 実装済み内容を統合した設計書（最新版）
    ├── BACKLOG.md   # 未対応事項・課題・次ステップ
    └── EXECUTE.md   # 実施記録
```

## 既知の未確認事項・制約

- Sesame APIのレスポンス構造（`SesameStatus`）と施錠/解錠コマンドの署名・ペイロード仕様は、
  参考実装 [pysesame3](https://github.com/mochipon/pysesame3) のソースコードを読んで判明した内容であり、
  CANDY HOUSE公式ドキュメントそのものは未参照です。ただし実機（Sesame 5 + Hub 3）での状態取得・
  施錠/解錠の疎通確認は完了しています。
- Mobile側からWear側への状態同期（Tile / Complicationの表示更新）は、コマンド送信成功時と
  Tile/Complication表示時の状態取得リクエスト時に行われます。定期ポーリングは行わないため、
  Sesame純正アプリでの操作など他経路による状態変化は、次の表示更新まで反映されません。
- 文字盤のComplicationにデバイスを割り当てても状態文言が表示されない不具合を確認しています
  （[docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md) BL-072）。Tile側は正常に
  表示・操作できます。
- `mobile` / `wear` の実アイコンはVectorDrawableベースのAdaptive Icon（簡易的な図案）です。
  Google Play提出用の高解像度アイコン画像（512x512 PNG）は
  [docs/store/images/play_store_icon_512.png](docs/store/images/play_store_icon_512.png) に用意済みです。
- 実機（Pixel Watch + Sesame 5 + Hub 3）を用いる動作確認は自動実行できないため、
  [docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md) に人手検証タスクとして記録しています。
- `wear`のdynamic feature化（BL-036）に伴い`minSdk`を30から26（`mobile`と統一）へ変更しました。
  minSdk26のビルドでもPixel Watch実機でTileの表示・施錠/解錠が動作することは確認済みです。
  Google Playの自動プッシュインストール（スマホへのインストールだけでWatch側にもwearが導入される）が
  実際に機能するかは未検証です（BL-038、人手検証）。ローカルビルドを両デバイスへ直接インストールする
  手順は [docs/INSTALL.md](docs/INSTALL.md) を参照してください。

## 関連ドキュメント

- [docs/INSTALL.md](docs/INSTALL.md): スマホ・スマートウォッチへのアプリインストール方法
- [PLAN.md](PLAN.md): 要件・API仕様メモ・アーキテクチャ方針
- [docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md): 実装済み内容の統合設計書
- [docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md): 未対応事項・課題・次ステップ
- [SECURITY.md](SECURITY.md): 脆弱性の報告方法・対象範囲・サポート対象バージョン
- [CONTRIBUTING.md](CONTRIBUTING.md): 開発プロセス・ブランチ運用・Markdown品質チェック
- [CLAUDE.md](CLAUDE.md): Claude Code運用ルール（品質ゲート定義を含む）

## ライセンス

[MIT License](LICENSE)。Sesame APIレスポンス構造・コマンド署名アルゴリズムの実装にあたり
参考にした [pysesame3](https://github.com/mochipon/pysesame3)（MIT License）に感謝します。

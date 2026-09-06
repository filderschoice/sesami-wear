# Sesami Wear

Pixel WatchなどのWear OSスマートウォッチから、CANDY HOUSE Sesame 5（+ Hub 3）を操作するAndroid /
Wear OSアプリです。タイルからワンタップで施錠・解錠でき、ウォッチフェイスのコンプリケーションに
施錠状態を表示できます。

> 本アプリは個人による非公式プロジェクトであり、CANDY HOUSE株式会社とは提携・協力関係にありません。
> 「CANDY HOUSE」「Sesame」は同社の商標または登録商標です。公開されているSesame APIを利用して
> 実装していますが、CANDY HOUSE社による動作保証・サポート対象外です。

## 主な機能

- タイルから施錠・解錠をワンタップで実行（解錠は誤操作防止の確認画面付き）
- 成功／失敗を振動パターンで区別して通知
- ウォッチフェイスのコンプリケーションへ施錠状態を表示
- 複数台のSesameを登録し、タイル・コンプリケーションごとに対象を切り替え
- 2台以上を登録している場合、「全デバイス」での一括操作
- スマートフォン未接続時はタイル上に明示して誤操作を防止

secretKeyは機密性が高いためウォッチ単体には保持させず、施錠/解錠の実行は常にスマートフォン側で
行う設計です。

## アプリを使う方へ

**現在、Google Playへは未公開です（2026-09-05時点）。** 利用にはリポジトリからのローカルビルドが
必要です。

| 目的 | 参照先 |
| --- | --- |
| 使い方を知りたい（初期設定・タイル・操作） | [docs/USER_GUIDE.md](docs/USER_GUIDE.md) |
| インストールしたい | [docs/INSTALL.md](docs/INSTALL.md) |
| 更新内容を確認したい | [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) |
| 不具合を報告したい・質問したい | [docs/SUPPORT.md](docs/SUPPORT.md) |
| 収集する情報を知りたい | [docs/store/PRIVACY_POLICY.md](docs/store/PRIVACY_POLICY.md) |
| 脆弱性を報告したい | [SECURITY.md](SECURITY.md) |

---

以降は開発者向けの情報です。

## アーキテクチャ概要

スマートフォン向けAndroid application（`mobile`）、Wear OS向けAndroid application（`wear`）、
両者が参照する純Kotlin/JVMライブラリ（`core`）の3モジュール構成です。`mobile`と`wear`は同一の
`applicationId`（`com.sesamiwear.mobile`）を共有しつつ、**それぞれ独立したAndroid App Bundle**
としてビルドされ、Google Playでは1つのストア掲載ページの中で別々のトラックへ配信されます
（[docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md) 参照）。

- `mobile`: apikey / secretKey / uuidを保持し、AES-CMAC署名生成とSesame APIへのHTTP通信を担当する。
- `wear`: Tile / Complicationの表示と、施錠/解錠の意図（コマンド種別のみ）をWearable Data Layer API
  （`MessageClient`）でスマホ側へ送信する。secretKeyは保持しない。
- `core`: `mobile` / `wear` 双方から参照する非機密のロジック（AES-CMAC実装、APIクライアント、
  Data Layer APIのメッセージパス定数、状態解決ロジック等）を配置する。Android依存コードは置かない。

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
ログへは出力されません。利用者向けの詳細な操作手順は [docs/USER_GUIDE.md](docs/USER_GUIDE.md) に
記載しています。

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

署名済みのAAB（Android App Bundle、Google Play提出用）は以下でビルドします。スマートフォン用と
ウォッチ用で別々のAABが必要なため、2つのタスクを実行します。

```bash
./gradlew :mobile:bundleRelease   # スマートフォン用
./gradlew :wear:bundleRelease     # Wear OS用
```

| 成果物 | 出力先 | Play Consoleでのアップロード先 |
| --- | --- | --- |
| スマートフォン用 | `mobile/build/outputs/bundle/release/mobile-release.aab` | 電話・タブレット系のトラック |
| Wear OS用 | `wear/build/outputs/bundle/release/wear-release.aab` | **Wear OS専用トラック** |

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

リリース時は、利用者向けの変更点を [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) へ追記してください
（Google Play Consoleの「このリリースの新機能」欄への転記元になります）。

`mobile`と`wear`は同一の`applicationId`を共有し、Google Playでは1つのストア掲載ページの中で
別々のトラック（電話・タブレット系トラックとWear OS専用トラック）へ配信します。Googleは
単一のApp BundleへWear OSアプリをdynamic featureとして同梱する構成をサポートしておらず、
Wear OS向けリリースは専用トラックでの公開が必須です（詳細は
[docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md) の「Google Play配布方式」を参照）。
`versionCode`は全フォームファクタで一意である必要があるため、`mobile`は1始まり、`wear`は
1001始まりの独立した系列で管理します（`scripts/version.properties`）。

Play Console提出用のストア掲載情報・プライバシーポリシーのドラフトは
[docs/store/](docs/store/) 配下で管理しています。

## プロジェクト構成

```text
sesami-wear/
├── core/    # 純Kotlin/JVMライブラリ（AES-CMAC、APIクライアント、Data Layerプロトコル定義等）
├── mobile/  # スマートフォン用アプリ（資格情報保存、Sesame API通信、Data Layer受信）
├── wear/    # Wear OS用アプリ（Tile、Complication、施錠/解錠アクション、ハプティクス）。
│            # mobileと同一のapplicationIdを持つ独立したapplicationモジュール
├── scripts/ # バージョン管理付きリリースビルド（release-build.bat / .ps1）
├── config/  # detekt設定
├── rules/   # 統合ガードレール（セキュリティ・プライバシー・自律ループ実行モード統制）
├── PLAN.md  # 要件・API仕様メモ・アーキテクチャ方針（初回依頼時点のメモ）
└── docs/
    ├── USER_GUIDE.md     # 利用ガイド（アプリ利用者向け）
    ├── SUPPORT.md        # アップデート確認先・問い合わせ窓口
    ├── RELEASE_NOTES.md  # バージョンごとの変更点（アプリ利用者向け）
    ├── INSTALL.md        # 実機へのインストール手順
    ├── store/            # Google Play提出用のストア掲載情報・プライバシーポリシー
    ├── guidelines/       # ガードレール一式を他リポジトリへ導入するための汎用ガイド
    └── records/
        ├── managed/DESIGN.md    # 実装済み内容を統合した設計書（最新版）
        ├── managed/BACKLOG.md   # 未対応事項・課題・次ステップ
        ├── managed/EXECUTE.md   # 実施記録
        └── spec/FORMAT.md       # records配下の記述仕様
```

## 既知の未確認事項・制約

- Sesame APIのレスポンス構造（`SesameStatus`）と施錠/解錠コマンドの署名・ペイロード仕様は、
  参考実装 [pysesame3](https://github.com/mochipon/pysesame3) のソースコードを読んで判明した内容であり、
  CANDY HOUSE公式ドキュメントそのものは未参照です。ただし実機（Sesame 5 + Hub 3）での状態取得・
  施錠/解錠の疎通確認は完了しています。
- Mobile側からWear側への状態同期（Tile / Complicationの表示更新）は、コマンド送信成功時と
  Tile/Complication表示時の状態取得リクエスト時に行われます。定期ポーリングは行わないため、
  Sesame純正アプリでの操作など他経路による状態変化は、次の表示更新まで反映されません。
- 同一のTileを2つ以上追加することが、Wear OS側のタイル管理UIの制約でできません（コード側の
  多重インスタンス対応は実装済み）。実運用では単一Tileでのデバイス切り替えが主な使い方になります。
  Complicationは枠ごとに別デバイスを割り当てられることを実機確認済みです。
- `mobile` / `wear` の実アイコンはVectorDrawableベースのAdaptive Icon（簡易的な図案）です。
  Google Play提出用の高解像度アイコン画像（512x512 PNG）は
  [docs/store/images/play_store_icon_512.png](docs/store/images/play_store_icon_512.png) に用意済みです。
- 実機（Pixel Watch + Sesame 5 + Hub 3）を用いる動作確認は自動実行できないため、
  [docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md) に人手検証タスクとして記録しています。
- `wear`の`minSdk`は26（`mobile`と統一。旧30）です。minSdk26のビルドでもPixel Watch実機で
  Tileの表示・施錠/解錠が動作することは確認済みです。
- Google Playの自動プッシュインストール（スマホへのインストールだけでWatch側にもウォッチ用アプリが
  導入される）が実際に機能するかは未検証です（BL-038、人手検証）。ローカルビルドを両デバイスへ
  直接インストールする手順は [docs/INSTALL.md](docs/INSTALL.md) を参照してください。
- `wear`は当初`mobile`のdynamic featureとして単一AABへ統合していましたが、Googleがこの構成を
  Wear OSアプリの配布方式としてサポートしていないため、独立したapplicationモジュールへ変更しました
  （BL-090）。旧構成では`wear`の`uses-feature android.hardware.type.watch`が`mobile`側の
  マニフェストへマージされ、アプリ全体が腕時計必須と判定されてスマートフォンが配信対象から
  除外される状態でした。

## 関連ドキュメント

### 利用者向け

- [docs/USER_GUIDE.md](docs/USER_GUIDE.md): 初期設定・タイル・コンプリケーション・操作方法
- [docs/INSTALL.md](docs/INSTALL.md): スマホ・スマートウォッチへのアプリインストール方法
- [docs/SUPPORT.md](docs/SUPPORT.md): アップデート内容の確認先・問い合わせ窓口
- [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md): バージョンごとの変更点
- [docs/store/PRIVACY_POLICY.md](docs/store/PRIVACY_POLICY.md): プライバシーポリシー

### 開発者向け

- [PLAN.md](PLAN.md): 要件・API仕様メモ・アーキテクチャ方針
- [docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md): 実装済み内容の統合設計書
- [docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md): 未対応事項・課題・次ステップ
- [CONTRIBUTING.md](CONTRIBUTING.md): 報告の受け付け方針（Issueは受付、外部Pull Requestは非受付）・
  開発プロセス・品質ゲート
- [SECURITY.md](SECURITY.md): 脆弱性の報告方法・対象範囲・サポート対象バージョン
- [CHANGELOG.md](CHANGELOG.md): リポジトリの運用ルール・ドキュメントの変更履歴
- [CLAUDE.md](CLAUDE.md): Claude Code運用ルール（品質ゲート定義を含む）

## ライセンス

[MIT License](LICENSE)。Sesame APIレスポンス構造・コマンド署名アルゴリズムの実装にあたり
参考にした [pysesame3](https://github.com/mochipon/pysesame3)（MIT License）に感謝します。

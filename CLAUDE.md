# CLAUDE.md

Claude Code が本リポジトリで作業するときの実行ルールです。リポジトリルートに置かれているため、
セッション開始時に自動読み込みされます。

**共通規約の正本は [`.github/copilot-instructions.md`](.github/copilot-instructions.md) です。**
ファイル名は GitHub の規約で固定されていますが、内容は全AIエージェント共通の実行ルールで、
下記の `@import` により本ファイルと同時に自動読み込みされます。
本ファイルには Claude Code 固有の差分と、本リポジトリ固有の情報（アーキテクチャ概要・品質ゲート定義）
のみを定義します。共通規約と矛盾する場合は本ファイルを優先します。

@rules/guardrails-unified.v1.md
@.github/copilot-instructions.md

`@import` で自動展開するのは上記2ファイルだけです。その他の参照先と読むタイミングは、共通規約
「参照するドキュメント」の表を参照してください。

## 共通規約に対する Claude Code 固有の差分（MUST）

| 項目 | 共通規約 | Claude Code での差分 |
| --- | --- | --- |
| git操作 | エージェントは実行せず、コマンド例のみ提示する | 同左。ただし「自律ループ実行モード」中の作業ブランチへの `git add` / `git commit` のみ例外。`git push` はモードを問わず常にユーザーが実行する |
| 1ブランチ1目的の例外 | `BACKLOG.md` の複数項目の一括対応のみ | 上記に加えて「自律ループ実行モード」も例外。いずれも対象タスクの `id` をブランチ名・コミット本文・PR説明へ列挙する |
| 指示参照の優先順位 1位 | エージェントのシステム指示 | Claude Code ハーネスのシステムプロンプト |
| PR説明文・コードレビュー | `.github/instructions/pr.instructions.md` に従う | Claude Code には同ファイルを自動適用する機構が無いため、生成時に**明示的に同ファイルを読んでから**従う |
| 記録ファイルの編集権限 | 規定なし | `.claude/settings.json` の `permissions.allow` により権限プロンプトなしで反映される（下記「記録ファイルの権限設定」） |

## リポジトリの現状とアーキテクチャ概要

### 現状

Pixel WatchからCANDY HOUSE Sesame 5（+ Hub 3）を操作するAndroid/Wear OSアプリの実装が進行中です
（`core` / `mobile` / `wear` の3モジュールGradleプロジェクトが存在し、主要機能は実装済み）。
実装済み内容・設計意図・制約は [docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md) に、
セットアップ・ビルド・実行手順は [README.md](README.md) にまとまっています。未対応事項・人手検証待ち項目は
[docs/records/managed/BACKLOG.md](docs/records/managed/BACKLOG.md) を参照してください。

実装作業を依頼された場合は、まず上記3ファイル（DESIGN.md / BACKLOG.md / README.md）で現在の実装状況を
確認してから着手してください。要件・API仕様の背景（元の依頼内容）は `PLAN.md` に残っていますが、
実装済み内容の最新版は DESIGN.md が優先します。

### モジュール構成

- `core`: `mobile` / `wear` 双方から参照する非機密の純Kotlin/JVMロジック（AES-CMAC実装、
  Sesame APIクライアント、Data Layer APIのメッセージパス定数、状態解決ロジック等）。
  Android依存コードは置かない。
- `mobile`: apikey / secretKey / uuidを保持し、AES-CMAC署名生成とSesame APIへのHTTP通信を担当する
  スマートフォン用Androidアプリ（`com.android.application`）。
- `wear`: Tile / Complicationの表示と、施錠/解錠の意図（コマンド種別のみ）をWearable Data Layer API
  （`MessageClient`）でスマホ側へ送信するWear OS用アプリ（`com.android.application`）。
  secretKeyは保持しない。

`mobile` と `wear` は同一の `applicationId`（`com.sesamiwear.mobile`）を共有する独立した2つの
applicationモジュールで、それぞれ別のAABとしてビルドします。Google Playでは1つのストア掲載
ページの中で、電話・タブレット系トラックとWear OS専用トラックへ別々に配信します（BL-090。
Googleは単一AABへWear OSをdynamic featureとして同梱する構成をサポートしていません。
詳細は DESIGN.md「Google Play配布方式」参照）。

secretKeyは機密性が高いためWatch単体には保持させず、施錠/解錠の実行は常にスマホ側で行う方針です
（詳細は DESIGN.md「アーキテクチャ方針」参照）。

### 主要な処理フロー（Wearable Data Layer API経由）

`core.SesameWearProtocol` が定義するメッセージパス定数を軸に、`mobile`/`wear`間は
`MessageClient`（コマンド送受信）と`DataClient`（状態同期）の2系統で通信します。各経路の起点・終点は
以下のとおりです。処理の詳細（引数・エラー時の挙動・画面仕様）は DESIGN.md の該当節が正本です。

| 経路 | 起点 → 終点 | DESIGN.md の該当節 |
| --- | --- | --- |
| 施錠/解錠コマンド送信（wear → mobile） | `wear.tile.SesameTileService` → `wear.action.SesameActionActivity`（提示コマンドは `core.display.SesameTileActions`）→ `wear.messaging.SesameCommandSender` → `PATH_LOCK_REQUEST` / `PATH_UNLOCK_REQUEST` | wear側コマンド送信・結果受信 |
| コマンド実行（mobile） | `mobile.messaging.SesameMessageListenerService` → `SesameCommandHandler` → `core.api.SesameApiClient`（AES-CMAC署名付きPOST） | mobile側コマンド処理 |
| 状態同期（mobile → wear） | `mobile.messaging.SesameStatusSyncer` → `STATUS_DATA_ITEM_PATH` の DataItem | Data Layer APIプロトコル定義 |
| 結果返送（mobile → wear） | `PATH_COMMAND_RESULT` → `wear.messaging.SesameResultListenerService` → `SesameResultHandler` → `wear.haptics.SesameHapticPlayer` | wear側コマンド送信・結果受信 |
| 状態表示（Tile/Complication） | `wear.messaging.SesameStatusSnapshotReader` → `core.SesameStatusSnapshotFactory` → Tile/Complication | Tile / Complication |

着手前に知っておく必要がある制約（詳細は DESIGN.md「複数Sesameデバイス対応方針」）:

- 本アプリは複数のSesameデバイスを扱うため、各メッセージは対象デバイスの`uuid`をペイロードへ載せる
  （`core.SesameWearProtocol.encodeDeviceUuid` / `decodeDeviceUuid`）。`ALL_DEVICES_TARGET_UUID`
  （`"__all_devices__"`）は「登録済み全デバイス」を表す特別値で、`core.display.SesameDeviceTargets.targetUuids`
  が展開した全uuidへwear側が個別に送る（mobile側は単一デバイス処理をN回受けるだけ）。
- 状態取得（`PATH_STATUS_REQUEST`）はFire-and-forget送信で結果が返らない。`STATUS_DATA_ITEM_PATH`の
  DataItem変更として非同期に届く（`wear.messaging.SesameStatusListenerService`が受けて再描画を要求する）。
- 他経路（Sesame純正アプリでの操作等）による状態変化は検知されない
  （README.md「既知の未確認事項・制約」参照）。
- mobile側は`mobile.messaging.CommandDebouncer`が同一uuidへの2秒以内の重複コマンドを無視する
  （Tile連打による多重送信・多重ハプティクスの防止）。
- Tile / Complicationの対象デバイスは、インスタンス固有のID（`tileId` / `complicationInstanceId`）ごとに
  非暗号化の`SharedPreferences`へ永続化する（機密情報を含まないため）。

### 参照先マップ

個別ファイルの内容は各正本が持ちます。ここでは「知りたいこと → 参照先」だけを示します。

| 知りたいこと | 参照先 |
| --- | --- |
| 実装済み内容・設計意図・制約（実装の正本） | `docs/records/managed/DESIGN.md` |
| 未対応事項・人手検証待ち項目 | `docs/records/managed/BACKLOG.md` |
| セットアップ・ビルド・実行・リリース手順、既知の未確認事項・制約 | `README.md` |
| 実機へのインストール手順（Wi-Fi経由のADBペア設定、`ANDROID_SERIAL`） | `docs/INSTALL.md` |
| 利用者向けの操作説明（UIの表示文言を変えたら追随させる） | `docs/USER_GUIDE.md` |
| 利用者向けの変更点・問い合わせ窓口・クローズドテスト参加手順 | `docs/RELEASE_NOTES.md` / `docs/SUPPORT.md` / `docs/CLOSED_TEST.md` |
| Google Play掲載情報・プライバシーポリシー・アイコン | `docs/store/`（索引は `docs/store/README.md`） |
| 脆弱性報告の受付方針 | `SECURITY.md`（GitHubがSecurity policyとして参照するためルートから移動しない） |
| 開発プロセス・ブランチ規約・レビュー要件・Issue受付方針 | `CONTRIBUTING.md` |
| 運用ルール・ドキュメントの変更履歴 | `CHANGELOG.md` |
| 要件・API仕様の背景（原初の依頼内容） | `PLAN.md`（実装済み内容は DESIGN.md を優先する） |
| 記録ファイルの記述仕様（唯一の参照元） | `docs/records/spec/FORMAT.md` |
| 他リポジトリへの配布・導入手順 | `docs/guidelines/`、`templates/` |

設定ファイルの所在（変更時に片方だけ直す事故が起きやすい箇所）:

- detekt: `config/detekt/detekt.yml`（`MagicNumber`無効、`LongMethod`閾値60、`maxIssues: 0`）と、
  ルート `build.gradle.kts` の `subprojects` ブロック（`buildUponDefaultConfig = true`）の2箇所。
- markdownlint: `.markdownlint-cli2.yaml`（行長120、コードブロック/テーブルは対象外、MD060無効、
  MD024は`siblings_only`、`**/*.local.md` は検査対象外）。
- リリースビルド: `scripts/release-build.bat` / `.ps1` と `scripts/version.properties`。
- GitHub: `.github/ISSUE_TEMPLATE/`（Issueフォームと空Issueの無効化）、
  `.github/PULL_REQUEST_TEMPLATE.md`（品質ゲート実行・資格情報混入確認のチェックリスト）。
  外部からのPull Requestは受け付けていない（`CONTRIBUTING.md` 参照）。

### よく使うコマンド

Gradle Wrapper経由ですべてリポジトリルートから実行します（`gradlew.bat` はWindows用）。
品質ゲートのコマンドは後述「本リポジトリの品質ゲート定義」、ビルド・実行手順の詳細は `README.md`、
実機インストール手順は `docs/INSTALL.md` が正本です。ここには、そちらに無い操作と制約だけを置きます。

単一テストクラス・メソッドのみ実行する場合は `--tests` を使います（`core` は素の `test`
タスク、`mobile`/`wear` は `testDebugUnitTest` タスクです）。

```bash
./gradlew :core:test --tests "com.sesamiwear.core.crypto.AesCmacTest"
./gradlew :mobile:testDebugUnitTest --tests "com.sesamiwear.mobile.credentials.CredentialsInputValidatorTest"
./gradlew :wear:testDebugUnitTest --tests "com.sesamiwear.wear.complication.SesameComplicationContentTest"
```

単体テストはAndroid非依存のクラス（`core`全般、`mobile.messaging.SesameCommandHandler`、
`wear.complication.SesameComplicationContent`等）に集中しており、Android依存クラス（`*Service` / `*Activity` /
Compose画面）はテスト対象外です。ロジックを追加する際は、Android依存部から切り離した純Kotlinの
クラス・objectへ置くと検証可能になります（detektの`LongMethod`/`TooManyFunctions`回避にもなります。
DESIGN.md「実装制約 > 技術制約」参照）。

`mobile` と `wear` は同一の `applicationId`（`com.sesamiwear.mobile`）を共有します（BL-090）。
**インストール先のデバイス種別に応じて実行するタスクが異なり、1台のデバイスに両方は入りません**
（後から入れた方が前のものを置き換えます）。同時接続時は `ANDROID_SERIAL` でインストール先を1台へ
固定します。リリースAABも同じ理由で2つ生成が必要です（`versionCode` は `mobile` が1始まり、
`wear` が1001始まりの独立系列。`scripts
elease-build.bat` は1回の実行で両方をビルドします）。

```bash
ANDROID_SERIAL=<スマホのデバイスID>    ./gradlew :mobile:installDebug
ANDROID_SERIAL=<ウォッチのデバイスID>  ./gradlew :wear:installDebug

./gradlew :mobile:bundleRelease   # → Play Consoleの電話・タブレット系トラックへ
./gradlew :wear:bundleRelease     # → Play ConsoleのWear OS専用トラックへ
```

## 本リポジトリの品質ゲート定義（MUST）

共通規約「品質ゲート」を本リポジトリでの具体的なコマンドと合否基準へ落とし込みます。Copilot・Claude Code など
全エージェント共通で使用する正本です。自律ループ実行モードでは各イテレーションでこの表を実行し、記録へ残します。

| ゲート | コマンド | 合否基準 |
| --- | --- | --- |
| コードスタイル（ktlint） | `./gradlew ktlintCheck` | 終了コード0 |
| 静的解析（detekt） | `./gradlew detekt` | 終了コード0（`maxIssues: 0`） |
| Android Lint | `./gradlew lintDebug` | 終了コード0 |
| 単体テスト | `./gradlew testDebugUnitTest test` | 終了コード0（`core` / `mobile` / `wear` 全モジュール） |
| ビルド | `./gradlew assembleDebug` | 終了コード0 |
| Markdown静的解析 | `npx markdownlint-cli2 "**/*.md"` | 終了コード0（`Summary: 0 issues`） |
| 記録ファイルのYAML検証 | `docs/records/spec/FORMAT.md`「YAMLとしての体裁」の手順でマーカー内を抽出し `yaml.safe_load` へ通す | 例外なく読み込めること |
| 脆弱性チェック | (未導入) | Gradleの依存脆弱性スキャン（`dependencyCheck` 等）は未導入。導入した場合は本節を更新する |

- 上記コマンドを変更・追加した場合は本セクションと `CONTRIBUTING.md`「品質ゲート」の両方を更新する
  （定義とドキュメントの乖離を禁止）
- ktlintの違反は `./gradlew ktlintFormat` で自動修正できる（品質ゲートには含めない）
- 本リポジトリにCIはなく、上記コマンドのローカル実行が唯一の品質ゲート
  （経緯は `CONTRIBUTING.md`「Markdownlintのローカル実行」参照）
- Wear OS実機（Pixel Watch）およびSesame実機を伴う検証、実資格情報（apikey / secretKey）を用いる
  疎通確認は自動実行の対象外とし、`BACKLOG.md` へ `区分: 人手検証` として記録する。自動品質ゲートの
  合否判定からは除外する

## 記録ファイルの権限設定（MUST）

`docs/records/managed/` 配下の3ファイルの編集は、`.claude/settings.json`（プロジェクト設定、リポジトリ管理下）の
`permissions.allow` により権限プロンプトなしで反映されます。記録ファイルは共通規約により人手編集を前提とせず、
内容の妥当性はコミット前の差分確認と `FORMAT.md` 準拠で担保するため、更新のたびに確認を挟む意味が無いことに
よります。

ルールファイル（`CLAUDE.md` / `rules/` / `CONTRIBUTING.md`）・`.github/` 配下・
`docs/records/spec/FORMAT.md` の編集は、従来どおり確認を挟みます（記述仕様そのものの変更は人の判断が必要）。

自律ループ実行モードでは、個別の指示がなくても各イテレーションの完了を記録ファイルの更新契機とします。

## 自律ループ実行モード（Loop Engineering）

ユーザーの明示指示により、人の応答を待たずに複数イテレーションを連続実行する運用モードです。
**Claude Code 固有であり Copilot は対象外です**（Copilot は常に git 操作を実行しません）。

### 適用条件（MUST）

- ユーザーが本モードの開始を明示的に指示していること
- 指示されたスコープ内でのみ有効で、ループ終了と同時に通常の対話モードへ戻る
- 適用条件を満たさない場合、`git add` / `git commit` を実行してはならない

### 起動方法（MUST）

適用条件を満たしたら、**Skill `autonomous-loop` を起動し、その手順に従ってください**。
開始手順・イテレーション手順・タスク選択規則・確認質問の代替・出力要件の読み替え・完了条件・終了時の
報告は同スキルが正本です。手順を記憶や推測で代用せず、必ずスキルを読んでから開始します。

手順をスキルへ分離しているのは、本モードが明示指示時にしか使われないためです。統制要件
（許可・禁止される操作、停止条件、確認事項の非同期化、秘密情報の取り扱い、監査）は
`rules/guardrails-unified.v1.md` セクション12として常時読み込みされており、スキルを読み込んでいない
状態でも禁止事項は有効です。ブランチ・コミット規約は `CONTRIBUTING.md` の同名節が正本です。

## 保守

共通規約の変更は `.github/copilot-instructions.md` へ、Claude Code 固有の変更と本リポジトリ固有の情報は
本ファイルへ反映します（役割分担の正本は `CONTRIBUTING.md`「エージェント指示ファイルの構成規約」）。
ガードレール一式の配布元は `C:\Dev\repo\copilot-rules` で、追従判断は同リポジトリの `CHANGELOG.md` を
参照します。

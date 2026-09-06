# 変更履歴

このリポジトリの運用ルール・ドキュメント・ガードレールの変更は本ファイルに記録します。
記録先は変更の種類によって分かれています。

- アプリ利用者向けのバージョンごとの変更点: [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md)
- コード修正1件ごとの実施記録: [docs/records/managed/EXECUTE.md](docs/records/managed/EXECUTE.md)
- 本ファイル: 上記以外（運用ルール、ドキュメント構成、ガードレールの変更）

## 2026-08-18（自律ループ実行モード対応）

Claude Code による自律ループ実行（Loop Engineering）でアプリ実装を進めるため、
これを阻害するルールの改訂と、不足していたルールの追加を行いました。

- `rules/guardrails-unified.v1.md`
  - セクション5.3の禁止事項を改訂。`git push` と規定ブランチへの直接コミットは常時禁止とし、
    `git add` / `git commit` はセクション12の条件下でのみ許可する形へ変更。
  - セクション12「自律ループ実行モード統制」を新設（実行条件・許可範囲・停止条件・
    確認事項の非同期化・秘密情報の取り扱い・監査）。
  - セクション11は欠番として明示（旧「開発プロセス統制」削除の経緯を保持）。
  - セクション3.2にセクション12.4への参照を追加。
  - セキュリティ担当・プライバシー担当の承認完了に伴い、冒頭の暫定扱い注記を削除（2026-08-18）。
- `CONTRIBUTING.md`
  - AIエージェントのgit操作禁止規定に、自律ループ実行モードの例外を追記。
  - 「自律ループ実行モードのブランチ・コミット規約」を新設。ループ全体で長期作業ブランチ1本
    （`feature/loop-<YYYYMMDD>-<主題>`）を使用し、「1ブランチ1目的」原則の例外として扱う。
- `CLAUDE.md`
  - 「自律ループ実行モード（Loop Engineering）」セクションを新設。開始手順、イテレーション手順、
    タスク選択規則、対話前提の出力要件の読み替え、完了条件、終了時の報告を定義。
  - 「本リポジトリの品質ゲート定義」を新設。段階A（Markdownlintのみ）／段階B（Gradle）の
    コマンドを明記し、実機・実資格情報を用いる検証を自動実行対象外と定義。
  - 確認が必要な場合の規定に、ループ中は `状態: 要確認` として記録し継続する運用を追記。
- `docs/records/spec/FORMAT.md`
  - `BACKLOG.md` に必須キー `依存`、任意キー `根拠` を追加。`状態` に `要確認` を追加。
  - `区分: 人手検証` の意味を定義（ループの完了判定から除外）。
  - `EXECUTE.md` に任意キー `details.関連ID` を追加（ループ実行時は必須）。
- `docs/guidelines/RULE.md`
  - 汎用フレームワークへ「自律ループ実行モード」採用時の定義必須項目とDoD追加条件を記載。
- `.github/copilot-instructions.md`
  - 自律ループ実行モードが Claude Code 固有であり Copilot は対象外であることを明記。
  - 品質ゲートのコマンド定義を `CLAUDE.md` に集約する旨を追記。
- `.gitignore` を新規追加。`local.properties` や鍵ファイル等を除外し、自動コミット時の
  資格情報混入を防止。

## 2026-08-18

- `CLAUDE.md` に「リポジトリの現状とアーキテクチャ概要」「よく使うコマンド」セクションを追加。
  アプリのソースコードが未実装であること、`PLAN.md` が将来実装予定プロダクトの仕様メモであること、
  ディレクトリ間の参照関係、Markdownlintの実行コマンドを明記した（`/init` コマンドによる更新）。

## 2026-08-21（自律ループ実行モード、BL-031）

- `docs/records/managed/DESIGN.md`「実装制約」セクションへ、`mobile`/`wear`が別々の
  `applicationId`を持つ現行構成が、Google Play推奨の単一AAB配布方式（wear feature module化）
  と異なる制約であることを記録。影響範囲（掲載ページの分離、自動プッシュインストール不可、
  リリース管理が2アプリ分必要）と、今回のタスクスコープでは統合構成への移行を行わない方針、
  将来統合する場合の概要を明記した。コード変更を伴わないため`EXECUTE.md`は更新していない。

## 2026-08-21（BL-021）

- `docs/records/managed/BACKLOG.md` 冒頭（`COPILOT_RECORDS:BEGIN`より前、マーカー外）に
  `<!-- markdownlint-disable-file MD041 -->` を追加。`DESIGN.md`/`EXECUTE.md`には
  既に付与済みだったコメントが本ファイルのみinit commit時点から欠落しており、
  `npx markdownlint-cli2 "**/*.md"` 実行時にエラーとなっていた（BL-021）。
  マーカー外への追記はCLAUDE.md「records自動更新規約」の原則に抵触しうるため、
  BL-021としてユーザーへ対応可否を確認したうえで、明示的な許可を得て追記した。
  あわせてBACKLOG.mdの完了項目としてBL-021のレコード自体をマーカー内から削除した。

## 2026-08-22（CLAUDE.md コンテキスト最適化）

Claude Code セッション開始時に自動読み込みされる文字数が、`CLAUDE.md`本体と `@import` 3ファイル
（`rules/guardrails-unified.v1.md` / `docs/guidelines/RULE.md` / `CONTRIBUTING.md`）の合計で約55,800文字
に達し、内容の大半（初回/追加実装のMUSTリスト、例外管理、gitコミット権限の規定など）が複数ファイルへ
重複していたため、ユーザー依頼により整理した。`rules/guardrails-unified.v1.md`はセキュリティ・
プライバシー担当のレビュー必須文書のため対象外とし、変更していない。

- `CLAUDE.md`
  - `@import` を `rules/guardrails-unified.v1.md` のみへ縮小。`docs/guidelines/RULE.md` と
    `CONTRIBUTING.md` は内容の大半が本ファイルへ具体化済みで二重読み込みだったため、通常の
    Markdownリンクによる参照（必要時に読む方式）へ変更した（ファイル自体は無変更、他リポジトリへ
    配布するテンプレートとしての役割は維持）。
  - 「指示参照の優先順位」の記載を、上記import変更に合わせて更新。
  - 「セキュリティ要件」「例外と保守 > 例外管理」の各セクションから、`rules/guardrails-unified.v1.md`
    （上記import済み）と一字一句同内容だった箇所を削除し、本リポジトリ固有の補足のみを残す形へ圧縮。
  - 「ディレクトリと参照関係」の `CLAUDE.md` 項目説明を、import構成の変更に合わせて更新。
  - 上記の結果、自動読み込み合計は約55,800文字から約39,500文字（約29%減）になった。
  コード変更を伴わないため`EXECUTE.md`は更新していない。

## 2026-09-05（ドキュメント最新化・最適化）

実機（Pixel 8 Pro + Pixel Watch 2）へローカルビルドを配置した検証結果と、2026-08-22の`CLAUDE.md`
import構成変更が未反映だった箇所を、リポジトリ内のドキュメントへ反映した。各ドキュメントの目的・
役割は変更していない。

- 実態との差異解消
  - `CONTRIBUTING.md` / `CLAUDE.md`: `.github/workflows/` ディレクトリ自体が存在せずCIが一切
    動作していない事実へ記述を統一した（`markdown-quality.yml.disabled` と `lychee-action` への
    言及を削除）。`CLAUDE.md`側にあった「未確認の差異」の注記も解消した。
  - `docs/guidelines/RULE.md` / `docs/guidelines/ADOPTION.md` / `docs/guidelines/README.md`:
    `CLAUDE.md` が `@import` するのは `rules/guardrails-unified.v1.md` のみである現行構成へ更新した
    （2026-08-22の変更が配布用ガイド側へ同期されていなかった）。
- 検証実績の反映
  - `docs/INSTALL.md`: Wi-Fi経由デバッグのペア設定手順（`adb pair`、ペア設定用ポートと接続用ポートが
    別番号であること）と、`ANDROID_SERIAL`によるインストール先の指定手順を追加した。2台同時接続時は
    bundletoolが必要であるかのような記述を、実際に確認できた手順へ置き換えた。
  - `README.md` / `docs/store/STORE_LISTING.md` / `docs/records/managed/DESIGN.md`: 実機疎通確認
    （BL-010）・実機Tile操作（BL-011）・512x512アイコンの準備が完了済みであること、Complication表示の
    不具合（BL-072）が未解決であること、未検証なのはGoogle Play経由の自動プッシュインストールのみで
    あることを反映した。あわせてDESIGN.mdのBL-064の記述を「調査中」から確認済みの実装メモへ更新した。
  - `docs/records/managed/BACKLOG.md`: BL-038のタスク内容へ、ローカルインストールは確認済みである旨を
    注記した（区分・完了条件は変更なし）。
- 記述の整理
  - `PLAN.md`: 冒頭へ、本ファイルが初回依頼時点のメモであり最新は`DESIGN.md`である旨の注記を追加した。
  - `docs/store/PRIVACY_POLICY.md`: 問い合わせ先メールアドレスの記法をMD034準拠へ修正した。
  - `.markdownlint-cli2.yaml`: `.gitignore`対象の`*.local.md`を検査対象から除外した。
  - `docs/records/README.md`: 冒頭の余分な空行を削除した。
  コード変更を伴わないため`EXECUTE.md`は更新していない。

## 2026-09-05（人手検証タスクの棚卸し）

`BACKLOG.md`の`区分: 人手検証`タスクについて、ユーザーへ検証状況をヒアリングし、結果を反映した。

- BL-042（アプリアイコンのコンプリケーション風リング表示）: 実機（スマホ・Pixel Watch）での
  再確認が完了し、リングが欠けずに表示されることを確認したため`BACKLOG.md`から削除した。
  リング追加のコード変更自体は実施済み（`EXECUTE.md`の2026-08-30の記録を参照）。
- BL-033 / BL-034 / BL-038（Google Play公開まわり）: ユーザー判断により当面保留とし、現状のまま
  据え置いた（記録の変更なし）。
- BL-055（複数Complicationのデバイス別表示）: 前提となるBL-072（Complicationの状態文言が空欄に
  なる不具合）が未解決のため、引き続き検証待ち。BL-072の対応は自律ループ実行モードで実施する。
  `BACKLOG.md`のみの変更のため`EXECUTE.md`は更新していない。

同日、自律ループ実行モードでBL-072を修正した後、実機（Pixel Watch 2）での確認結果を受けて
以下を追加で処理した。

- BL-072（Complicationの状態文言が空欄）: 修正版のインストール後、文字盤へ状態文言が表示される
  ことを確認したため`BACKLOG.md`から削除した。
- BL-073（Complicationのデバイス設定・変更導線）: BL-072の解消により、デバイス割り当ての導線が
  「未設定枠のタップ」1経路しかなく枠ごとに別デバイスを設定できない問題が顕在化したため新規登録し、
  Wear OS標準の設定導線（`PROVIDER_CONFIG_ACTION`）への対応と設定済み枠への`tapAction`付与で
  対応した。実機で複数枠への別デバイス割り当てと設定済み枠の変更ができることを確認したため
  `BACKLOG.md`から削除した（コード変更の記録は`EXECUTE.md`参照）。
- BL-055（複数Tile/Complicationのデバイス別対応）: 上記BL-073の完了により、完了条件を
  「単一Tileのデバイス切り替え」に限定し、依存を解消した。

同日、限定した完了条件についてユーザーへ検証結果をヒアリングし、以下を処理した。

- BL-055（単一Tileのデバイス切り替え）: 実機（Pixel Watch + Sesame 5実機2台以上）で、
  デバイス切り替え後のデバイス名・状態表示、施錠/解錠が対象デバイスのみに作用すること、
  成否を判別できるハプティクス、「全デバイス」選択時の一括操作と🔀混在表示のいずれも
  問題ないことを確認したため`BACKLOG.md`から削除し、`DESIGN.md`の該当記述を確認済みへ更新した。
  コード変更を伴わないため`EXECUTE.md`は更新していない。

## 2026-09-05（GitHub Public公開対応、BL-074〜BL-086）

GitHubリポジトリをPublicへ切り替えるにあたり、公開リポジトリとして不足していたドキュメント・
テンプレートを整備し、既存ドキュメントを実態へ合わせて最新化した。あわせてストア公開後の
利用者向け導線（アップデート内容の確認先・問い合わせ先）をリポジトリ内で管理する構成へ整理した。
git履歴の秘密情報スキャンを実施し、混入がダミー値とRFC 4493公開テストベクタのみであることを
確認済み。

- `SECURITY.md` を新規作成（BL-074）。脆弱性報告の対象範囲（本リポジトリのコードのみ。
  CANDY HOUSE社のSesame本体・クラウドAPIは対象外）、非公開の報告経路（GitHub Security Advisories /
  メール）、報告に資格情報を含めない依頼、対応の目安、サポート対象バージョン（最新リリースのみ）を
  定義した。`README.md`の関連ドキュメントからも参照できるようにした。
- `CONTRIBUTING.md` をPublicリポジトリの実態へ合わせた（BL-075）。冒頭へ「外部コントリビューターの
  方へ」を新設し、受け入れの流れ（Issue先行）・判断基準（資格情報の取り扱い方針と非送信方針の維持）・
  注意事項（MIT Licenseでの公開への同意、資格情報を含めない）・PR作成前に実行する品質ゲートを
  明記した。「変更手順」をルールファイル前提からアプリコード・ドキュメント双方に対応する記述へ改め、
  記録ファイルの使い分け（EXECUTE.md / CHANGELOG.md / RELEASE_NOTES.md）を追記した。
  「レビュー要件」のテンプレート由来だった役割分担（セキュリティ担当・プライバシー担当・
  プロダクトオーナー）を、メンテナー1名が兼務する現在の体制の記述へ修正した。
- Issue・Pull Requestのテンプレートを整備した（BL-076）。`.github/ISSUE_TEMPLATE/` へ
  `bug_report.yml`（不具合報告）・`feature_request.yml`（機能要望）・`question.yml`（使い方の質問）・
  `config.yml`（空Issueの無効化と、脆弱性報告・サポート情報・リリースノートへの導線）を新規作成した。
  不具合報告・質問のテンプレートには、資格情報を記載しないことを確認するチェック項目を必須で設けた。
  `.github/PULL_REQUEST_TEMPLATE.md`（従来はCopilot向けコメント2行のみ）へ、`CLAUDE.md`が規定する
  PR説明構成（概要・変更内容・変更理由・テスト方法・関連事項）と、品質ゲート実行・資格情報混入確認を
  含むチェックリストを追加した。
- アプリ利用者向けの [docs/USER_GUIDE.md](docs/USER_GUIDE.md) を新規作成した（BL-077）。
  これまで利用手順は`README.md`（開発者向け）と`docs/INSTALL.md`（インストール手順）へ断片的に
  記載されているのみで、実際の操作手順をまとめたドキュメントが存在しなかった。資格情報の取得と登録、
  タイルの追加とデバイス割り当て、施錠/解錠操作、コンプリケーションの設定、状態表示が更新される
  タイミング（定期ポーリングを行わない仕様）、表示アイコンの意味、トラブル時の確認事項、
  資格情報の削除方法を、実装中の表示文言に合わせて記載した。
- アプリ利用者向けの [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) を新規作成した（BL-079）。
  バージョンごとの新機能・改善・修正・既知の問題を新着順で記録する様式と運用ルール（記載の粒度、
  `scripts/version.properties`との整合、Google Play「このリリースの新機能」欄への転記）を定義し、
  現行の0.1.0（未リリース）の内容を記載した。あわせて本ファイル冒頭へ、`CHANGELOG.md` /
  `RELEASE_NOTES.md` / `EXECUTE.md` の記録先の使い分けを明記した。
- アプリ利用者向けの [docs/SUPPORT.md](docs/SUPPORT.md) を新規作成した（BL-078）。アップデート内容の
  確認先（リリースノートを一次情報とし、GitHub Releases・Google Playの「新機能」を併記）、
  配布経路ごとのアップデート方法、現在の配布状況、問い合わせ窓口（GitHub Issuesのテンプレート別・
  メール・脆弱性は非公開経路）、報告時に資格情報を含めない依頼、対応方針、サポートの対象範囲
  （Sesame本体・純正アプリ・OS自体は対象外）、免責を記載した。
- `README.md` をPublic公開向けに再構成した（BL-080）。冒頭へアプリ概要と主な機能、
  「アプリを使う方へ」の導線表（利用ガイド・インストール・リリースノート・サポート・
  プライバシーポリシー・脆弱性報告）を配置し、以降を開発者向け情報と明示した。
  「既知の未確認事項・制約」からComplicationの状態文言が表示されない旨の記述（BL-072として解消済み）
  を削除し、実態に合わせてTileの多重追加制約とComplicationの枠別割り当て確認済みの記述へ置き換えた。
  プロジェクト構成ツリーへ`docs/`配下と`scripts/`・`config/`・`rules/`を反映し、関連ドキュメントを
  利用者向け・開発者向けに分けて最新化した。
- `docs/store/` をストア掲載情報の管理場所として整理した（BL-081）。索引となる
  [docs/store/README.md](docs/store/README.md) を新規作成し、Play Consoleの各入力項目と参照元ファイル・
  文字数上限の対応表、更新手順（原本をリポジトリ側に置きPlay Console側を直接書き換えない運用）を
  定義した。`PRIVACY_POLICY.md` はPublic公開後のGitHub上のURLをそのまま公開URLとして使う方針を明記し、
  「ドラフト」表記を解消して問い合わせ導線を追加した。`STORE_LISTING.md` の詳細な説明へ、利用ガイド・
  リリースノート・サポート情報のURLを追加した。
- `docs/INSTALL.md` の公開状況に関する記述を統一した（BL-082）。一般ユーザー向けセクションに残っていた
  「現状（2026-08-22時点）」を、冒頭の「現状（2026-09-05時点）」への参照へ置き換えた。あわせて、
  インストール後の使い方は `docs/USER_GUIDE.md`、配布状況とアップデート方法は `docs/SUPPORT.md` を
  参照する導線を追加した。
- リリースビルドから切り分け用のデバッグログ（`Log.d` / `Log.v`）を除去するR8ルールを
  `mobile/proguard-rules.pro` へ追加した（BL-083。コード修正のため詳細は
  [docs/records/managed/EXECUTE.md](docs/records/managed/EXECUTE.md) を参照）。
- エージェント向けルールの参照関係を、今回追加したドキュメントを含む実ファイル構成へ更新した
  （BL-084）。`CLAUDE.md`「ディレクトリと参照関係」へ`SECURITY.md`・`docs/USER_GUIDE.md`・
  `docs/SUPPORT.md`・`docs/RELEASE_NOTES.md`・`.github/ISSUE_TEMPLATE/`・
  `.github/PULL_REQUEST_TEMPLATE.md`を追加し、`docs/store/`の説明を索引付きの現構成へ改めた。
  `CLAUDE.md` / `.github/copilot-instructions.md` の「記録対象」へ`CHANGELOG.md`と
  `docs/RELEASE_NOTES.md`の使い分けを追記し、`CONTRIBUTING.md`「ドキュメント管理」へ利用者向け
  ドキュメントの一覧を追加した。

## 2026-09-05（外部コントリビューション方針の変更、BL-087〜BL-089）

本アプリの実装はClaude CodeによるAI実装を主体として開発者本人が行う体制のため、コードの変更経路を
開発者側へ一本化し、外部からのPull Requestを受け付けない方針へ変更した。Issue（不具合報告・要望・
質問）は引き続き受け付け、対応は開発者が行う。上記のPublic公開対応（BL-074〜BL-086）で整備した
外部コントリビューター向けの記述を、この方針へ合わせて改めた。

- `CONTRIBUTING.md` の「外部コントリビューターの方へ」を「受け付けている報告と、受け付けていないもの」
  へ全面的に書き換えた（BL-087）。受け付ける報告（不具合・要望・質問・ドキュメントの誤り・脆弱性）と
  受け付けないもの（Pull Request）を一覧化し、受け付けない理由（記録ファイルを含む一貫した手順の維持、
  資格情報を扱うアプリでの変更経路の限定、レビュー体制の制約）を明記した。Issue作成時のお願いと、
  要望を実装するかどうかの判断基準（従来の受け入れ判断基準を流用）を残した。あわせて、
  「外部コントリビューターの方へ」配下にあった品質ゲートを開発者向けの独立セクションへ移し、
  「レビュー要件」へAI実装主体である旨と、レビュー対象が開発者・AIエージェントの変更に限られる旨を
  追記した。
- Issue・Pull Requestのテンプレートを新方針へ合わせた（BL-088）。
  `.github/ISSUE_TEMPLATE/feature_request.yml` から「実装への参加意思」（Pull Request作成を選択肢に
  含む項目）を削除し、実装は開発者が行い外部PRを受け付けていない旨の案内へ置き換えた。
  `.github/ISSUE_TEMPLATE/config.yml` へ受け付け方針（CONTRIBUTING.md）への導線を追加した。
  `.github/PULL_REQUEST_TEMPLATE.md` の冒頭コメントへ、本テンプレートが開発者・AIエージェント専用で
  あることを明記した。
- 新方針を関連ドキュメントへ反映した（BL-089）。`docs/SUPPORT.md`「対応方針」へ、修正・改善は
  すべて開発者側で実装し外部PRを受け付けない旨と、判断基準の参照先（章名変更に追随）を反映した。
  `README.md`の`CONTRIBUTING.md`の説明を受け付け方針が分かる文言へ改め、`CLAUDE.md`の
  `.github/PULL_REQUEST_TEMPLATE.md`の説明へ開発者・AIエージェント専用である旨を追記した。
  `docs/records/managed/DESIGN.md`「GitHub公開対応」の記述を新方針へ更新した。あわせて`BACKLOG.md`の
  BL-085（Public切り替え）へ、fork抑止設定の可否確認（個人アカウントのPublicリポジトリで
  提供されているかは未確認）と、提供されない場合の運用を追記した。

## 2026-09-06（プライバシーポリシーの記述を実装と一致させた）

Google Play Consoleのデータセーフティ申告の準備にあたり実装を確認したところ、
`docs/store/PRIVACY_POLICY.md`「情報の送信先」の記述が実装と一致していないことが判明したため修正した。
実装上、secretKeyは端末内でのAES-CMAC署名の計算にのみ使用され、Sesame APIへ送信されるのは
その計算結果である署名値（`sign`）である（`core` の `SesameApiClient.sendCommand()` および
`SesameCommandSigner.sign()`）。

- `docs/store/PRIVACY_POLICY.md`
  - 「情報の送信先」を、Sesame APIへ実際に送信する項目（uuid・apikey・コマンド種別・操作履歴タグ・
    コマンド署名）の一覧へ改め、secretKeyは端末外へ送信しない旨を明記した。
  - 「保存方法」末尾の「外部のサーバーへ送信・バックアップすることはありません」を、Sesame APIへの
    送信と矛盾して読めないよう、開発者が管理するサーバーが存在しない旨の記述へ改めた。
  - 最終更新日を2026-09-06へ更新した。

なお本修正はドキュメントのみの変更であり、アプリの動作に変更はないため
`docs/RELEASE_NOTES.md` および `docs/records/managed/EXECUTE.md` は更新していない。

## 2026-09-06（Wear OS配布方式の変更に伴うドキュメント更新、BL-090〜BL-094）

Google Play Consoleへの初回アップロードで、Wear OS向けリリースは専用トラックでの公開が必須という
エラーが発生した。Googleは単一App BundleへWear OSアプリをdynamic featureとして同梱する構成を
サポートしていないため、`wear`を独立したapplicationモジュールへ変更し、`applicationId`を共通に
したまま2つのAABを生成する構成へ改めた（実装の詳細は
[docs/records/managed/EXECUTE.md](docs/records/managed/EXECUTE.md) を参照）。
これに伴い、単一AAB構成を前提としていた記述を更新した。

- `README.md`
  - 「アーキテクチャ概要」を、同一`applicationId`を共有する独立2モジュール構成の記述へ改めた。
  - 「リリースビルド・Google Play公開」に、スマホ用とウォッチ用の2つのAABをビルドするコマンドと、
    それぞれのPlay Consoleでのアップロード先を対応表として追加した。
  - 「プロジェクト構成」の`wear`の説明からdynamic featureの記述を除いた。
  - 「既知の未確認事項・制約」へ、旧構成が非サポートだった経緯と、旧構成では
    `uses-feature android.hardware.type.watch`のマージによりスマートフォンが配信対象から
    除外される状態だったことを追記した。
- `CLAUDE.md`
  - 「モジュール構成」へ、`mobile`/`wear`が同一`applicationId`を共有する独立した2つの
    applicationモジュールである旨と、Play Consoleでのトラック分離を追記した。
  - ビルド・インストール手順を、デバイス種別ごとにタスクを使い分ける形
    （スマホは`:mobile:installDebug`、ウォッチは`:wear:installDebug`）へ変更した。
    従来の「`:wear:`配下のモジュール単体タスクは失敗する」という記述は成立しなくなったため削除した。
- `docs/INSTALL.md`
  - 冒頭の構成説明を2成果物構成へ改め、同一`applicationId`のため1台のデバイスに両方は
    インストールできない旨の注意を追加した。
  - ローカルビルドのインストール手順を、インストール先に応じたタスクの対応表とともに更新した。
  - Google Play経由のインストール手順を、1つの掲載ページ内で2トラックへ配信する構成の説明へ改めた。
- `docs/records/managed/DESIGN.md`
  - 「Google Play配布方式」を全面的に書き換え、Googleの要件・旧構成が失敗した理由・
    versionCodeの系列分離・署名鍵の共通化・guava依存の整理・ランチャーアイコンの扱いを記載した。
  - 「対象システム概要」「Androidアイコンリソース」「ProGuard/R8」「モジュール構成・パッケージ方針」
    「実装制約」を新構成へ合わせて更新した。
- `docs/store/STORE_LISTING.md`
  - Google Playには1つのアプリとして登録するが、成果物は2つに分かれ、スクリーンショットも
    スマートフォン用とWear OS用の枠へそれぞれ投入する旨を追記した。

`.github/copilot-instructions.md` はモジュール構成・ビルド手順を記載していないため、今回の同期対象外。
`docs/RELEASE_NOTES.md` / `docs/SUPPORT.md` / `docs/USER_GUIDE.md` は利用者から見た動作・手順に
変更がないため更新していない。

## 2026-09-06（ストア素材のリポジトリ追加、BL-095）

Google Play Consoleへ提出したストア素材を、掲載情報の原本と同じ場所で管理するためリポジトリへ
追加した。素材はPlay Consoleの入力欄へ直接アップロードするため、リポジトリ側を原本として
更新履歴を追えるようにする。

- `docs/store/images/play_feature_graphic_1024x500.png` を追加。機能グラフィックは必須項目で
  未作成だったため、アプリアイコンと同じ配色（`#1E3A5F` / `#C99A46`）で作成した。
- `docs/store/images/screenshots/` を追加（スマートフォン用2枚、Wear OS用3枚）。実機
  （Pixel 8 Pro / Pixel Watch 2）で撮影したもの。スマートフォン用は実解像度1344x2992が
  Play Consoleの縦横比の上限を超えるため、内容を切らずに左右へ背景色の帯を足して16:9へ整えた。
  登録済みデバイスの表示名は公開を避けるため画像上でモザイク処理している。
- `docs/store/README.md` のファイル一覧へ機能グラフィックとスクリーンショットを追加し、
  各ファイルの内容・サイズ・加工内容を表で示した。「Play Consoleの入力項目との対応」の
  「スクリーンショット: 未作成」を、スマートフォン用とWear OS用で枠が分かれる旨とともに更新した。
- `docs/store/STORE_LISTING.md` の「未確認事項」を「文字数の実測値」へ置き換えた。文字数は
  すべて上限内であることを実測で確認済み（アプリ名11/30、短い説明50/80、詳細な説明1052/4000、
  新機能160/500）。スクリーンショットの所在も追記した。

作業中に生成したPlay Console貼り付け用のプレーンテキスト（`play_full_description.txt`）は、
`STORE_LISTING.md` からの派生物で原本と乖離するリスクがあるためリポジトリへは追加していない。

## 2026-09-06（Public化完了の反映と人手検証タスクの棚卸し、BL-096）

GitHubリポジトリのPublic化（BL-085）が完了したため、`docs/records/managed/BACKLOG.md` へ反映した。
あわせて、Google Play Consoleでの作業が進んだことで記述が実態と合わなくなった人手検証タスクを
更新し、完了済みタスクを指したままの依存を整理した。

- BL-085（Public化）を完了として削除した。Public化・Description/Topics・Issues有効化・
  Ruleset（`protect-main`、承認数0）・Code security（Secret scanning / Push protection /
  Dependabot / 非公開脆弱性報告）・Actionsのフォークワークフロー承認設定・Security policyの認識は
  すべて完了。fork抑止は個人アカウントのPublicリポジトリではGitHubが設定を提供しておらず
  不可能であることを確認し、`CONTRIBUTING.md`・PRテンプレートでの明示と、届いたPRをクローズする
  運用で対応する（記述は整備済みのため追加の変更は不要だった）。
- BL-085の残件だった初回リリースタグ v0.9.0 とGitHub Releasesの作成をBL-098として分離した。
  配信した成果物とタグを対応させるため、BL-097の完了を待って実施する。
- BL-086（プライバシーポリシーの公開URL確定）へ、Public化により
  `https://github.com/filderschoice/sesami-wear/blob/main/docs/store/PRIVACY_POLICY.md` が
  HTTP 200で到達可能であること、GitHub Pagesは不要と判断したこと、Play Consoleへ登録済みで
  あることを追記した。
- BL-033（データ安全性の申告）へ、実装確認の結果に基づく申告方針を記録した。apikeyを
  「個人情報 > ユーザーID」、uuidを「デバイスIDまたはその他のID」として収集・共有の両方で申告し、
  secretKeyはGoogleの「収集」の定義（端末外への送信）に該当しないため申告対象外とする。
- BL-034（Play Consoleへの登録）へ、アプリ新規作成・ストア掲載情報・アプリのコンテンツが
  完了済みであることを記録し、残るトラックへのアップロードとテスター登録はBL-097で扱う旨を
  明記した。
- 完了済みで既に削除されたタスク（BL-029 / BL-030 / BL-081 / BL-085）を指していた依存を整理した。

# コントリビューションガイド（リポジトリ固有）

本リポジトリは、Pixel WatchからCANDY HOUSE Sesameを操作する非公式アプリ「Sesami Wear」の
開発リポジトリです。本ファイルは `CONTRIBUTING.md`（配布元から取り込む共通規約）に対する
リポジトリ固有の内容で、外部の方からの報告の受け付け方針（前半）と、開発者向けの固有規約（後半）の
2部構成です。共通規約との分担は `CONTRIBUTING.md` 冒頭を参照してください。

- アプリの使い方・不具合の相談は [docs/SUPPORT.md](docs/SUPPORT.md) を参照してください。
- 脆弱性の報告は公開Issueではなく [SECURITY.md](SECURITY.md) の手順に従ってください。

## 受け付けている報告と、受け付けていないもの

本アプリの実装は、Claude Code によるAI実装を主体として開発者本人が行う体制で進めています。
コードの変更経路を開発者側へ一本化しているため、**外部からのPull Requestは受け付けていません**。
一方で、Issueでのご報告・ご要望は歓迎します。いただいた内容は開発者が確認し、対応する場合は
開発者側で実装します。

| 種類 | 受け付け | 窓口 |
| --- | --- | --- |
| 不具合の報告 | 受け付けます | [Issue](https://github.com/filderschoice/sesami-wear/issues/new/choose) |
| 機能の要望・改善提案 | 受け付けます | [Issue](https://github.com/filderschoice/sesami-wear/issues/new/choose) |
| 使い方の質問 | 受け付けます | [Issue](https://github.com/filderschoice/sesami-wear/issues/new/choose) |
| 誤字・ドキュメントの誤りの指摘 | 受け付けます | [Issue](https://github.com/filderschoice/sesami-wear/issues/new/choose) |
| 脆弱性の報告 | 受け付けます（非公開経路） | [SECURITY.md](SECURITY.md) |
| Pull Request | 受け付けていません | — |

### Pull Requestを受け付けていない理由

- 実装・テスト・記録ファイル（`docs/records/managed/`）の更新を、AIエージェントを含む一貫した手順で
  行っており、外部からの変更を取り込むとこの手順と記録の整合を保てないためです。
- 資格情報（apikey / secretKey / uuid）を扱うアプリのため、コードの変更経路を開発者に限定し、
  変更の監査を単純に保っています。
- 個人が余暇で開発しており、外部のPull Requestをレビューする体制を維持できないためです。

Pull Requestをいただいても、内容を確認せずにクローズする場合があります。あらかじめご了承ください。
ご提案は Issue へお願いします。

### Issueを作成するときのお願い

- 目的に合ったテンプレート（不具合の報告 / 機能の要望・改善提案 / 使い方の質問）を選んでください。
- **実際の資格情報（apikey / secretKey / uuid）を含めないでください。** ログやスクリーンショットに
  写り込んでいないかもご確認ください。
- 既に同じ内容のIssueがないかご確認ください。
- 個人が余暇に開発しているため、返信までに数日〜数週間かかる場合があります。
- ご要望に対応するかどうか、対応する時期は開発者が判断します。対応しないままクローズする場合も
  あります。

### 要望・提案を実装するかどうかの判断基準

いただいたご要望は、次の観点で判断します（開発者自身の実装にも同じ基準を適用します）。

- 本アプリの目的（Wear OSからSesameを操作する）に沿っていること
- 資格情報の取り扱い方針を変えないこと。secretKeyはウォッチ側へ保持させず、施錠/解錠は
  スマートフォン側で実行する設計を前提とします
  （[docs/records/managed/DESIGN.md](docs/records/managed/DESIGN.md)「アーキテクチャ方針」参照）
- 広告SDK・アクセス解析SDK・外部への送信を伴う機能を追加しないこと
- 既存の公開仕様・UXを壊す場合は、移行方針を示せること
- 個人で保守を継続できる範囲に収まること

---

以降は、開発者（メンテナーおよびAIエージェント）向けの固有規約です。

## 規定ブランチ

- 規定ブランチ: `main`

## レビュー責任者

本リポジトリの現在の体制はメンテナー1名（[@filderschoice](https://github.com/filderschoice)、
`.github/CODEOWNERS` で全パスを担当）です。`CONTRIBUTING.md`「レビュー要件」の各担当は、
現時点ではすべてメンテナーが兼務します。体制を拡大した場合は本節と `.github/CODEOWNERS` を更新します。

| 担当 | 担当者・チーム |
| --- | --- |
| セキュリティ担当 | `@filderschoice` |
| プライバシー担当 | `@filderschoice` |
| プロダクトオーナー | `@filderschoice` |

- すべての変更は、規定ブランチへマージする前にメンテナーのレビューを必須とします。
- `rules/` 配下の変更は、セキュリティ観点・プライバシー観点の双方をレビューで確認します
  （担当を分離できる体制になった場合は、それぞれ1名以上を必須とします）。
- 互換性に影響する変更は、移行方針の提示とメンテナーの承認を必須とします。
- 本リポジトリの実装はClaude CodeによるAI実装を主体としています。AIエージェント（Claude Code /
  GitHub Copilot）が作成した変更にも、人手による変更と同じレビュー要件を適用します。
- 外部からのPull Requestは受け付けていないため（[前掲](#受け付けている報告と受け付けていないもの)）、
  レビュー対象は開発者およびAIエージェントによる変更に限られます。

## 自律ループ実行モードの採否

- 採否: 採用（`CLAUDE.md`「自律ループ実行モード（Loop Engineering）」、Skill `autonomous-loop`）

## 例外の追跡

- 追跡場所: `docs/records/managed/BACKLOG.md` へ理由・期限・責任者つきで登録する。

## ドキュメント構成（追加分）

`CONTRIBUTING.md`「ドキュメント構成」の表に無い、本リポジトリ固有のディレクトリ・ファイルです。
全体の参照先マップは `CLAUDE.md`「参照先マップ」を参照してください。

| パス | 役割 | 詳細 |
| --- | --- | --- |
| `docs/`（直下） | アプリ利用者向けドキュメント。UIの表示文言・配布状況・仕様を変更した場合に追随させる | `USER_GUIDE.md` / `INSTALL.md` / `SUPPORT.md` / `CLOSED_TEST.md` / `RELEASE_NOTES.md`（利用者に影響する変更を行った場合に追記） |
| `docs/store/` | Google Play提出用の掲載情報の原本 | `docs/store/README.md` |
| `.claude/skills/` | Claude Code のオンデマンド手順（常時読み込みしない） | 配布元と共有: `autonomous-loop` / `docs-consistency-review` / `pr-create`。本リポジトリ固有（配布対象外）: `guardrail-sync-local` ほか |

## その他の開発プロセス

### 変更手順（記録先の割当）

`CONTRIBUTING.md`「変更手順」の「記録ファイルを更新する」工程では、変更の種類ごとに次へ記録します。

- コード修正を伴う変更: `docs/records/managed/EXECUTE.md`（実施記録）と、設計方針が変わる場合は
  `docs/records/managed/DESIGN.md`
- リポジトリのルール・ドキュメントのみの変更: `CHANGELOG.md`
- 利用者向けの機能変更・不具合修正: `docs/RELEASE_NOTES.md`

### 品質ゲート

本リポジトリにはCIがなく（`.github/workflows/` を置いていません）、品質ゲートはローカル実行が唯一の
ゲートです。コミット・プルリクエスト作成の前に、`CLAUDE.md`「本リポジトリの品質ゲート定義」の
コマンドを実行し、すべて成功することを確認します。コマンドと合否基準の正本は同節で、ここへは複製しません。
ビルド環境の準備は [README.md](README.md)「前提環境」を参照してください。

- `python scripts/validate-records.py` はPyYAMLが必要です（`pip install pyyaml`）。検査内容は
  スクリプトのdocstring、体裁の正本は [docs/records/spec/FORMAT.md](docs/records/spec/FORMAT.md)
  「YAMLとしての体裁」です。
- ktlintの指摘は `./gradlew ktlintFormat` で自動修正できます。
- Markdownlintの設定は `.markdownlint-cli2.yaml` です。`docs/` 配下の `*.local.md`（Git管理外の
  手順書・作業メモ）は検査対象外です。
- リンク切れチェックは定義していません。確認する場合は [lychee](https://github.com/lycheeverse/lychee) を
  別途インストールし、`lychee "**/*.md"` を実行してください。

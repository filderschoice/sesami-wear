---
description: "Use when generating pull request descriptions, PR summaries, or reviewing code in a pull request. Applies to PR description generation, code review comments, and review summaries."
---

# PR説明文・コードレビューの規約

**本ファイルは Claude Code / GitHub Copilot 双方へ適用する、PR説明文とコードレビューの正本です。**
規範はここへ一元化し、エージェント別の入口ファイルへは複製しません
（`CONTRIBUTING.md`「エージェント指示ファイルの構成規約」）。

| エージェント | 入口 | 読み込まれ方 |
| --- | --- | --- |
| GitHub Copilot | 本ファイル / [`.github/prompts/pr-create.prompt.md`](../prompts/pr-create.prompt.md) | 本ファイルは `.vscode/settings.json` の `pullRequestDescriptionGeneration.instructions` と `reviewSelection.instructions` から参照され、PR説明文の生成時・レビュー時に自動適用される。プロンプトファイルは Copilot Chat で `/pr-create` として明示的に呼び出す |
| Claude Code | [`.claude/skills/pr-create/SKILL.md`](../../.claude/skills/pr-create/SKILL.md) | 自動適用の機構が無いため、スキルの手順に従って生成前に本ファイルを読む |

## 言語要件（MUST）

- PRの説明文（Description）は**日本語**で生成する
- コードレビューのコメント・指摘事項は**日本語**で記述する
- レビューサマリーは**日本語**で記述する
- セクション見出し・ラベルも日本語を使用する

## 本文の型（MUST）

**本文の型の正本は [`.github/PULL_REQUEST_TEMPLATE.md`](../PULL_REQUEST_TEMPLATE.md) です。**
6節構成（概要／変更内容／変更理由・背景／テスト方法／関連事項／チェックリスト）で、
**チェックリスト節はテンプレート側にしかありません**。構成が似ているからと本ファイルの節名だけで
本文を作ると、チェックリストが丸ごと抜けます。書き始める前に必ずテンプレート本体を開いてください。

```markdown
## 概要
（変更内容の簡潔な説明）

## 変更内容
（箇条書きで主な変更点を列挙）

## 変更理由・背景
（なぜこの変更が必要か）

## テスト方法
（動作確認手順）

## 関連事項
（関連するIssue番号、参考リンクなど）

## チェックリスト
（テンプレート本体の文言をそのまま使う）
```

各節の書き方:

- **変更内容**: `docs/records/managed/BACKLOG.md` に対象タスクがあれば `id`（例: `BL-172`）を列挙する。
- **テスト方法**: 実行した品質ゲートのコマンドと結果（成功/失敗）を書く。実機検証を行った場合は
  機種とOSバージョンも書く。実行していない検証を書かない。
- **関連事項**: 関連する `id`・Issue番号・PR番号。

PRのタイトル:

- 日本語で、Conventional Commits の type を先頭に付ける（`feat:` `fix:` `docs:` `chore:` など）。
- **絵文字は付けない。** 絵文字を含めるのはコミットメッセージのタイトル行だけです
  （共通規約「コミット要件」）。
- 複数タスクをまとめた場合は対象の `id` を併記する（例: `docs: 実機検証結果の記録 (BL-172 / BL-165)`）。

base ブランチは規定ブランチ（既定 `main`）です。本リポジトリは外部からのPull Requestを
受け付けていません（`CONTRIBUTING.md`）。

## 着手前に差分を読む（MUST）

規定ブランチとの差分を確認してから本文を書きます。読まずに要約すると、実際の変更とズレた本文になります。

```bash
git branch --show-current
git log main..HEAD --oneline
git diff main...HEAD --stat
git diff main...HEAD
```

## チェックリストの扱い（MUST）

- **実行して確認できた項目だけをチェックする。** 実行していないゲートを成功扱いにすると、
  レビュアーへ誤った保証を与えます。
- 変更内容的に対象外の項目は**チェックせず**、行末へ理由を追記する
  （例: `（コード修正を伴わないため対象外）`）。
- 品質ゲートの実行コマンドと合否基準の正本は `CLAUDE.md`「本リポジトリの品質ゲート定義」です。

| 変更の種類 | 実行するゲート |
| --- | --- |
| `core` / `mobile` / `wear` 配下のコード変更を含む | `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug` |
| Markdownを変更した | `npx markdownlint-cli2 "**/*.md"` |
| `docs/records/managed/` 配下を変更した | `python scripts/validate-records.py` |
| 利用者に影響する変更 | `docs/RELEASE_NOTES.md` を更新したかを確認 |
| コード修正を伴う | `docs/records/managed/EXECUTE.md` を更新したかを確認 |
| いずれにも該当しない項目 | チェックせず「〜のため対象外」と添える |

資格情報（apikey / secretKey / uuid）・個人情報の混入確認は、変更の種類によらず毎回行います。

## PR作成・更新の実行（MUST）

- PRの作成・更新は**GitHub上へ公開される、後戻りしにくい操作**です。ユーザーから明示的に
  指示された場合にのみ実行します（`rules/guardrails-unified.v1.md` 3.2）。
- **作業ブランチの `git push` はユーザーが実行します**（共通規約「ブランチ・コミット管理」）。
  push されていないブランチにはPRを作成できないため、未 push の場合はその旨を伝えて待ちます。
- 自律ループ実行モードではPRの作成・更新・マージを行いません
  （`rules/guardrails-unified.v1.md` 12.2。Claude Code 固有のモードで Copilot は対象外）。
- 実行手順はエージェントごとに異なります。Claude Code は `.claude/skills/pr-create/SKILL.md`、
  Copilot は `.github/prompts/pr-create.prompt.md` を参照してください。

## コードレビューの観点（SHOULD）

レビュー時は以下の観点で指摘する：

- **バグ・ロジック**: 誤った処理、エッジケースの見落とし
- **セキュリティ**: OWASP Top 10に基づく脆弱性の有無
- **パフォーマンス**: 非効率な処理、N+1問題など
- **可読性**: 命名の適切さ、コードの明確さ
- **テスト**: テストカバレッジの妥当性

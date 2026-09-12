# ガイドライン（docs/guidelines）

複数プロジェクトで共有・再利用できるガイドラインを格納しています。

| ファイル | 説明 |
| --- | --- |
| `RULE.md` | Copilot / Claude Code 運用ルール（汎用版）。基本原則・出力要件・完了条件 |
| `ADOPTION.md` | 新規・既存リポジトリ向けの導入手順と日常運用ガイド。導入資産一覧の正本 |

推奨参照順は `RULE.md` → `ADOPTION.md` → プロジェクト側のエージェント指示ファイルです。

## エージェント指示ファイルからの参照方法

エージェント指示は「共通規約1ファイル + エージェント固有差分」で構成します。共通規約の正本は
`.github/copilot-instructions.md` で、Copilot は自動読み込み、Claude Code は `CLAUDE.md` の
`@import` 経由で読み込みます。役割分担は `CONTRIBUTING.md`「エージェント指示ファイルの構成規約」が正本です。

Claude Code の `CLAUDE.md` は `@` から始まる import 記法でセッション開始時に自動読み込みできます。

```markdown
@rules/guardrails-unified.v1.md
@.github/copilot-instructions.md
```

自動読み込みはセッションのコンテキストを消費し続けるため、対象は最小限に絞ります。
方針と現在の構成は `ADOPTION.md`「1.1 `CLAUDE.md` の `@import` について」を参照してください。

## プロジェクト固有の指示を追加する場合

プロジェクトのエージェント指示ファイルで本ガイドラインを参照した上で、固有の制約を追加します。
以下は記述例（汎用例であり、本リポジトリの構成ではありません）です。

```markdown
## プロジェクト固有制約

- 対応OS: Windows 11
- 言語・フレームワーク: Rust + Tauri + Vue.js
- UI フレームワーク: Vuetify
- 制約: 外部APIの呼び出しは単一のクライアント実装へ集約する
```

書く内容は「そのプロジェクトで作業するエージェントが、最初に知らないと誤った実装をしてしまうこと」に
絞ります。実装済み機能の一覧や未対応事項の件数など、時点によって変わる情報は書かず、参照先
（`README.md`・`docs/records/managed/*.md`）を示してください。指示ファイルはセッション開始時に
読み込まれるため、時点情報を書くと更新漏れでそのまま陳腐化します。

## 更新管理

ガイドラインを更新した場合は、以下を実施してください。

1. `docs/records/managed/DESIGN.md` の再実装用プロンプト設計書を最新化
2. `CHANGELOG.md` でリリースノートを更新
3. 共通規約に関わる変更は `.github/copilot-instructions.md` へ、Claude Code 固有の変更は `CLAUDE.md` へ反映
   （役割分担は `CONTRIBUTING.md`「エージェント指示ファイルの構成規約」が正本）

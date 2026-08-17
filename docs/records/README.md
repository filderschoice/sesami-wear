
# docs/records/ 運用・更新ルール

このディレクトリは、Copilot・Claude Code などのAIエージェントが管理する記録ファイル群を役割ごとに分離して管理します。

- `spec/`: 記述仕様（人間向け参照）
- `managed/`: Copilot・Claude Code などのAIエージェント管理対象の記録本体（手動編集禁止）

`managed/` 配下の `BACKLOG.md`、`DESIGN.md`、`EXECUTE.md` は、Copilot・Claude Code などのAIエージェントがプロンプト指示の処理実行時に追記・更新・削除する専用ファイルです。ユーザは直接編集しないでください。

## 各ファイルの役割と自動管理の流れ

- **managed/BACKLOG.md**: 未対応事項・課題・次ステップを1項目＝1YAMLブロックで管理。Copilot・Claude Code などのAIエージェントが追加・状態変更・削除を行います。
  コード修正を伴って完了した項目は `managed/EXECUTE.md` へ実施内容を記録します。
- **managed/DESIGN.md**: 実装済み機能の要件・設計・制約を統合した「再実装用プロンプト設計書（最新版）」として管理します。
  同一要件をCopilot・Claude Code などのAIエージェントへ再実装依頼できるよう、差分確認結果を反映して文書全体を更新します。
- **managed/EXECUTE.md**: コード修正を伴う変更の実施記録を1件ごとにYAMLブロックで管理。Copilot・Claude Code などのAIエージェントが追記・更新します。

## 記述ルール・記述例

- 記述ルールと記述例は [spec/FORMAT.md](spec/FORMAT.md) に集約しています。
- managed/BACKLOG.md / managed/EXECUTE.md はYAMLレコード形式、managed/DESIGN.md はMarkdown設計書形式で保持します。
- Copilot・Claude Code などのAIエージェントは各ファイルの `COPILOT_RECORDS:BEGIN` と `COPILOT_RECORDS:END` の間を更新します。

## 編集禁止の理由

- 機械可読性・一貫性・自動管理のため、Copilot・Claude Code などのAIエージェント以外の手動編集は想定していません。
- 誤編集や競合を防ぐため、内容の追加・削除・修正はCopilot・Claude Code などのAIエージェントのプロンプト指示経由でのみ行ってください。

## 参照先

- 詳細な運用ルールやCopilot・Claude Code などのAIエージェントの管理仕様は以下も参照してください。
  - .github/copilot-instructions.md（Copilot向けリポジトリ運用ガイド・ドキュメント管理要件）
  - CLAUDE.md（Claude Code向けリポジトリ運用ガイド・ドキュメント管理要件）
  - docs/guidelines/RULE.md（Copilot・Claude Code などのAIエージェント運用ルール・汎用フレームワーク）
  - rules/guardrails-unified.v1.md（セキュリティ・プライバシー・ガバナンス統制）
  - docs/records/spec/FORMAT.md（records配下の記述ルール・記述例）

---

ご不明点や運用ルールの変更要望は、管理者またはCopilot・Claude Code などのAIエージェント運用担当までご連絡ください。

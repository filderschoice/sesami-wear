# records 記述ルール・記述例

このファイルは、
[../managed/BACKLOG.md](../managed/BACKLOG.md)、
[../managed/DESIGN.md](../managed/DESIGN.md)、
[../managed/EXECUTE.md](../managed/EXECUTE.md) の記述ルールと記述例をまとめた参照用ドキュメントです。
実データは各ファイルへCopilotが自動追記します。ユーザはこのファイルを参照し、各記録ファイルを直接編集しないでください。

## 共通ルール

- 記録ファイル本体はCopilot専用で、ユーザ手動編集は禁止
- `managed/BACKLOG.md` と `managed/EXECUTE.md` はYAML形式で1件ずつ追記
- `managed/DESIGN.md` はMarkdown形式の単一設計書を最新版として上書き更新
- 追記位置は各ファイルの `COPILOT_RECORDS:BEGIN` と `COPILOT_RECORDS:END` の間
- BACKLOG/EXECUTE は新着順（先頭追加）を維持し、キー名はこのファイルの定義を厳守

## Copilot更新手順（実行規約）

1. 更新前にこの `docs/records/spec/FORMAT.md` を参照し、対象ファイルの必須キーと型を確認する
2. 対象ファイル
  （`docs/records/managed/BACKLOG.md` / `docs/records/managed/DESIGN.md` /
  `docs/records/managed/EXECUTE.md`）の
  `COPILOT_RECORDS:BEGIN` と `COPILOT_RECORDS:END` の間だけを編集する
3. BACKLOG/EXECUTE は新規レコードを先頭に追加し、既存レコードはキー構造を維持したまま更新する
4. DESIGN は文書全体の整合性を優先して更新し、同一要件の再実装に使える実装用プロンプトとして常に最新版を保持する
5. 完了・不要レコードの削除は、各ファイルの管理ルールに従って行う
6. 定義外キーの追加や、マーカー外の本文変更は行わない

## BACKLOG.md

### BACKLOG.md 記述ルール

必須キー:

- `id`
- `区分`
- `タスク内容`
- `優先度` (`P1` / `P2` / `P3`)
- `状態` (`未着手` / `進行中` / `ブロック` / `完了`)
- `担当`
- `完了条件`

### BACKLOG.md 記述例

```yaml
- id: BL-001
  区分: 品質ゲート
  タスク内容: Rust lint/test/format を実行する
  優先度: P1
  状態: ブロック
  担当: Backend
  完了条件: cargo fmt, clippy, test 成功
```

## DESIGN.md

### DESIGN.md 記述ルール

- 形式はMarkdown
- 目的は「同一要件をCopilotに再実装させるための実装用プロンプト設計書」
- 文書は履歴を積み上げず、最新版を維持する
- 実装済み機能の要件・設計意図・制約・品質観点を統合して記載する
- 変更時は既存コードとの差分確認結果を反映し、要件の抜け漏れを更新する

### DESIGN.md 推奨テンプレート

```markdown
# 実装用プロンプト設計書

## 目的
- この文書の利用目的

## 対象システム概要
- 対象
- 前提環境

## 実装済み機能要件
- 要件IDと要件内容

## 設計方針
- アーキテクチャ方針
- データ設計方針
- UI/UX方針（該当時）

## 非機能要件
- 性能
- 信頼性
- セキュリティ/プライバシー

## 実装制約
- 技術制約
- 運用制約

## Copilot実装指示
- 初回実装時の出力要件
- 追加実装時の出力要件
- 要件トレーサビリティ要件
```

## EXECUTE.md

### EXECUTE.md 記述ルール

必須キー:

- `date` (`YYYY-MM-DD HH:mm`)
- `summary`
- `details.変更内容`
- `details.変更ファイル` (配列)
- `details.検証コマンド`
- `details.検証結果`

### EXECUTE.md 記述例

```yaml
- date: 2026-05-12 15:45
  summary: guardrails-unified.v1.md セクション11削除と参照整合性修正
  details:
    変更内容: セクション11を削除し、関連参照を整合化
    変更ファイル:
      - rules/guardrails-unified.v1.md
      - .github/copilot-instructions.md
      - docs/guidelines/RULE.md
    検証コマンド: git diff
    検証結果: 成功 - 参照整合性を確認
```

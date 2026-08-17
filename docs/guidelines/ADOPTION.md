# Copilot / Claude Code ルール導入・運用ガイド

このガイドは、本リポジトリで管理している Copilot・Claude Code 用のプロセスルールとガードレールを、
新規リポジトリと既存リポジトリへ適用して運用するための実務手順を示します。

## 1. まず最初に読むポイント

- この文書は「導入判断 -> 導入実施 -> 日常運用」の順で読む。
- すぐ着手する場合は「2. クイックスタート（5ステップ）」から開始する。
- 既存リポジトリへの移行は「4. 既存リポジトリへの適用手順」を先に読む。

## 2. クイックスタート（5ステップ）

1. 規定ブランチから作業ブランチを作成する。
2. 必須ファイルを配置する（`rules/`、`.github/`、`templates/`、`docs/`、`CONTRIBUTING.md`、`CHANGELOG.md`）。
3. `.github/copilot-instructions.md`（Copilot）または `CLAUDE.md`（Claude Code）の参照先ファイルを配置し、参照パスを現行構成に合わせる。
4. テンプレートのプレースホルダを埋める。
5. 初期リスク登録と受け入れ確認（リンク整合性、AIエージェント応答、PR 運用）を実施する。

## 3. 適用対象と適用方式

### 3.1 適用対象

- 対象ルール: `rules/guardrails-unified.v1.md`
- 対象プロンプト指示: `.github/copilot-instructions.md`（Copilot）、`CLAUDE.md`（Claude Code、利用する場合）
- 対象テンプレート: `templates/app-guardrail-template.yaml`、`templates/model-risk-register-template.csv`
- 補助運用文書: `CONTRIBUTING.md`、`docs/guidelines/RULE.md`、`docs/records/README.md`、`docs/records/spec/FORMAT.md`、`docs/records/managed/*.md`

### 3.2 適用方式

- 推奨方式: 配布先リポジトリに「正本コピー」を配置し、差分を PR で管理する。
- バージョン管理: ルール本文はファイル名バージョン（例: `guardrails-unified.v1.md`）で管理する。
- 更新同期: 配布元（本リポジトリ）の `CHANGELOG.md` を参照して追従判断する。

### 3.3 `.github/copilot-instructions.md` / `CLAUDE.md` 参照依存ファイル

適用時は、`.github/copilot-instructions.md`（Copilot）および `CLAUDE.md`（Claude Code）が参照する以下ファイルを同時に配置・整合確認する。

- `rules/guardrails-unified.v1.md`
- `docs/guidelines/RULE.md`
- `CONTRIBUTING.md`
- `docs/records/managed/DESIGN.md`
- `docs/records/managed/EXECUTE.md`
- `docs/records/managed/BACKLOG.md`
- `docs/records/README.md`
- `docs/records/spec/FORMAT.md`
- `CHANGELOG.md`

`CLAUDE.md` は `rules/guardrails-unified.v1.md` / `docs/guidelines/RULE.md` / `CONTRIBUTING.md` を
`@import` 記法でセッション開始時に自動読み込みするため、リポジトリルートからの相対パスが崩れていないことを確認する。

## 4. 既存リポジトリへの適用手順

既存運用を壊さないため、必ず「追加適用」から始める。
作業は規定ブランチから切った作業ブランチで実施する。

### 4.1 ギャップ分析

1. 現行の AI 利用ルール、セキュリティルール、PR 運用を棚卸しする。
2. 以下 3 観点で差分を分類する。
   - 追加: 現行に存在しない統制
   - 競合: 既存ルールと矛盾する統制
   - 置換: 本ガイドへ統合したい重複ルール
3. 競合項目は「優先ルール」「移行期限」「責任者」を決める。

### 4.2 段階導入

1. Phase 1（参照導入）
   - `rules/guardrails-unified.v1.md` と `docs/guidelines/RULE.md` を導入し、既存ルールから参照する。
2. Phase 2（運用導入）
    - `.github/copilot-instructions.md`（Copilot）と、必要に応じて `CLAUDE.md`（Claude Code）を導入し、
       `CONTRIBUTING.md`、`docs/records/spec/FORMAT.md`、`docs/records/managed/*.md`、`CHANGELOG.md` を含む
       参照依存ファイルの存在と参照整合性を確認する。
3. Phase 3（統合完了）
   - 重複ルールを整理し、正本を 1 か所に寄せる。

### 4.3 互換性管理

- 既存のレビュー手順を壊さないよう、まず「追加適用」で開始する。
- 破壊的変更がある場合は、旧ルール併用期間を設定する。
- CI や監査ログ要件と衝突する場合は、`docs/records/managed/DESIGN.md` の再実装用プロンプト設計書へ判断理由を反映する。

## 5. 新規リポジトリへの適用手順

新規導入は、以下を上から順に実施する。
作業は規定ブランチから切った作業ブランチで実施する。

### 5.1 初期配置

1. 配布先リポジトリに以下を作成または配置する。
   - `rules/guardrails-unified.v1.md`
   - `.github/copilot-instructions.md`（Copilot を利用する場合）
   - `CLAUDE.md`（Claude Code を利用する場合、リポジトリルート直下に配置）
   - `templates/app-guardrail-template.yaml`
   - `templates/model-risk-register-template.csv`
2. `docs/guidelines/` と `docs/records/` を作成し、最低限以下を配置する。
   - `docs/guidelines/RULE.md`
   - `docs/guidelines/ADOPTION.md`
   - `docs/records/README.md`
   - `docs/records/spec/FORMAT.md`
   - `docs/records/managed/DESIGN.md`
   - `docs/records/managed/EXECUTE.md`
   - `docs/records/managed/BACKLOG.md`
3. ルートファイルとして `CONTRIBUTING.md` と `CHANGELOG.md` を配置する。
4. `.github/CODEOWNERS` と `.github/PULL_REQUEST_TEMPLATE.md` を組織体制に合わせて調整する。

### 5.2 初期設定

1. `.github/copilot-instructions.md`（Copilot）と `CLAUDE.md`（Claude Code）の参照パスが実フォルダ構成と一致し、参照依存ファイルがすべて存在することを確認する。
2. `docs/records/README.md` と `docs/records/spec/FORMAT.md` の記述に基づき、records ファイルをAIエージェント自動更新前提で運用する。
3. `templates/app-guardrail-template.yaml` の `replace-me` をすべて埋める。
4. `templates/model-risk-register-template.csv` に初期リスクを最低 2 件以上登録する。
5. `CONTRIBUTING.md` に規定ブランチとレビュー責任者を明記する。

### 5.3 受け入れ確認

1. ルール参照整合性を確認する（リンク切れ・パス誤りがないこと）。
2. 代表的な Copilot・Claude Code タスクで、禁止事項と出力要件が期待通りに働くことを確認する。
3. PR ベース運用（規定ブランチ直コミット禁止、レビュー必須）が有効であることを確認する。

## 6. 日常運用ガイド

### 6.1 変更管理

1. ルール変更は必ず作業ブランチで実施する。
2. 変更時は `CHANGELOG.md` を更新する。
3. 影響判断は Copilot・Claude Code 指示経由で `docs/records/managed/DESIGN.md` の設計書へ反映する。
4. コード修正を伴う場合のみ、Copilot・Claude Code 指示経由で `docs/records/managed/EXECUTE.md` を更新する。
5. 未対応事項は Copilot・Claude Code 指示経由で `docs/records/managed/BACKLOG.md` で管理する。
6. records の記述仕様変更は `docs/records/spec/FORMAT.md` を正本として更新し、`docs/records/managed/*.md` 本体には定義外の構造を追加しない。

### 6.2 records 自動更新運用

- `docs/records/managed/DESIGN.md` / `docs/records/managed/EXECUTE.md` / `docs/records/managed/BACKLOG.md` は
  Copilot・Claude Code など対応AIエージェントによる自動更新専用として扱う。
- 各ファイルは `COPILOT_RECORDS:BEGIN` と `COPILOT_RECORDS:END` の間のみ更新する（マーカー名は導入時の命名を継続利用しており、全エージェント共通です）。
- records の記述ルール・記述例は `docs/records/spec/FORMAT.md` を唯一の参照元とする。
- ユーザやレビューアは records 本体を直接編集せず、必要な修正はAIエージェントへの指示で反映する。

### 6.3 レビュー観点

- セキュリティ: 禁止事項、インジェクション対策、機密情報保護
- プライバシー: データ分類、保持期間、削除要求対応
- 開発プロセス: ブランチ運用、PR テンプレート、責任者承認
- 実効性: AIエージェント出力でルールが再現可能か

### 6.4 監査観点

- どのバージョンのガードレールを適用しているか明示されていること
- 例外に理由・責任者・期限があること
- 変更の履歴が `CHANGELOG.md` と `docs/records/` に残ること

## 7. ロールバック方針

- 問題が発生した場合は、直前の安定版ルールセットへ戻す。
- ロールバック時は、戻した理由と影響を `docs/records/managed/DESIGN.md` の設計書へ反映する。
- 暫定運用（例外）を行う場合は、期限付きで `docs/records/managed/BACKLOG.md` に登録する。

## 8. 配布先プロジェクト向けチェックリスト

- `rules/guardrails-unified.v1.md` を導入済み
- `docs/guidelines/RULE.md` を導入済み
- `.github/copilot-instructions.md`（Copilot）・`CLAUDE.md`（Claude Code、利用する場合）の参照パスが有効
- `CONTRIBUTING.md` を導入済み
- `docs/records/README.md` と `docs/records/spec/FORMAT.md` を導入済み
- `docs/records/managed/DESIGN.md` `docs/records/managed/EXECUTE.md` `docs/records/managed/BACKLOG.md` を導入済み
- `CHANGELOG.md` を導入済み
- `templates/app-guardrail-template.yaml` の `replace-me` を解消済み
- `templates/model-risk-register-template.csv` に初期リスク登録済み
- `CONTRIBUTING.md` に規定ブランチ、レビュー責任者、例外運用を明記済み
- `docs/records/managed/*.md` に `COPILOT_RECORDS:BEGIN` / `COPILOT_RECORDS:END` が存在
- `CHANGELOG.md` と `docs/records/` の更新ルールをチームで合意済み

## 9. 既知の制約

- プロンプト指示だけでは、アプリ実行時の強制ブロックは保証できない。
- 本番での最終強制は、実装コード、ポリシーエンジン、運用監視で補完する必要がある。

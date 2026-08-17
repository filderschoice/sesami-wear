# ガイドライン（docs/guidelines）

このフォルダには、プロジェクト全体で共有・再利用できるガイドラインが格納されています。

## ファイル一覧

| ファイル | 説明 |
|---------|------|
| `RULE.md` | Copilot / Claude Code 運用ルール（汎用版）。複数プロジェクトで再利用可能な指示ガイド |
| `ADOPTION.md` | 新規・既存リポジトリ向けの導入手順と日常運用ガイド |

## 使い方

### Copilot指示で参照する場合

`.github/copilot-instructions.md` で以下のように参照します：

```markdown
統合ルール `docs/guidelines/RULE.md` で、品質・ドキュメント管理の汎用ルールを確認してください。
```

### Claude Code指示で参照する場合

`CLAUDE.md` では `@docs/guidelines/RULE.md` の import 記法でセッション開始時に自動読み込みします：

```markdown
@docs/guidelines/RULE.md
```

### プロジェクト固有の指示を追加する場合

プロジェクトのプロンプト（`.github/copilot-instructions.md` または `CLAUDE.md`）で、このガイドラインを参照した上で、プロジェクト特有の制約を追加してください：

```markdown
## プロジェクト固有制約

- 対応OS: Windows 11
- 言語: Rust + Tauri + Vue.js
- UI フレームワーク: Vuetify
```

## 更新管理

ガイドラインを更新した場合は、以下を実施してください：

1. `docs/records/managed/DESIGN.md` の再実装用プロンプト設計書を最新化
2. `CHANGELOG.md` でリリースノートを更新
3. `.github/copilot-instructions.md` と `CLAUDE.md` の参照情報があれば同期

## 関連ドキュメント

- `docs/records/spec/FORMAT.md`: records 記述仕様
- `docs/records/managed/DESIGN.md`: 実装済み内容を統合した再実装用プロンプト設計書（最新版）
- `docs/records/managed/EXECUTE.md`: 実施済み内容（コード修正を伴う変更）
- `docs/records/managed/BACKLOG.md`: 未対応事項、課題、次ステップ

## 推奨参照順

1. `RULE.md` で汎用ルールを確認
2. `ADOPTION.md` で新規導入/既存移行の手順を確認
3. プロジェクト側 `.github/copilot-instructions.md`（Copilot）または `CLAUDE.md`（Claude Code）に固有制約を反映

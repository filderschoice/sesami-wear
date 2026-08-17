---
description: "Use when generating pull request descriptions, PR summaries, or reviewing code in a pull request. Applies to PR description generation, code review comments, and review summaries."
---

# PR Description・コードレビューの言語設定

## 言語要件（MUST）

- PRの説明文（Description）は**日本語**で生成する
- コードレビューのコメント・指摘事項は**日本語**で記述する
- レビューサマリーは**日本語**で記述する
- セクション見出し・ラベルも日本語を使用する

## PR Description の構成（SHOULD）

PRの説明文を生成する際は、以下の構成に従う：

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
```

## コードレビューの観点（SHOULD）

レビュー時は以下の観点で指摘する：

- **バグ・ロジック**: 誤った処理、エッジケースの見落とし
- **セキュリティ**: OWASP Top 10に基づく脆弱性の有無
- **パフォーマンス**: 非効率な処理、N+1問題など
- **可読性**: 命名の適切さ、コードの明確さ
- **テスト**: テストカバレッジの妥当性

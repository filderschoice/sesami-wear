<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-105
  区分: 運用設定
  タスク内容: リリースタグを保護するRulesetをGitHubへ追加する。対象は `v*` タグで、削除と
    更新（強制上書き）を禁止する。BL-098の任意項目として挙げていた検討事項を、v0.9.0タグの
    作成に伴い独立タスクとして切り出したもの。配信済みの成果物とタグの対応が事後に壊れることを
    防ぐのが目的で、mainブランチのprotect-main Rulesetと同じ考え方をタグへ適用する
  優先度: P3
  状態: 要確認
  担当: ユーザー
  完了条件: v* タグに対する削除・更新がRulesetで禁止され、既存のv0.9.0タグが保護対象になっている
  根拠: Claudeはgh api経由でRulesetを作成できるが、リポジトリ設定の変更は作業ツリー外への
    影響を伴うため、自律ループ実行モードでは実行せずユーザーの承認を待つ
    （rules/guardrails-unified.v1.md セクション12.4）
  依存: []


```
<!-- COPILOT_RECORDS:END -->

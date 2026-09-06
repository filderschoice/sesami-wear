<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-098
  区分: 人手検証
  タスク内容: 初回リリースタグ v0.9.0 を作成し、GitHub Releasesを公開する。本文には
    docs/RELEASE_NOTES.md の 0.9.0 の内容を転記する。BL-085から分離した残件で、配信した成果物と
    タグを対応させるため、BL-097でPlay Consoleの内部テストへのアップロードが成功したことを
    確認してから実施する。あわせて、v* タグを対象としたRulesetで削除・更新を制限することを
    検討する（任意）
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: v0.9.0タグがpushされ、GitHub Releasesから参照できる
  根拠: Claudeによる代替実施は不可。自律ループ実行モードではgit pushが禁止されており
    （rules/guardrails-unified.v1.md セクション12.2）、タグをリモートへ反映できないため
    GitHub Releasesも作成できない。ユーザーによる実施が必須
  依存: []


```
<!-- COPILOT_RECORDS:END -->

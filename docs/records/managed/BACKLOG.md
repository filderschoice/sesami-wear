<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-111
  区分: ドキュメント
  タスク内容: docs/CLOSED_TEST.md のプレースホルダを実際のURLへ差し替える。BL-106でGoogleグループ
    とクローズドテストトラックを作成すると、グループ参加URL（現在は
    https://groups.google.com/g/sesami-wear-testers を仮記載）とオプトインURL（現在は未記載）が
    確定する。あわせて冒頭の「このページは準備中です」の注記を削除する。BL-108の周知文を
    公開する前に完了させる必要がある
  優先度: P2
  状態: 要確認
  担当: Claude
  完了条件: docs/CLOSED_TEST.md に実際のグループ参加URLとオプトインURLが記載され、準備中の注記が
    削除されている
  根拠: URLはユーザーがPlay ConsoleとGoogleグループで作成しないと確定せず、自律ループ実行モードでは
    取得できない（rules/guardrails-unified.v1.md セクション12.2）。ユーザーから2つのURLの提供を
    受けた時点で実施する
  依存: [BL-106]

- id: BL-106
  区分: 人手検証
  タスク内容: Google Playの製品版公開に必要なクローズドテスト（12人以上が14日間連続でオプトイン）を
    実施し、製品版へのアクセスを申請する。テスター管理はメールアドレスのリストではなく
    Googleグループ方式を採用し、参加者が自分でグループへ参加してオプトインする自己完結フローに
    する（ユーザーがメールアドレスを収集・保管しない方針）。手順の詳細と現在地は
    docs/store/PLAY_CONSOLE_STEPS.local.md の「8. 本番公開（クローズドテスト要件）」を参照する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: クローズドテストで12人以上が14日間連続でオプトインした状態を満たし、製品版への
    アクセス申請が承認される
  根拠: Claudeによる代替実施は不可。Play ConsoleとGoogleグループはブラウザ専用の操作であり、
    テスターの募集も外部への発信にあたるため自律ループ実行モードでは実行できない
    （rules/guardrails-unified.v1.md セクション12.2）
  依存: []


```
<!-- COPILOT_RECORDS:END -->

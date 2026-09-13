<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-117
  区分: 人手検証
  タスク内容: docs/store/STORE_LISTING.md の「詳細な説明」へ追記した3行（複数台の切り替え、
    「全デバイス」での一括操作、Sesame未登録時のデモ）を、Google Play Consoleのストア掲載情報へ
    転記する。実測文字数は同ファイル「文字数の実測値」のとおり1186文字（上限4000文字）。
    転記時はMarkdownの見出し・箇条書き記法を除き、段落途中の改行を詰める
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: Play Consoleのストア掲載情報の「詳細な説明」が STORE_LISTING.md の内容と一致する
  根拠: Play Consoleはブラウザ専用の操作でエージェントは代替実施できない
    （rules/guardrails-unified.v1.md セクション12.2）。実施はクローズドテストの審査
    （BL-106）が通過してから行う。審査中に掲載情報を変更すると再送信が必要になるため
  依存: []

- id: BL-116
  区分: 人手検証
  タスク内容: リリースタグ v0.10.0 を作成し、GitHub Releasesを公開する。本文には
    docs/RELEASE_NOTES.md の 0.10.0 の内容を転記する。v0.9.0（BL-098）と同様、配信した成果物と
    タグを対応させるため、Play Consoleのクローズドテスト両トラック（スマートフォン用 versionCode 5 /
    Wear OS用 versionCode 1003）が審査を通過し「公開中」になったことを確認してから実施する。
    タグは v0.9.0 と同じくRuleset protect-release-tags の保護対象になる
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: v0.10.0タグがpushされ、GitHub Releasesから参照できる
  根拠: タグのpushはユーザーが実行する（rules/guardrails-unified.v1.md セクション12.2でエージェントの
    git pushを禁止）。タグ作成とReleases本文の生成はスキル github-release-publish の手順に従い
    エージェントが実施できるため、区分は人手検証としつつ実作業は分担する
  依存: []

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

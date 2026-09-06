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

- id: BL-086
  区分: 人手検証
  タスク内容: GitHub上の docs/store/PRIVACY_POLICY.md をPublic公開後のURLとしてホスティングし、
    Play ConsoleのData safety申告へ登録する。2026-09-06にリポジトリのPublic化が完了し、
    次のURLがHTTP 200で到達可能であることを確認済み。GitHub Pagesは不要と判断した。
    https://github.com/filderschoice/sesami-wear/blob/main/docs/store/PRIVACY_POLICY.md
    Play Consoleのアプリのコンテンツ（App content）へも同URLを登録済み。残るのはBL-033の
    データ安全性の申告完了確認のみで、実作業はBL-033と同一のため、BL-033完了時に本タスクも
    完了とみなす
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: プライバシーポリシーが恒久URLで参照でき、Play Consoleへ登録されている
  根拠: Claudeによる代替実施は不可。Play Consoleはブラウザ専用の操作で、Play Developer APIの
    認証情報も保持していないため申告画面を操作できない。公開URLの到達性確認（HTTP 200）まで
    は代替実施済み
  依存: []

- id: BL-033
  区分: 人手検証
  タスク内容: プライバシーポリシーを公開可能なURLでホスティングし、Google Play ConsoleのData
    safety（データ安全性）セクションへ申告する。公開URLの用意とPlay Consoleへの登録は
    2026-09-06に完了済み（BL-086参照）。データ安全性の申告は、実装を確認した結果
    （SesameApiClient.sendCommandが送信するのはuuid・apikey・コマンド種別・操作履歴タグ・
    コマンド署名であり、secretKeyは端末内での署名計算にのみ使われ送信されない）に基づき、
    apikeyを「個人情報 > ユーザーID」、uuidを「デバイスIDまたはその他のID」として、収集・共有の
    両方・目的はアプリの機能のみで申告する方針を確定済み。secretKeyはGoogleの「収集」の定義
    （端末外への送信）に該当しないため申告対象外
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: プライバシーポリシーURLがPlay Consoleに登録され、データ安全性の申告が完了する
  根拠: Claudeによる代替実施は不可（Play Consoleはブラウザ専用）。申告内容の根拠となる
    実装調査（送信項目の特定、secretKeyが端末外へ出ないことの確認）は代替実施済みで、
    回答方針も本タスクへ記録済み
  依存: []


```
<!-- COPILOT_RECORDS:END -->

<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-021
  区分: ドキュメント
  タスク内容: 本ファイル（BACKLOG.md）冒頭にmarkdownlint-disable-file MD041コメントが欠落しており、
    npx markdownlint-cli2 "**/*.md" 実行時にエラーとなる（DESIGN.md/EXECUTE.mdには同コメントが
    付与済みで、BACKLOG.mdのみinit commit時点から欠落している模様、BL-018のREADME.md作成時に発見）。
    修正にはCOPILOT_RECORDS:BEGINより前（マーカー外）への追記が必要で、CLAUDE.md「records自動更新規約」
    の「マーカー外の本文変更は行わない」に抵触する可能性があるため、対応可否をユーザーへ確認する
  優先度: P3
  状態: 要確認
  担当: ユーザー
  完了条件: マーカー外への追記可否をユーザーが判断する（可の場合はユーザー自身が追記するか、
    Claude Codeへの追記許可を明示する。不可の場合は既知の制約として本タスクをクローズする）
  依存: []

- id: BL-022
  区分: 実装
  タスク内容: SesameActionScreen（SesameActionActivity.kt）の解錠確認ボタンのラベルが「ホールドで解錠」
    となっているが、実装はワンタップで反応するButtonであり、実際の操作方法（タップ）と文言（ホールド）が
    食い違っている（BL-019 UI/UXレビューで発見）。ユーザーが長押しを試みて誤操作するリスクがあるため、
    ラベルを実装（ワンタップ確認）に即した文言へ修正する
  優先度: P1
  状態: 未着手
  担当: Wear
  完了条件: ボタンラベルが実際の操作方法（タップで確定）と一致する文言に修正され、ビルドが成功する
  依存:
    - BL-014

- id: BL-023
  区分: 実装
  タスク内容: CredentialsSettingsScreenのsecretKey入力フィールドが平文表示されており、
    肩越しの盗み見リスクがある（BL-019 UI/UXレビューで発見）。VisualTransformation
    （PasswordVisualTransformation等）でマスキング表示にする
  優先度: P2
  状態: 未着手
  担当: Mobile
  完了条件: secretKey入力フィールドがマスキング表示され、ビルドが成功する
  依存:
    - BL-005

- id: BL-024
  区分: 実装
  タスク内容: CredentialsSettingsScreenで保存ボタン押下後の成功フィードバック（Toast等）がなく、
    ユーザーが保存されたか判断できない。また uuid/apikey/secretKeyBase64 が空文字列のままでも
    保存できてしまい入力バリデーションがない（BL-019 UI/UXレビューで発見）。保存成功フィードバックと
    必須入力バリデーション（いずれかが空の場合は保存ボタンを無効化する等）を追加する
  優先度: P3
  状態: 未着手
  担当: Mobile
  完了条件: 保存成功時にフィードバックが表示され、いずれかのフィールドが空の場合に保存を防止する
    ロジックの単体テストが成功する
  依存:
    - BL-005

- id: BL-025
  区分: 実装
  タスク内容: Wear OS Tileのテキスト要素（SesameTileService.buildStatusBox内のText.Builder）に
    contentDescriptionが設定されておらず、スクリーンリーダー等のアクセシビリティ機能で状態が
    正しく読み上げられない可能性がある（BL-019 UI/UXレビューで発見）。Tileのテキスト要素へ
    contentDescriptionを設定する
  優先度: P3
  状態: 未着手
  担当: Wear
  完了条件: Tileのレイアウト要素にcontentDescriptionが設定され、ビルドが成功する
  依存:
    - BL-007

- id: BL-010
  区分: 人手検証
  タスク内容: 実資格情報（uuid/secretKey/apikey）を用いてSesame APIの状態取得・施錠・解錠を疎通確認する。
    SesameStatus（batteryVoltage/position/CHSesame2Status。isInLockRange/isInUnlockRangeは
    CHSesame2Statusからの導出値）のフィールド構成が実際のレスポンスと一致するか、
    施錠/解錠コマンドの署名・ペイロード仕様（DESIGN.md記載、pysesame3参照で判明した推測）が
    実際に通るかもあわせて確認する（未確認事項、DESIGN.md参照）
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実際のSesame 5 + Hub 3に対してGET状態取得・POST施錠/解錠が成功し、
    SesameStatusのデコードエラーが発生しないことを確認する
  依存:
    - BL-003
    - BL-004

- id: BL-011
  区分: 人手検証
  タスク内容: Pixel Watch実機 + Sesame 5実機でTileの施錠/解錠操作・ハプティクス・Complication表示を確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実機で一連の操作が成功し、PLAN.mdのUX要件を満たすことを確認する
  依存:
    - BL-007
    - BL-008
    - BL-009
    - BL-010
    - BL-013
    - BL-014
    - BL-015
    - BL-016
    - BL-022
```
<!-- COPILOT_RECORDS:END -->

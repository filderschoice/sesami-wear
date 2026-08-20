<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-026
  区分: 実装
  タスク内容: コードレビューで発見。CredentialsInputValidatorはuuid/apikey/secretKeyBase64の非空
    チェックのみを行っており、secretKeyBase64が有効なBase64文字列か、デコード後に16バイト
    （AES-128鍵長）かを検証していない。ユーザーが誤った文字列を保存した場合、
    SesameMessageListenerService.createHandler内のcredentials.secretKeyBytes（Base64デコード）や
    AesCmac.compute内のrequire(key.size==16)で例外が発生し、onMessageReceived内の
    CoroutineScope(Dispatchers.IO).launchで未処理コルーチン例外としてアプリがクラッシュするリスクが
    ある。CredentialsInputValidatorへBase64形式・デコード後16バイトの検証を追加し、設定画面で
    不正な鍵は保存できないようにする。あわせてSesameCommandHandler側でもBase64デコード・
    AesCmac.compute呼び出しを例外安全にし、不正な鍵の場合はFAILUREを返すようフォールバックする
  優先度: P1
  状態: 未着手
  担当: 共通
  完了条件: 不正なBase64文字列・不正な長さの鍵に対してCredentialsInputValidator.isValidがfalseを
    返す単体テストが成功し、SesameCommandHandler.handle（またはその呼び出し経路）が不正な鍵に対して
    例外を送出せずFAILUREを返すことを確認する単体テストが成功する
  依存:
    - BL-005
    - BL-024

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

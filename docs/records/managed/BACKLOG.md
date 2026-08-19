<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-015
  区分: 実装
  タスク内容: SesameTileServiceのonTileRequestが常に固定値（isPhoneConnected=false,
    isCommandInProgress=false, isLocked=null → 常にUNKNOWN状態）を使っているプレースホルダー実装を、
    実際のスマホ接続状態・Sesame API状態取得結果に基づく表示へ置き換える。Mobile側からWear側への
    状態通知（PATH_COMMAND_RESULTとは別の状態同期メッセージ、またはDataLayerのDataClient/
    TileService.getUpdater()による定期更新）を実装する
    （BL-014時点ではクリックアクション自体の技術実装のみを完了させ、Tileが常にUNKNOWNのため
    実際にはタップできない状態が残っている。DESIGN.md記載の未完了事項を引き継いだ）
  優先度: P2
  状態: 未着手
  担当: 共通
  完了条件: Tileが実際のロック状態・スマホ接続状態を反映するロジックの単体テストが成功する
  依存:
    - BL-003
    - BL-013
    - BL-014

- id: BL-016
  区分: 実装
  タスク内容: Wear側でPATH_COMMAND_RESULTメッセージを受信するWearableListenerServiceを実装し、
    受信結果をSesameHapticPlayer.play()（BL-008実装）へ橋渡しする。現状SesameHapticPlayerは
    実装済みだが、呼び出し元となる結果受信の仕組みが存在せず未接続のため本タスクへ切り出した
  優先度: P2
  状態: 未着手
  担当: Wear
  完了条件: 受信メッセージからHapticPatternを再生するまでの接続ロジックの単体テストが成功する
    （受信サービス自体は薄いアダプタのためビルド確認まで）
  依存:
    - BL-006
    - BL-008

- id: BL-009
  区分: 実装
  タスク内容: Complicationで常時ロック状態を文字盤表示する機能を実装する
  優先度: P2
  状態: 未着手
  担当: Wear
  完了条件: ComplicationDataSourceServiceのデータ生成ロジック単体テストが成功する
  依存:
    - BL-006

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
```
<!-- COPILOT_RECORDS:END -->

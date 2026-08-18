<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-005
  区分: 実装
  タスク内容: apikey/secretKey/uuidをスマホ側で安全に保存する機能（EncryptedSharedPreferences等）と設定画面を実装する
  優先度: P2
  状態: 未着手
  担当: Mobile
  完了条件: 保存/読み出しの単体テストが成功し、ログへ平文出力しないことをコードレビューで確認する
  依存:
    - BL-001

- id: BL-006
  区分: 実装
  タスク内容: Wearable Data Layer API（MessageClient）でWatch側からスマホ側へlock/unlock意図を送信し、実行結果を返すメッセージングを実装する
  優先度: P1
  状態: 未着手
  担当: 共通
  完了条件: メッセージ送受信ロジックの単体テストが成功する
  依存:
    - BL-001
    - BL-004

- id: BL-007
  区分: 実装
  タスク内容: Wear OS Tile UIを実装する（ロック状態のアイコン・色表示、施錠ワンタップ、解錠ホールド確認、通信中表示とボタン無効化、スマホ未接続時の明示）
  優先度: P1
  状態: 未着手
  担当: Wear
  完了条件: Tileのロジック単体テスト/プレビューが成功する
  依存:
    - BL-006

- id: BL-008
  区分: 実装
  タスク内容: 施錠/解錠成功時と失敗時を区別するハプティクスフィードバックを実装する
  優先度: P2
  状態: 未着手
  担当: Wear
  完了条件: 成功/失敗の分岐に応じたハプティクスパターン選択ロジックの単体テストが成功する
  依存:
    - BL-007

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
    SesameStatus（batteryVoltage/isBatteryCritical/position/CHSesame2Status/isInLockRange/
    isInUnlockRange）のフィールド構成が実際のレスポンスと一致するかもあわせて確認する（未確認事項、DESIGN.md参照）
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
```
<!-- COPILOT_RECORDS:END -->

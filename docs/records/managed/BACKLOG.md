<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-031
  区分: 実装
  タスク内容: mobile/wearが別々のapplicationId（com.sesamiwear.mobile / com.sesamiwear.wear）を
    持つ現行アーキテクチャは、Google Playが推奨するWear OSアプリの標準的な配布方式
    （1つのAndroid App Bundleにwear feature moduleとして統合し単一のapplicationIdで公開する方式）
    と異なり、2つの独立したアプリとしてPlay Consoleへ登録・限定公開することになる。この制約と
    影響範囲（検索性・自動インストール推奨機能が使えない等）をDESIGN.mdの制約事項として明記し、
    今回のタスクスコープでは統合構成への移行は行わないことを記録する
  優先度: P3
  状態: 未着手
  担当: 共通
  完了条件: DESIGN.mdへ本制約と、今後統合構成へ移行する場合の概要（wear feature module化）を
    記載する
  依存: []

- id: BL-032
  区分: 人手検証
  タスク内容: keytoolコマンド（BL-028で整備した署名設定に対応する形でREADME.mdへ記載する手順）で
    リリース署名用Keystoreを生成し、安全な場所（パスワードマネージャ等）に保管する。秘密鍵の生成・
    保管は自律ループ実行モードの対象外（rules/guardrails-unified.v1.md セクション12.5「実資格情報の
    取り扱い」参照）
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: Keystoreが生成され、BL-028で用意したlocal.properties経由の署名設定から参照できる
    状態になる
  依存:
    - BL-028

- id: BL-033
  区分: 人手検証
  タスク内容: BL-030で作成したプライバシーポリシーを公開可能なURLでホスティングし、Google Play
    ConsoleのData safety（データ安全性）セクションへ申告する
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: プライバシーポリシーURLがPlay Consoleに登録され、データ安全性の申告が完了する
  依存:
    - BL-030

- id: BL-034
  区分: 人手検証
  タスク内容: BL-032のKeystoreを使ってBL-029のリリース設定で署名付きAAB（mobile/wear）をビルドし、
    Google Play Consoleでアプリを新規登録（Play Console提出用の高解像度アイコン画像512x512 PNGの
    用意を含む）、限定公開（内部テストまたはクローズドテスト）トラックへアップロードし、
    テスターを登録する。実機でのスクリーンショット撮影もあわせて行う
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: Google Play Consoleでテスタートラックにアップロードされ、テスターがインストールできる
  依存:
    - BL-011
    - BL-029
    - BL-030
    - BL-032
    - BL-033

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

<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-042
  区分: 人手検証
  タスク内容: BL-041のリング寸法修正後、実機（スマホ・Pixel Watch）でwearのランチャーアイコンを
    目視確認し、コンプリケーション風リングがマスク形状で欠けずに表示されることを確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実機のランチャー・アプリ一覧でwearアイコンのリングが欠けずに表示されることを確認する
  依存:
    - BL-041

- id: BL-041
  区分: 実装
  タスク内容: mobile/src/main/res/drawable/ic_launcher_wear_foreground.xmlのコンプリケーション風リング
    （中心(54,54)、半径40、ストローク幅5のため外周が中心から42.5dpに達する）が、Adaptive Icon
    のセーフゾーン（108dp viewportの中心から半径33dp・直径66dp、Google公式ガイドライン）を
    超えており、丸型等のランチャーマスクで欠けて表示される。半径・ストローク幅を縮小し
    外周が中心から33dp以内に収まるよう修正する
  優先度: P2
  状態: 未着手
  担当: Claude Code
  完了条件: リング外周（半径+ストローク幅/2）が中心から33dp以内に収まる値へ修正し、
    品質ゲート段階B（ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebug）が成功する
  依存: []

- id: BL-040
  区分: 実装
  タスク内容: mobile/MainActivity.ktのonCreate()に、Wear OS実機
    （android.hardware.type.watch機能を持つデバイス）上で起動された場合は資格情報設定画面を
    表示せずfinish()するガードを追加する。wearはmobileのbaseモジュールとして常時ウォッチ側にも
    インストールされるため、BL-039のdist:conditions対応だけではmobileのMainActivityアイコンを
    ウォッチのランチャーから排除できないことへの対処
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: ウォッチ実機でmobileのMainActivityアイコンをタップしても資格情報設定画面が表示されず
    即座に終了し、品質ゲート段階Bが成功する
  依存: []

- id: BL-038
  区分: 人手検証
  タスク内容: BL-036/BL-037完了後、Google Play Console限定公開トラック（BL-034）へ統合後の
    AABをアップロードし、スマホへインストール後にペアリング済みPixel Watchへ自動的にwearアプリが
    プッシュインストールされることを実機で確認する
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: スマホへのインストールのみでPixel Watch側にもwearアプリが自動導入されることを確認する
  依存:
    - BL-036
    - BL-037
    - BL-034

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

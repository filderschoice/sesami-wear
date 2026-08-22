<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-055
  区分: 人手検証
  タスク内容: BL-053/BL-054完了後、実機（Pixel Watch + 複数のSesame 5実機、3〜5台相当）で
    複数Tileインスタンスをそれぞれ異なるSesameデバイスに設定し、各Tileのロック状態表示・
    施錠/解錠操作・ハプティクスと、各Complicationの表示が正しく対象デバイスと対応することを確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 複数台のSesame実機に対し、それぞれ独立したTile/Complicationで誤りなく施錠/解錠操作と
    状態表示ができることを確認する
  依存:
    - BL-053
    - BL-054

- id: BL-054
  区分: 機能追加
  タスク内容: wear側のSesameComplicationDataSourceServiceを、Complicationインスタンス
    （complicationInstanceId）ごとに対象Sesameデバイスを設定・保持できるよう変更する。
    Configuration Activity（BL-052のTile用と同様の仕組み、または共通化）でComplication追加時に
    対象デバイスを選択できるようにし、選択結果をinstanceIdごとに永続化する。
    SesameComplicationContent/SesameStatusSnapshotReaderをdeviceId対応へ変更する
  優先度: P2
  状態: 未着手
  担当: Claude Code
  完了条件: 単体テスト対象ロジック（表示文言生成等）でdeviceIdごとの分岐が検証され、
    ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebugが成功する
  依存:
    - BL-052
    - BL-050

- id: BL-053
  区分: 機能追加
  タスク内容: wear側のSesameTileService/SesameTileActions/SesameActionActivity/
    SesameCommandSenderProvider等のコマンド送信経路を、tileIdに紐付く対象デバイス（BL-052で
    永続化した設定）を参照してdeviceId込みでコマンドを送信するよう変更する。
    SesameStatusSnapshotReaderもdeviceIdごとの状態を読み取れるよう変更する
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: 既存の単体テスト（SesameTileActionsTest等）がdeviceId対応後も成功し、
    ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebugが成功する
  依存:
    - BL-052
    - BL-050

- id: BL-052
  区分: 機能追加
  タスク内容: BL-051の技術調査結果に基づき、wear側にTile Configuration Activityを実装する。
    ユーザーがTileギャラリーからTileを追加する際に対象Sesameデバイスを選択する画面を表示し、
    選択結果をtileIdをキーとして永続化する（DataStore等）仕組みを用意する
  優先度: P1
  状態: ブロック
  担当: Claude Code
  完了条件: Tile追加時に対象デバイス選択画面が表示され、選択結果がtileIdごとに永続化される
    ことをコード上確認できる（実機での最終確認はBL-055）
  依存:
    - BL-051
    - BL-047

- id: BL-044
  区分: 人手検証
  タスク内容: BL-043の修正検証中、./gradlew :mobile:installDebug実行時にPixel 8 Pro（スマホ、
    watchハードウェア機能なし）にもcom.sesamiwear.wear.MainActivity（wearのランチャー
    アクティビティ）がインストールされる現象を確認した。BL-039で追加したdist:conditions/
    dist:device-feature（android.hardware.type.watch限定配信）はGoogle Play正式配信でのみ
    評価され、ローカルのinstallDebug（bundletool経由のAPK Set生成）では評価されない制約による
    可能性があるが未確認。Google Play限定公開トラック（BL-034）へのアップロード後、実際に
    watch限定配信が機能し、スマホ側にwear機能・ランチャーアイコンが含まれないことを確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: Google Play経由でインストールしたスマホ実機にcom.sesamiwear.wear.MainActivityの
    ランチャーアイコンが表示されないことを確認する
  依存:
    - BL-034
    - BL-039

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

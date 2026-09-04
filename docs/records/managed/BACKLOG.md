<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-072
  区分: 不具合対応
  タスク内容: 文字盤のComplication枠にSesami Wearを配置しデバイスを割り当てても、状態文言
    （「施錠」「解錠」「未接続」等）が一切表示されず空欄のままになる（2026-08-30の実機確認、
    Wear OS 7のPixel Watch）。同一の SesameTileStateResolver を使うTile側は正しく状態表示できて
    いるため、状態解決ロジックではなくComplication固有の要因を疑う。切り分け観点は
    (a) 配置した文字盤の枠が要求するComplicationTypeがSHORT_TEXT以外
    （SesameComplicationDataSourceServiceはSUPPORTED_TYPES=SHORT_TEXTのみ、getPreviewDataも
    SHORT_TEXT以外はnullを返す）、(b) onComplicationRequestがCoroutineScope(Dispatchers.IO)から
    listener.onComplicationDataを呼ぶ非同期実装のため、Wearable API呼び出しの遅延・例外で
    listenerが呼ばれずデータ未返却になっている、(c) UPDATE_PERIOD_SECONDS=0のため
    ComplicationDataSourceUpdateRequesterでの更新要求のみが更新契機となっており、
    ComplicationConfigurationActivityからのrequestUpdateが届いていない、の3点
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: Complication枠にデバイスを割り当てた後、対象デバイスの状態文言が文字盤に表示され、
    品質ゲート（ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebug）が成功する
  依存: []

- id: BL-055
  区分: 人手検証
  タスク内容: 2026-08-30の実機確認（Wear OS 7のPixel Watch）で、同一Tileを複数追加することが
    できないと判明した。Watch上の文字盤長押し→タイル編集の「＋」でも、スマホのPixel Watch
    アプリのタイル管理画面でも、追加済みのSesami Wearはチェック済みとして扱われ再選択できない。
    コード側はtileIdごとに対象デバイスを永続化する多重インスタンス対応済み
    （SesameTileService/TileDeviceAssignmentStore、BL-052）であり、wear/AndroidManifest.xmlの
    TileService定義にも多重追加を妨げる指定はないため、Wear OS側のタイル（ウィジェット）管理UIの
    制約と考えられる（未確認）。ユーザー判断により、複数Tileインスタンスの検証は現行環境で
    実施不可として完了条件から外し、単一Tileでのデバイス切り替えと複数Complicationでの
    デバイス別表示に検証範囲を縮小する。Complication側は複数枠への配置とデバイス割り当てまでは
    できたが状態が表示されない問題が判明しており、BL-072として分離した
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: BL-072完了後、実機（Pixel Watch + 複数のSesame 5実機）で(1)単一Tileのデバイス切り替えに
    より各デバイスの状態表示・施錠/解錠操作・ハプティクスが誤りなく対象デバイスへ対応すること、
    (2)文字盤の複数Complication枠にそれぞれ異なるデバイスを割り当て、各枠が対応するデバイスの
    状態を表示すること、の2点を確認する
  依存:
    - BL-072

- id: BL-042
  区分: 人手検証
  タスク内容: 2026-08-30の実機確認で、スマホ・Pixel Watch双方のアプリ一覧でコンプリケーション風
    リングが全く表示されないことが判明した。原因は、mobile/wear統合（BL-036）と1アイコン統一
    （BL-066）によりランチャーへ表示されるアイコンがmobileのic_launcher_foreground.xmlのみと
    なった一方、リングのpathは Tile/Complicationピッカー用の ic_launcher_wear_foreground.xml
    にしか存在しなかったこと。対応としてic_launcher_foreground.xmlへ等倍のリングpathを追加済み
    （実施記録はEXECUTE.md参照）。この修正を反映したビルドでの実機再確認が残っている
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実機（スマホ・Pixel Watch）のランチャー・アプリ一覧でアプリアイコンのリングが
    欠けずに表示されることを確認する
  依存: []

- id: BL-038
  区分: 人手検証
  タスク内容: BL-036/BL-037完了後、Google Play Console限定公開トラック（BL-034）へ統合後の
    AABをアップロードし、スマホへインストール後にペアリング済みPixel Watchへ自動的にwearアプリが
    プッシュインストールされることを実機で確認する。なおローカルビルドの直接インストール
    （ANDROID_SERIALでインストール先を指定した:mobile:installDebug）によるスマホ・Pixel Watch 2
    双方への配信とwearモジュールの振り分けは2026-09-05に確認済みであり、本タスクの対象は
    Google Play経由の自動プッシュインストールに限る（手順はdocs/INSTALL.md参照）
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: スマホへのインストールのみでPixel Watch側にもwearアプリが自動導入されることを確認する
  依存:
    - BL-036
    - BL-037
    - BL-034

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
    Google Play Consoleでアプリを新規登録し、限定公開（内部テストまたはクローズドテスト）
    トラックへアップロードし、テスターを登録する。実機でのスクリーンショット撮影もあわせて行う。
    Play Console提出用の高解像度アイコン画像512x512 PNGは
    docs/store/images/play_store_icon_512.pngとして準備済み（既存のAdaptive Iconデザインを
    ラスタライズしたもの、docs/store/STORE_LISTING.md「アプリアイコン」参照）
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: Google Play Consoleでテスタートラックにアップロードされ、テスターがインストールできる
  依存:
    - BL-029
    - BL-030
    - BL-033

```
<!-- COPILOT_RECORDS:END -->

<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-072
  区分: 人手検証
  タスク内容: 文字盤のComplication枠にSesami Wearを配置しデバイスを割り当てても、状態文言
    （「施錠」「解錠」「未接続」等）が一切表示されず空欄のままになる（2026-08-30の実機確認、
    Wear OS 7のPixel Watch）。実機ログを採取できない状態では3つの想定要因を切り分けられないため、
    2026-09-05にすべての要因に対する防御的修正を実装済み（実施記録はEXECUTE.md参照）。
    (a) 対応ComplicationTypeへLONG_TEXTを追加し、要求された型に応じてデータを作り分ける、
    (b) 状態解決を10秒でタイムアウトさせ、例外・タイムアウト時も「不明」表示へフォールバックして
    listener.onComplicationDataを必ず呼ぶ、(c) UPDATE_PERIOD_SECONDSを0から600へ変更し、更新要求が
    届かない場合も定期更新で表示が回復するようにする。あわせて切り分け用のログ出力
    （TAG=SesameComplication）を追加したため、なお再現する場合は
    `adb logcat -s SesameComplication` で要求されたComplicationTypeとデータ返却有無・例外内容を
    確認できる
  優先度: P1
  状態: 未着手
  担当: ユーザー
  完了条件: 上記修正を反映したビルドをPixel Watchへインストールし、Complication枠にデバイスを
    割り当てた後、対象デバイスの状態文言が文字盤に表示されることを確認する
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

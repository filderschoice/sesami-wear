<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-055
  区分: 人手検証
  タスク内容: 2026-08-30の実機確認（Wear OS 7のPixel Watch）で、同一Tileを複数追加することが
    できないと判明した。Watch上の文字盤長押し→タイル編集の「＋」でも、スマホのPixel Watch
    アプリのタイル管理画面でも、追加済みのSesami Wearはチェック済みとして扱われ再選択できない。
    コード側はtileIdごとに対象デバイスを永続化する多重インスタンス対応済み
    （SesameTileService/TileDeviceAssignmentStore、BL-052）であり、wear/AndroidManifest.xmlの
    TileService定義にも多重追加を妨げる指定はないため、Wear OS側のタイル（ウィジェット）管理UIの
    制約と考えられる（未確認）。ユーザー判断により、複数Tileインスタンスの検証は現行環境で
    実施不可として完了条件から外し、単一Tileでのデバイス切り替えに検証範囲を縮小する。
    Complication側の状態表示の不具合はBL-072として分離し2026-09-05に解消（実機確認済み）、
    複数Complication枠へのデバイス別割り当てはBL-073へ移した
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実機（Pixel Watch + 複数のSesame 5実機）で、単一Tileのデバイス切り替えにより
    各デバイスの状態表示・施錠/解錠操作・ハプティクスが誤りなく対象デバイスへ対応することを
    確認する
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

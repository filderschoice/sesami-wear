<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-089
  区分: ドキュメント
  タスク内容: 外部Pull Requestを受け付けない方針を、関連ドキュメントへ反映する。docs/SUPPORT.md
    （対応方針・受け入れ判断基準の参照）、README.md（CONTRIBUTING.mdの説明文）、SECURITY.md
    （修正対応の主体）、docs/records/managed/DESIGN.md（GitHub公開対応の記述）、CLAUDE.md /
    .github/copilot-instructions.md（参照関係の記述）を実態に合わせる。あわせてBL-085の
    タスク内容へ、Public切り替え時にfork/Pull Requestを抑止する設定（Allow forkingの無効化）の
    検討を追記する
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: 外部PRを受け付ける前提の記述がリポジトリ内に残っておらず、markdownlintが成功する
  依存:
    - BL-087
    - BL-088

- id: BL-086
  区分: 人手検証
  タスク内容: BL-081で確定するプライバシーポリシーの公開URL方針に従い、GitHub上の
    docs/store/PRIVACY_POLICY.md をPublic公開後のURL（またはGitHub Pages）としてホスティングし、
    Play ConsoleのData safety申告へ登録する。実作業はBL-033と同一のため、BL-033完了時に本タスクも
    完了とみなす
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: プライバシーポリシーが恒久URLで参照でき、Play Consoleへ登録されている
  依存:
    - BL-081
    - BL-085

- id: BL-085
  区分: 人手検証
  タスク内容: GitHubリポジトリをPublicへ切り替え、公開リポジトリとしての設定を行う。
    Description・Topics（wear-os / android / sesame / smart-lock / kotlin 等）の設定、Issues有効化、
    SECURITY.mdのSecurity policy認識確認、初回リリースタグ（v0.1.0）とGitHub Releasesの作成、
    Secret scanningの有効化を含む
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: リポジトリがPublicで公開され、Issues・Security policy・Releasesがユーザーから
    参照できる状態になっている
  依存:
    - BL-074
    - BL-076
    - BL-080

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

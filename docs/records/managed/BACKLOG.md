<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-097
  区分: 人手検証
  タスク内容: スマホ用AAB（mobile-release.aab、wearを含まない）を
    電話・タブレットの内部テストトラックへ、ウォッチ用AAB（wear-release.aab）をWear OS専用
    トラックへそれぞれアップロードし、エラーなくリリースを作成できることを確認する。あわせて
    テスターとしてインストールし、スマホ側の資格情報登録とウォッチ側のTile/Complicationが
    動作すること、ウォッチのアプリ一覧にアイコンが1つ表示されること（BL-091でLAUNCHERを
    復活させたため）を実機で確認する。2つのAABは scripts\release-build.bat の1回の実行で
    生成される（BL-093）
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: 両トラックへのアップロードが成功し、テスター配信でスマホ・ウォッチ双方の主要機能が
    動作する
  依存: []

- id: BL-096
  区分: ドキュメント
  タスク内容: BL-085（GitHubリポジトリのPublic化）の完了内容をBACKLOGへ反映する。Public化・
    Description/Topics・Issues有効化・Ruleset（protect-main）・Code security（Secret scanning /
    Push protection / Dependabot / 非公開脆弱性報告）・Actions設定・Security policy認識は完了済み。
    fork抑止は個人アカウントのPublicリポジトリではGitHubが設定を提供しておらず不可能であることを
    確認済みで、CONTRIBUTING.md・PRテンプレートでの明示とクローズ運用で対応済み。初回リリースタグ
    v0.9.0とGitHub Releasesの作成のみ未実施のため、その部分を独立したタスクとして残す
  優先度: P3
  状態: 未着手
  担当: Claude Code
  完了条件: BL-085が完了として削除され、タグ・Releases作成のみが残タスクとして登録されている
  依存: []

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
    SECURITY.mdのSecurity policy認識確認、初回リリースタグ（v0.9.0）とGitHub Releasesの作成、
    Secret scanningの有効化を含む。あわせて、外部からのPull Requestを受け付けない方針
    （BL-087）に対し、リポジトリ設定でforkを抑止できるかを設定画面で確認する（個人アカウントの
    Publicリポジトリでfork禁止の設定が提供されているかは未確認。提供されていない場合は
    CONTRIBUTING.md・PRテンプレートでの明示と、届いたPRをクローズする運用で対応する）
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
  タスク内容: スマホへインストール後、ペアリング済みPixel WatchへGoogle Play経由でウォッチ用
    アプリが自動的にプッシュインストールされることを実機で確認する。BL-090でwearを独立した
    applicationモジュールへ分離したため、単一AABのdynamic featureによる配信ではなく、
    同一applicationIdの2成果物（電話・タブレットのトラックとWear OS専用トラック）による
    自動導入が対象となる。ローカルビルドの直接インストール（ANDROID_SERIALでインストール先を
    指定した:mobile:installDebug）による配信は2026-09-05に確認済みだが、モジュール構成が
    変わったため再確認が必要（手順はdocs/INSTALL.md参照）
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: スマホへのインストールのみでPixel Watch側にもウォッチ用アプリが自動導入されることを
    確認する
  依存:
    - BL-097

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

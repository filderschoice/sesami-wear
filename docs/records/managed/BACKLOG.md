<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-097
  区分: 人手検証
  タスク内容: BL-090〜BL-094完了後、スマホ用AAB（wearを含まない）を電話・タブレットの内部テスト
    トラックへ、ウォッチ用AABをWear OS専用トラックへそれぞれアップロードし、エラーなく
    リリースを作成できることを確認する。あわせてテスターとしてインストールし、スマホ側の資格情報
    登録とウォッチ側のTile/Complicationが動作することを実機で確認する
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: 両トラックへのアップロードが成功し、テスター配信でスマホ・ウォッチ双方の主要機能が
    動作する
  依存:
    - BL-090
    - BL-091
    - BL-092
    - BL-093
    - BL-094

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

- id: BL-095
  区分: ドキュメント
  タスク内容: Google Play提出用に作成したストア素材（機能グラフィック1024x500、スクリーンショット
    5枚）をリポジトリへ追加し、docs/store/README.mdのファイル一覧と「スクリーンショット: 未作成」
    の記述を更新する。あわせてdocs/store/STORE_LISTING.mdの「未確認事項」のうち文字数に関する項目を
    実測値（アプリ名11/30、短い説明50/80、詳細な説明1052/4000、新機能160/500）で解消する。
    docs/store/play_full_description.txtはSTORE_LISTING.mdからの派生物であり原本と乖離する
    リスクがあるため、リポジトリへは追加せず削除する
  優先度: P2
  状態: 未着手
  担当: Claude Code
  完了条件: ストア素材がリポジトリに追加され、docs/store配下の記述が実態と一致している
  依存: []

- id: BL-094
  区分: ドキュメント
  タスク内容: wearを独立applicationモジュールへ変更したことに伴い、単一AAB構成を前提とした記述を
    全ドキュメントで更新する。対象はREADME.md（プロジェクト構成・リリースビルド・ビルドコマンドの
    制約）、docs/records/managed/DESIGN.md（Google Play配布方式・実装制約）、CLAUDE.md
    （モジュール構成・よく使うコマンド・wearのモジュール単体タスクが失敗するという記述）、
    docs/INSTALL.md（インストール手順）、.github/copilot-instructions.md（CLAUDE.mdとの同期）、
    docs/store/README.md・STORE_LISTING.md（単一アプリとして登録する旨の記述）
  優先度: P2
  状態: 未着手
  担当: Claude Code
  完了条件: 単一AAB・dynamic feature前提の記述が残っておらず、2成果物・2トラック構成が正しく
    記載されている。npx markdownlint-cli2が成功する
  依存:
    - BL-090
    - BL-091
    - BL-092
    - BL-093

- id: BL-093
  区分: 実装
  タスク内容: リリースビルドスクリプト（scripts/release-build.ps1 / .bat、scripts/version.properties）
    をスマホ用・ウォッチ用の2成果物へ対応させる。GoogleはversionCodeが全フォームファクタで一意で
    あることを求めるため、mobileとwearでversionCode体系を分離する。あわせてSet-Contentが
    version.propertiesの末尾改行をCRLFへ書き換えビルドのたびに無意味なgit差分が出る問題も修正する
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: 1回の実行でmobile/wear双方の署名付きAABが生成され、versionCodeが衝突しない。
    version.propertiesに不要な改行コード差分が出ない
  根拠: versionCodeの体系はmobileを1始まり、wearを1001始まりの独立系列とする。Googleは独立した
    体系を推奨しており、どちらが新しいかを人が判別しやすい単純な規則を既定値として採用した
  依存:
    - BL-090

- id: BL-092
  区分: 実装
  タスク内容: mobile側のウォッチ向け委譲ロジックを削除する。mobile.MainActivityは
    PackageManager.FEATURE_WATCHを判定してwear.MainActivityへexplicit Intentで委譲していたが、
    分離後はmobileがウォッチへインストールされないため不要になる。あわせてwearのために
    mobileへ追加していたguava依存（BL-036）と、wear側のcompileOnly(guava)を見直し、
    wear側でimplementationとして持つ形へ整理する
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: mobile.MainActivityからウォッチ判定・委譲コードが除去され、両モジュールが
    それぞれ必要なguavaを自前で解決する。品質ゲートが成功する
  依存:
    - BL-090

- id: BL-091
  区分: 実装
  タスク内容: ウォッチ用アイコンリソースをmobileからwearへ移設し、ウォッチ上のランチャー導線を
    復活させる。ic_launcher_wear.xml / ic_launcher_wear_round.xml（mipmap-anydpi-v26）と
    ic_launcher_wear_background.xml / ic_launcher_wear_foreground.xml（drawable）はmobile側に
    あるためwearへ移す。BL-066でwear.MainActivityからLAUNCHER intent-filterを除去したのは
    「baseモジュール（mobile）が常にウォッチにもインストールされる」という単一AAB構成の前提に
    基づくものであり、分離後はウォッチ側にmobileが存在しないためアイコンが1つも無くなる。
    LAUNCHER intent-filterとapplicationのandroid:icon指定を復活させる
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: wearモジュール単体でアイコンリソースが解決でき、ウォッチのアプリ一覧に
    アイコンが1つ表示される構成になっている。品質ゲートが成功する
  依存:
    - BL-090

- id: BL-090
  区分: 実装
  タスク内容: wearモジュールをcom.android.dynamic-featureからcom.android.applicationへ変更し、
    mobileとは独立したAABを生成できるようにする。Googleは単一App BundleへWear OSをdynamic feature
    として同梱する構成を明確に非サポートとしており（Wear OS APKs are separate from mobile APKs /
    You cannot use a single app bundle with a dynamic feature module for Wear OS）、Play Consoleは
    Wear OS向けリリースを専用トラックで公開することを必須としている。applicationIdは
    com.sesamiwear.mobileのまま両成果物で共通とする（同一パッケージ名がGoogleの推奨）。
    wear/AndroidManifest.xmlからdist:moduleブロックを削除し、uses-feature
    android.hardware.type.watchは宣言したまま（required属性は付けない）とする。mobile側は
    dynamicFeatures指定とwear_module_title文字列リソースを削除し、uses-feature watchが
    baseマニフェストへマージされない状態にする
  優先度: P1
  状態: 未着手
  担当: Claude Code
  完了条件: :mobile:bundleReleaseと:wear:bundleReleaseがそれぞれ独立したAABを生成し、
    mobile側のマージ済みマニフェストにandroid.hardware.type.watchが含まれない。
    品質ゲートがすべて成功する
  根拠: 現行構成ではwearのuses-feature watchがbaseマニフェストへマージされ、required属性の
    既定値trueによりアプリ全体が腕時計必須と宣言される。この結果Playはバンドル全体をWear OS
    アプリと分類し、専用トラックを要求すると同時にスマートフォンを配信対象から除外する。
    required=falseを付ける回避策はGoogleが明示的に非サポートとしているため採用しない
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

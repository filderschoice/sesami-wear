<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-099
  区分: 人手検証
  タスク内容: targetSdkを35から36（Android 16）へ引き上げたことによる挙動変化を実機で確認する。
    Android 15（targetSdk 35）で既に必須化されたエッジツーエッジ表示は、Android 16では
    オプトアウトができなくなる。スマホ側はModifier.safeDrawingPadding()を適用済み（BL-045）だが、
    ステータスバー・ナビゲーションバーとの重なりが発生していないかを実機で確認する。あわせて
    ウォッチ側のTile・Complication・各Activityの表示崩れ、施錠/解錠の動作、ハプティクスに
    影響がないことも確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: Pixel 8 ProとPixel Watch 2で表示崩れがなく、施錠/解錠と状態表示が従来どおり動作する
  依存: []

- id: BL-098
  区分: 人手検証
  タスク内容: 初回リリースタグ v0.9.0 を作成し、GitHub Releasesを公開する。本文には
    docs/RELEASE_NOTES.md の 0.9.0 の内容を転記する。BL-085から分離した残件で、配信した成果物と
    タグを対応させるため、BL-097でPlay Consoleの内部テストへのアップロードが成功したことを
    確認してから実施する。あわせて、v* タグを対象としたRulesetで削除・更新を制限することを
    検討する（任意）
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: v0.9.0タグがpushされ、GitHub Releasesから参照できる
  依存:
    - BL-097

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

- id: BL-086
  区分: 人手検証
  タスク内容: GitHub上の docs/store/PRIVACY_POLICY.md をPublic公開後のURLとしてホスティングし、
    Play ConsoleのData safety申告へ登録する。2026-09-06にリポジトリのPublic化が完了し、
    次のURLがHTTP 200で到達可能であることを確認済み。GitHub Pagesは不要と判断した。
    https://github.com/filderschoice/sesami-wear/blob/main/docs/store/PRIVACY_POLICY.md
    Play Consoleのアプリのコンテンツ（App content）へも同URLを登録済み。残るのはBL-033の
    データ安全性の申告完了確認のみで、実作業はBL-033と同一のため、BL-033完了時に本タスクも
    完了とみなす
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: プライバシーポリシーが恒久URLで参照でき、Play Consoleへ登録されている
  依存: []

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
  タスク内容: プライバシーポリシーを公開可能なURLでホスティングし、Google Play ConsoleのData
    safety（データ安全性）セクションへ申告する。公開URLの用意とPlay Consoleへの登録は
    2026-09-06に完了済み（BL-086参照）。データ安全性の申告は、実装を確認した結果
    （SesameApiClient.sendCommandが送信するのはuuid・apikey・コマンド種別・操作履歴タグ・
    コマンド署名であり、secretKeyは端末内での署名計算にのみ使われ送信されない）に基づき、
    apikeyを「個人情報 > ユーザーID」、uuidを「デバイスIDまたはその他のID」として、収集・共有の
    両方・目的はアプリの機能のみで申告する方針を確定済み。secretKeyはGoogleの「収集」の定義
    （端末外への送信）に該当しないため申告対象外
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: プライバシーポリシーURLがPlay Consoleに登録され、データ安全性の申告が完了する
  依存: []

- id: BL-034
  区分: 人手検証
  タスク内容: Google Play Consoleでアプリを新規登録し、限定公開（内部テスト）トラックへ
    アップロードしてテスターを登録する。2026-09-06時点で以下が完了済み。
    (1)署名付きAABのビルド（scripts\release-build.batで2成果物、BL-093）
    (2)パッケージ名 com.sesamiwear.mobile でのアプリ新規作成
    (3)メインのストア掲載情報（テキスト3項目・アイコン・機能グラフィック・スクリーンショット、
    素材はdocs/store/images配下、BL-095）
    (4)アプリのコンテンツ（プライバシーポリシーURL、広告なし、ログインの詳細は「いいえ」、
    コンテンツのレーティング、ターゲットユーザー）
    残るトラックへのアップロードとテスター登録は、2成果物・2トラック構成での実施となるため
    BL-097で扱う
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: Google Play Consoleでテスタートラックにアップロードされ、テスターがインストールできる
  依存:
    - BL-033
    - BL-097

```
<!-- COPILOT_RECORDS:END -->

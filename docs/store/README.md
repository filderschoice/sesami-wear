# ストア掲載情報の管理

Google Play Console へ提出する掲載情報を、このディレクトリで管理します。Play Consoleの各入力欄へ
転記する前の**原本**をここに置き、変更はすべてリポジトリ側で行います。

## ファイル一覧

| ファイル | 内容 | Play Consoleでの用途 |
| --- | --- | --- |
| [STORE_LISTING.md](STORE_LISTING.md) | アプリ名・短い説明・詳細な説明・カテゴリ・対象デバイス | ストアの掲載情報 |
| [PRIVACY_POLICY.md](PRIVACY_POLICY.md) | 収集する情報・保存方法・送信先・削除方法 | プライバシーポリシーURL、データ安全性の申告根拠 |
| [images/play_store_icon_512.png](images/play_store_icon_512.png) | 512x512 PNG（アルファチャンネルなし） | アプリアイコン |
| [images/play_feature_graphic_1024x500.png](images/play_feature_graphic_1024x500.png) | 1024x500 24bit PNG（アルファなし） | 機能グラフィック |
| [images/screenshots/](images/screenshots/) | スマートフォン用2枚・Wear OS用3枚 | スクリーンショット |

`images/screenshots/` の内訳は次のとおりです。ファイル名の接頭辞がPlay Consoleの投入先の枠に
対応します（スマートフォン用とWear OS用は別の枠です）。

| ファイル | 内容 | サイズ |
| --- | --- | --- |
| `phone_1_credentials.png` | 資格情報の設定画面（登録済みデバイス一覧と追加フォーム） | 1683x2992 |
| `phone_2_help.png` | 値の取得方法を案内するヘルプダイアログ | 1683x2992 |
| `wear_1_complication.png` | ウォッチフェイスのコンプリケーション（施錠状態の表示） | 384x384 |
| `wear_2_tile_locked.png` | タイル（全施錠中） | 384x384 |
| `wear_3_device_select.png` | 操作対象デバイスの選択画面 | 384x384 |

- スマートフォン用は実機（Pixel 8 Pro）の1344x2992を、内容を切らずに左右へ背景色の帯を足して
  16:9へ整えたものです。Play Consoleは縦横比の上限があり、実解像度のままでは弾かれる可能性が
  あるためです。
- 登録済みデバイスの表示名は、公開を避けるため画像上でモザイク処理しています。

リリースごとの「このリリースの新機能」欄の文面は、このディレクトリではなく
[../RELEASE_NOTES.md](../RELEASE_NOTES.md) の各バージョンの「ストア掲載用の要約」で管理します。

## Play Consoleの入力項目との対応

| Play Consoleの項目 | 参照元 | 備考 |
| --- | --- | --- |
| アプリ名 | STORE_LISTING.md「アプリ名」 | 30文字以内 |
| 短い説明 | STORE_LISTING.md「短い説明」 | 80文字以内 |
| 詳細な説明 | STORE_LISTING.md「詳細な説明」 | 4000文字以内 |
| アプリのカテゴリ | STORE_LISTING.md「カテゴリ（案）」 | |
| アプリアイコン | images/play_store_icon_512.png | 512x512、32bit PNG（アルファなし） |
| 機能グラフィック | images/play_feature_graphic_1024x500.png | 1024x500、24bit PNG（アルファなし） |
| スクリーンショット（スマートフォン） | images/screenshots/phone_*.png | 最低2枚 |
| スクリーンショット（Wear OS） | images/screenshots/wear_*.png | スマートフォン用とは別の枠へ投入する |
| プライバシーポリシーURL | PRIVACY_POLICY.md（下記「公開URL」） | 必須項目 |
| データ安全性（Data safety） | PRIVACY_POLICY.md の記載内容 | 申告はBACKLOG BL-033 |
| サポートメールアドレス | PRIVACY_POLICY.md「お問い合わせ先」 | 必須項目 |
| このリリースの新機能 | ../RELEASE_NOTES.md「ストア掲載用の要約」 | 500文字以内 |

## プライバシーポリシーの公開URL

Play Consoleは、誰でも閲覧できるURLでのプライバシーポリシー掲載を求めます。本リポジトリのPublic公開後は、
次のURLをそのまま利用できます。

```text
https://github.com/filderschoice/sesami-wear/blob/main/docs/store/PRIVACY_POLICY.md
```

- URLはブランチ名（`main`）を含むため、ブランチ名を変更した場合はPlay Console側の登録も更新します。
- 恒久的なURLが必要になった場合はGitHub Pagesでの公開へ切り替えます（その場合も原本は本ファイル群です）。

## 更新手順

1. 掲載内容を変更する場合は、このディレクトリのファイルを更新します（Play Console側を直接書き換えて
   原本と乖離させないこと）。
2. 文字数の上限（上表参照）を超えていないか確認します。
3. `npx markdownlint-cli2 "**/*.md"` を実行します。
4. Play Consoleへ転記し、反映日を各ファイルの「最終更新日」へ記録します。
5. プライバシーポリシーを変更した場合は、データ安全性の申告内容に影響がないかもあわせて確認します。

## 関連ドキュメント

- [../RELEASE_NOTES.md](../RELEASE_NOTES.md): バージョンごとの変更点とストア掲載用の要約
- [../SUPPORT.md](../SUPPORT.md): 利用者向けのサポート窓口・アップデート確認先
- [../../README.md](../../README.md): リリースビルド手順（署名・AAB作成）
- [../records/managed/BACKLOG.md](../records/managed/BACKLOG.md): Google Play公開に関する人手検証タスク
  （BL-033 / BL-034 / BL-038 / BL-086）

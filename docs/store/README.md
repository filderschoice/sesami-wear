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
| [images/screenshots/](images/screenshots/) | スマートフォン用6枚・Wear OS用6枚 | スクリーンショット |

`images/screenshots/` の内訳は次のとおりです。ファイル名の接頭辞がPlay Consoleの投入先の枠に
対応します（スマートフォン用とWear OS用は別の枠です）。

| ファイル | 内容 | サイズ |
| --- | --- | --- |
| `phone_1_widget_locked.png` | ホーム画面ウィジェット（施錠中、タップで解錠） | 1350x2400 |
| `phone_2_widget_unlock_confirm.png` | ウィジェットからの解錠確認ダイアログ | 1350x2400 |
| `phone_3_widget_unlocked.png` | ホーム画面ウィジェット（解錠中、タップで施錠） | 1350x2400 |
| `phone_4_widget_config.png` | ウィジェットの操作対象を選ぶ画面（全デバイス／個別） | 1350x2400 |
| `phone_5_credentials.png` | 資格情報の設定画面（登録済みデバイス一覧と追加フォーム） | 1350x2400 |
| `phone_6_help.png` | ヘルプのメニュー（値の取得方法・デモ・ウォッチ・ウィジェット） | 1350x2400 |
| `wear_1_complication.png` | ウォッチフェイスのコンプリケーション（施錠状態の表示） | 384x384 |
| `wear_2_tile_locked.png` | タイル（全施錠中、タップで全解錠） | 384x384 |
| `wear_3_tile_unlocked.png` | タイル（全解錠中、タップで全施錠） | 384x384 |
| `wear_4_tile_mixed.png` | タイル（一部解錠、タップで全施錠）。複数デバイスの状態が混在した場合 | 384x384 |
| `wear_5_unlock_confirm.png` | 解錠時の確認画面（キャンセル／全解錠） | 384x384 |
| `wear_6_device_select.png` | 操作対象デバイスの選択画面 | 384x384 |

- スマートフォン用はAndroidエミュレータ（AVD `nocompanion`、Pixel 6相当、1080x2400）の画面を、
  内容を切らずに左右へ背景色の帯（`#1E3A5F`）を足して9:16へ整えたものです。Play Consoleは
  縦横比の上限があり、実解像度のままでは弾かれる可能性があるためです。
- 表示名は実在しないダミー（`Entrance` / `Garage`）を登録して撮影しているため、モザイク処理は
  不要です（2026-09-06版の実機撮影ではモザイクを掛けていました）。施錠・解錠の状態は
  モックAPI（`scripts/mock-sesame-api.py`）で作っており、実Sesameデバイス・実資格情報は
  使っていません。
- ファイル名の番号はPlay Consoleへ投入する順序に対応します。スマートフォン用は「ウィジェットの
  状態表示 → 操作の確認 → 設定 → 資格情報 → ヘルプ」、Wear OS用は「状態表示 → タイルの
  各状態 → 操作の確認 → 設定」の流れで並べています。
- スマートフォン用は2026-09-18に全点を撮り直しました。0.11.0のホーム画面ウィジェット（BL-121〜
  BL-123）を追加し、ヘルプのメニュー化（BL-113）とウィジェット項目の追加（BL-124）を反映して
  います。
- Wear OS用の6点は2026-09-06撮影のものを継続利用します。以降のwear側の変更は文言・色のcoreへの
  移設（BL-119、表示は不変）と、登録0台のときだけ出るデモ説明文の折り返し修正（BL-114）だけで、
  この6点の画面には影響しないことを差分で確認済みです。

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
| データ安全性（Data safety） | PRIVACY_POLICY.md の記載内容 | 申告済み（下記「データ安全性の申告内容」） |
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

## データ安全性の申告内容

2026-09-07にPlay Consoleへ申告済みの内容です。実装（`core.api.SesameApiClient.sendCommand`が
実際に送信する項目）に基づいて決定しています。再申告・内容変更の際はこの表を基準にします。

| 設問 | 回答 |
| --- | --- |
| ユーザーデータを収集または共有するか | はい |
| 転送中のデータは暗号化されるか | はい（Sesame APIはHTTPS） |
| ユーザーがデータの削除をリクエストできるか | いいえ（削除導線は未実装。端末内の登録削除のみ可能） |
| 独立したセキュリティ審査（MASA）を受けたか | いいえ |

申告したデータタイプは次の2つのみです。いずれも「収集」「共有」の両方に該当し、一時的な処理ではなく、
利用者にとって必須、目的は「アプリの機能」のみとしています。

| データタイプ | 該当する値 |
| --- | --- |
| 個人情報 > ユーザーID | `apikey` |
| デバイスIDまたはその他のID | Sesameデバイスの `uuid` |

- **`secretKey` は申告対象外です。** 端末内でAES-CMAC署名の計算にのみ使用され、端末外へ送信されないため、
  Googleの「収集」の定義（端末外への送信）に該当しません。認証情報として申告しないよう注意してください。
- 送信先のSesame APIはCANDY HOUSE社（第三者）が運営するため、「収集」だけでなく「共有」にも該当します。
- 広告ID（`AD_ID`）は使用していません。広告SDKを含まないため、マージ後のマニフェスト・AABのいずれにも
  `com.google.android.gms.permission.AD_ID` は含まれません（広告の宣言は「いいえ」）。

## 更新手順

1. 掲載内容を変更する場合は、このディレクトリのファイルを更新します（Play Console側を直接書き換えて
   原本と乖離させないこと）。
2. 文字数の上限（上表参照）を超えていないか確認します。
3. `npx markdownlint-cli2 "**/*.md"` を実行します。
4. Play Consoleへ転記し、反映日を各ファイルの「最終更新日」へ記録します。
5. プライバシーポリシーを変更した場合は、データ安全性の申告内容に影響がないかもあわせて確認します。
6. スクリーンショットを撮り直す場合は、Androidエミュレータで撮影します（手順はスキル
   `store-screenshot-capture`）。実機のPlayストア版では実資格情報と実デバイス名が写り、
   操作すると実際の鍵が動くため使いません。Wear OS用だけは、ウォッチ側の表示を変えたときに
   Pixel Watch 2の実機で撮り直します。

## 関連ドキュメント

- [../RELEASE_NOTES.md](../RELEASE_NOTES.md): バージョンごとの変更点とストア掲載用の要約
- [../SUPPORT.md](../SUPPORT.md): 利用者向けのサポート窓口・アップデート確認先
- [../../README.md](../../README.md): リリースビルド手順（署名・AAB作成）
- [../records/managed/BACKLOG.md](../records/managed/BACKLOG.md): Google Play公開に関する人手検証タスク
  （未対応分の有無は同ファイルを参照してください）

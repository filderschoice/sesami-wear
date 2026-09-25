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
| `phone_1_widget_locked.png` | ホーム画面ウィジェット（施錠中、タップで解錠）。2x1と4x2を並べて表示 | 1350x2400 |
| `phone_2_widget_unlock_confirm.png` | ウィジェットからの解錠確認ダイアログ | 1350x2400 |
| `phone_3_widget_unlocked.png` | ホーム画面ウィジェット（解錠中、タップで施錠）。2台の状態が混在した表示 | 1350x2400 |
| `phone_4_device_list.png` | アプリ主画面（Sesameごとのカード。状態・電池・角度・最終取得時刻・経路・Bluetoothの到達状況） | 1350x2400 |
| `phone_5_menu.png` | 右上の「⋮」メニュー（操作の経路・Bluetoothで直接操作・通知・ヘルプ・診断ログ） | 1350x2400 |
| `phone_6_device_edit.png` | Sesameの追加・編集画面（表示名・uuid・apikey・secretKey） | 1350x2400 |
| `phone_7_help.png` | ヘルプのメニュー（値の取得方法・デモ・ウォッチ・ウィジェット・Bluetooth） | 1350x2400 |
| `wear_1_complication.png` | ウォッチフェイスのコンプリケーション（施錠状態の表示） | 384x384 |
| `wear_2_tile_locked.png` | タイル（全施錠中、タップで全解錠） | 384x384 |
| `wear_3_tile_unlocked.png` | タイル（全解錠中、タップで全施錠） | 384x384 |
| `wear_4_tile_mixed.png` | タイル（一部解錠、タップで全施錠）。複数デバイスの状態が混在した場合 | 384x384 |
| `wear_5_unlock_confirm.png` | 解錠時の確認画面（キャンセル／全解錠） | 384x384 |
| `wear_6_device_select.png` | 操作対象デバイスの選択画面 | 384x384 |

- スマートフォン用は、`phone_1`〜`phone_3`（ホーム画面ウィジェット）が実機、`phone_4`〜`phone_7`（アプリ内の画面）が
  Androidエミュレータの撮影です。どちらも内容を切らずに左右へ背景色の帯（`#1E3A5F`）を足して9:16
  （1350x2400）へ整えています。Play Consoleは縦横比の上限があり、実解像度のままでは弾かれる可能性があるためです。
- `phone_1`〜`phone_3` は2026-09-26にPixel 8 Pro実機で撮り直しました（0.14.0。帯の経路マーク・
  左列・帯の色の変更、BL-205 / BL-206 / BL-209 / BL-210 を反映）。デバッグ版の撮影モード
  （README.md「撮影モード（デバッグ版のみ）」）で見本のデバイス（玄関・勝手口）と状態を出しているため、
  実Sesameデバイス・実資格情報は使っておらず、実デバイス名も写っていません。
  実機の状態が写るステータスバーとドックはモザイク、壁紙はウィジェットとダイアログ以外をぼかして
  います（手順は Skill `store-screenshot-masking`）。
- `phone_4`〜`phone_7` は2026-09-21にAndroidエミュレータ（AVD `nocompanion`、Pixel 6相当、1080x2400）で
  撮り直したものです（0.13.0）。スマートフォン画面のカードUIへの刷新（BL-173〜BL-188）、「⋮」メニューへの
  集約、Sesameの追加・編集画面の独立を反映しています。0.14.0ではリリース版のアプリ内の画面が
  変わっていないため据え置きました（デバッグ版で撮ると撮影モードの表示が写るため、撮り直していません）。
  表示名は実在しないダミー（`Entrance` / `Garage`）、施錠・解錠の状態はモックAPI
  （`scripts/mock-sesame-api.py`）で作っています。旧構成の `phone_4_widget_config.png` /
  `phone_5_credentials.png` / `phone_6_help.png` は削除しました。
- `phone_4_device_list.png` のカードに出る「Bluetooth：圏内（たった今確認）」と経路の
  「Bluetooth」は、BLEの到達実績の保存値（`sesami_wear_ble_reachability.xml`）と状態の保存値
  （`sesami_wear_lock_state.xml`）へ、圏内・経路BLEの値を書き込んで再現したものです。
  エミュレータの近くに実Sesameが無く、そのままでは「圏外」表示になるためで、Sesameの
  電波圏内にいる利用者が実際に見る表示と同じです（施錠・解錠の状態をモックAPIで作るのと同じ扱い）。
- ファイル名の番号はPlay Consoleへ投入する順序に対応します。スマートフォン用は「ウィジェットの
  状態表示 → 操作の確認 → アプリ画面 → 設定 → ヘルプ」、Wear OS用は「状態表示 → タイルの
  各状態 → 操作の確認 → 設定」の流れで並べています。
- Wear OS用の `wear_2`〜`wear_4`・`wear_6` は、2026-09-25にPixel Watch 2実機で撮り直しました（0.14.0）。
  タイルは帯の経路マーク（図柄）・左列の幅・帯の色の変更を反映しています。撮影モードの見本の3台
  （玄関・勝手口・ガレージ）で状態を作っているため、実物のセサミは操作しておらず、デバイス選択画面にも
  実デバイス名は写っていません。画面をそのまま保存しており、加工はしていません（384x384）。
- `wear_5_unlock_confirm.png`（2026-09-21に撮り直した画像が既存とバイト単位で一致）は、0.14.0でも
  画面が変わっていないため据え置いています。`wear_1_complication.png` は2026-09-06撮影のままです。
  撮影時の文字盤が手元に無いため撮り直しておらず、0.13.0で長いテキストへ加わった経路の絵文字と
  最終取得時刻は写っていません。
- Wear OSエミュレータ（AVD `wearos`）はスマートフォンのエミュレータとペア設定できず、タイルが常に
  「スマホ未接続」表示になるため撮影には使えません（`docs/INSTALL.md`「1.8」）。

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

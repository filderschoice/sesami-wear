# Google Play ストア掲載情報

Google Play Console提出用のストア掲載情報の原本です。Play Consoleの入力項目との対応と更新手順は
[README.md](README.md) を参照してください。Play Consoleへの登録と限定公開は実施済みで、以降は
本ファイルを更新したうえでPlay Consoleへ転記します。
`mobile`と`wear`は同一の`applicationId`を共有するため、Google Playには**1つのアプリ**として
登録します（掲載ページは1つで、mobile用・wear用を別アプリとして登録する必要はありません）。
ただし成果物は2つに分かれており、スマホ用AABは電話・タブレット系トラックへ、ウォッチ用AABは
**Wear OS専用トラック**へアップロードします（BL-090。DESIGN.md「Google Play配布方式」参照）。
そのためスクリーンショットも、スマートフォン用とWear OS用でそれぞれの枠へ投入します。

## アプリ名

Sesami Wear

## 短い説明（80文字以内）

Pixel Watchとホーム画面ウィジェットから、CANDY HOUSE Sesameスマートロックを施錠・解錠

## 詳細な説明（4000文字以内）

Sesami Wearは、Pixel Watchやスマートフォンのホーム画面ウィジェットからCANDY HOUSE Sesame
（Sesame 5 + Hub 3）スマートロックを操作するためのアプリです。スマートフォンのホーム画面ウィジェットはウォッチが無くても
使えます。

### 主な機能

- Wear OSのタイル（Tile）から施錠・解錠をワンタップで実行できます
- 解錠は誤操作防止のため確認ダイアログを経由し、施錠は即時実行します
- 通信中は処理中表示とボタン無効化により二重送信を防ぎます
- 施錠・解錠の成功/失敗をハプティクス（振動パターン）で区別して通知します
- ウォッチフェイスのコンプリケーションにロック状態を表示できます
- 状態の更新はタップで行います（自動取得は行いません）。最後に状態を取得した時刻もあわせて表示します
- 複数台のSesameを登録し、タイル・コンプリケーションごとに操作対象を切り替えられます
- 2台以上を登録している場合は「全デバイス」を選んで一括で施錠・解錠できます
- スマートフォン未接続時はタイル上に明示し、誤操作を防止します
- スマートフォンのホーム画面ウィジェットから、タイルと同じ表示・操作で施錠・解錠できます（ウォッチ不要）
- ウィジェットは大きさを変えられ、操作の結果は振動でも通知します
- Sesameを登録していない状態では、実際の鍵を操作しない「デモ」で操作感を確認できます

### 必要な準備

利用には biz.candyhouse.co（SESAME Biz 開発者ページ）で確認・発行できるuuid・apikey・secretKey
の3点が必要です。これらはスマートフォン側のSesami Wearアプリ（mobile）に暗号化して保存し、
Watch単体には保持しません。

### 使い方・サポートについて

使い方の詳細、更新内容の確認先、不具合の報告方法は、GitHubリポジトリで公開しています。

- 利用ガイド: <https://github.com/filderschoice/sesami-wear/blob/main/docs/USER_GUIDE.md>
- 更新内容（リリースノート）: <https://github.com/filderschoice/sesami-wear/blob/main/docs/RELEASE_NOTES.md>
- サポート情報・お問い合わせ: <https://github.com/filderschoice/sesami-wear/blob/main/docs/SUPPORT.md>

### プライバシーについて

収集する情報の範囲と取り扱いは [プライバシーポリシー](PRIVACY_POLICY.md) を参照してください。

### 非公式アプリについて

本アプリは個人による非公式アプリであり、CANDY HOUSE株式会社とは提携・協力関係にありません。
「CANDY HOUSE」「Sesame」は同社の商標または登録商標です。公開されているSesame APIを利用して
実装していますが、CANDY HOUSE社による動作保証・サポート対象外です。

アプリ名の「Sesami」は、公式製品の「Sesame」をもじった造語です。公式製品と取り違えないよう、
綴りをあえて変えています。Sesameの日本語読み「セサミ」をローマ字にしたもので、イタリア語で
sesamo（ゴマ）の複数形が sesami であることから、複数台のSesameをまとめて扱えるアプリという
意味も込めています。

## カテゴリ（案）

ツール（Tools）

## 対象デバイス

- Android 8.0（API 26）以降のスマートフォン（mobile側の対応OSバージョン。Pixel 8 Proで動作確認済み。
  下限のAPI 26端末での確認は未実施）。ホーム画面ウィジェットはスマートフォンだけで利用できます
- Wear OS 3以降を搭載したスマートウォッチ（任意。タイル・コンプリケーションを使う場合。Pixel Watch 2で
  動作確認済み）

## アプリアイコン

Play Console提出用の512x512 PNG（アルファチャンネルなし）を用意済みです。既存のAdaptive Icon
（`mobile/src/main/res/drawable/ic_launcher_background.xml` / `ic_launcher_foreground.xml`）の
デザインをそのままラスタライズしたものです。

- [images/play_store_icon_512.png](images/play_store_icon_512.png)

## 文字数の実測値

本ファイルの現在の内容の文字数です。いずれもGoogle Playの上限内に収まっています。
本ファイルを更新した際は再計測してください。

| 項目 | 実測 | 上限 |
| --- | --- | --- |
| アプリ名 | 11 | 30 |
| 短い説明 | 57 | 80 |
| 詳細な説明 | 1546 | 4000 |
| このリリースの新機能（`../RELEASE_NOTES.md` の最新バージョン） | 429 | 500 |

「詳細な説明」の実測値はMarkdown記法を含む本ファイル上の文字数です。Play Consoleの入力欄は
Markdownを解釈しないため、転記時は見出し・箇条書きの記法を除き、段落途中の改行も詰めてください。

## スクリーンショット

スマートフォン用6枚（Androidエミュレータ、2026-09-18撮影）とWear OS用6枚（Pixel Watch 2実機、
2026-09-06撮影）を [images/screenshots/](images/screenshots/) に用意済みです。内訳と加工内容は
[README.md](README.md)「ファイル一覧」を参照してください。

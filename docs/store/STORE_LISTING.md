# Google Play ストア掲載情報

Google Play Console提出用のストア掲載情報の原本です。Play Consoleの入力項目との対応と更新手順は
[README.md](README.md) を参照してください。実際の入力・公開はBL-034（人手検証、限定公開）で行います。
`mobile`と`wear`は同一の`applicationId`を共有するため、Google Playには**1つのアプリ**として
登録します（掲載ページは1つで、mobile用・wear用を別アプリとして登録する必要はありません）。
ただし成果物は2つに分かれており、スマホ用AABは電話・タブレット系トラックへ、ウォッチ用AABは
**Wear OS専用トラック**へアップロードします（BL-090。DESIGN.md「Google Play配布方式」参照）。
そのためスクリーンショットも、スマートフォン用とWear OS用でそれぞれの枠へ投入します。

## アプリ名

Sesami Wear

## 短い説明（80文字以内）

Pixel WatchからCANDY HOUSE Sesameスマートロックをワンタップで施錠・解錠

## 詳細な説明（4000文字以内）

Sesami Wearは、Pixel WatchからCANDY HOUSE Sesame（Sesame 5 + Hub 3）スマートロックを
操作するためのアプリです。

### 主な機能

- Wear OSのタイル（Tile）から施錠・解錠をワンタップで実行できます
- 解錠は誤操作防止のため確認ダイアログを経由し、施錠は即時実行します
- 通信中は処理中表示とボタン無効化により二重送信を防ぎます
- 施錠・解錠の成功/失敗をハプティクス（振動パターン）で区別して通知します
- ウォッチフェイスのコンプリケーションで現在のロック状態を常時表示できます
- スマートフォン未接続時はタイル上に明示し、誤操作を防止します

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

## カテゴリ（案）

ツール（Tools）

## 対象デバイス

- Wear OS 3以降を搭載したスマートウォッチ（Pixel Watch 2で動作確認済み）
- Android 8.0（API 26）以降のスマートフォン（mobile側の対応OSバージョン。Pixel 8 Proで動作確認済み。
  下限のAPI 26端末での確認は未実施）

## アプリアイコン

Play Console提出用の512x512 PNG（アルファチャンネルなし）を用意済みです。既存のAdaptive Icon
（`mobile/src/main/res/drawable/ic_launcher_background.xml` / `ic_launcher_foreground.xml`）の
デザインをそのままラスタライズしたものです。

- [images/play_store_icon_512.png](images/play_store_icon_512.png)

## 未確認事項

- スクリーンショットはBL-034の実機検証時に撮影します
- 上記の説明文の文字数はGoogle Playの上限内に収まるよう記載していますが、
  実際のPlay Console入力時に再確認が必要です（未確認）

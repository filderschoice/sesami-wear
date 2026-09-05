# Sesami Wear プライバシーポリシー

最終更新日: 2026-09-06

本ドキュメントは、Android / Wear OSアプリ「Sesami Wear」のプライバシーポリシーです。
本ファイル自体が公開版であり、次のURLで参照できます（Google Play Consoleのプライバシーポリシー欄には
このURLを登録します）。

```text
https://github.com/filderschoice/sesami-wear/blob/main/docs/store/PRIVACY_POLICY.md
```

Play ConsoleのData safety（データ安全性）セクションへの申告は、本ポリシーの記載内容に基づいて行います
（申告作業自体はBACKLOG BL-033）。

## 収集する情報

Sesami Wear（mobile / wear）は、CANDY HOUSE Sesameスマートロックを操作するために
以下の情報を扱います。

- uuid（sesame2_uuid）: 操作対象のSesameデバイスを識別するための情報
- apikey: CANDY HOUSE APIサーバーへの認証に使用する情報
- secretKey: 施錠・解錠コマンドの署名（AES-CMAC）に使用する秘密鍵

これらはすべて利用者本人が biz.candyhouse.co（SESAME Biz 開発者ページ）から取得し、
アプリの設定画面で入力する情報であり、本アプリが第三者から取得することはありません。

## 保存方法

上記の情報は、スマートフォン側アプリ（mobile）内でのみ、Android標準の
`androidx.security.crypto`（EncryptedSharedPreferences）により暗号化して端末内に
保存します。ウォッチ側アプリ（wear）は上記の情報を保持せず、施錠・解錠の操作意図
（コマンド種別のみ）をWearable Data Layer APIでスマートフォン側へ送信します。

本アプリの開発者が管理するサーバーは存在せず、これらの情報を開発者側へ送信・バックアップする
ことは一切ありません（Sesame APIへの送信内容は次節を参照してください）。

## 情報の送信先

施錠・解錠・状態取得の実行時に、CANDY HOUSE社が提供するSesame API
（`https://app.candyhouse.co/api/sesame2/`）へ以下を送信します。

- uuid: 操作対象のSesameデバイスを指定するため、リクエストURLに含めます
- apikey: APIの認証に使用するため、リクエストヘッダーに含めます
- コマンド種別・操作履歴タグ・コマンド署名

secretKeyは端末外へ送信しません。施錠・解錠コマンドの署名（AES-CMAC）は端末内で計算し、
その計算結果である署名値のみをSesame APIへ送信します。

上記以外の第三者（広告事業者、分析事業者等）への送信は行いません。本アプリは広告SDK・
アクセス解析SDKを組み込んでいません。

## ログについて

アプリのログにuuid・apikey・secretKeyを出力しません。

## 情報の削除

以下のいずれかの方法で、端末に保存された情報を削除できます。

- アプリ内の設定画面から資格情報を削除する
- Android設定の「アプリ情報」からアプリのデータを消去する
- アプリをアンインストールする

## 子どものプライバシー

本アプリは子どもを対象としたサービスではなく、子どもから意図的に情報を収集することは
ありません。

## ポリシーの変更

本ポリシーの内容を変更する場合は、本ドキュメントを更新し、冒頭の最終更新日を改めます。
変更の履歴は、GitHubリポジトリの本ファイルの変更履歴で確認できます。

## お問い合わせ先

- メール: <filderschoice.release@gmail.com>
- GitHub Issues: <https://github.com/filderschoice/sesami-wear/issues>
  （問い合わせの詳細は [サポート情報](../SUPPORT.md) を参照してください）

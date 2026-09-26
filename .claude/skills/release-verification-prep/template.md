# <バージョン> リリース前の実機検証手順書

- 対象ブランチ: `<ブランチ名>`
- 対象機: Pixel 8 Pro（スマホ）＋ Pixel Watch 2（ウォッチ）＋ 登録済みの Sesame 5
- 対象の人手検証: <BL-xxx, BL-yyy>
- 本ファイルは `*.local.md` のためGit管理外です。Google Playの両トラックで公開中になった時点で削除します。
- 作成・更新: Skill `release-verification-prep`（インストールまでを Claude Code、検証の操作をユーザーが行う）

## 0. インストール状況

| 端末 | パッケージ | versionCode | 更新時刻 | 接続先 |
| --- | --- | --- | --- | --- |
| Pixel 8 Pro | `com.sesamiwear.mobile.debug`（Sesami Wear (debug)） | （versionCode） | （日時） | <本番のSesame API / モックAPI> |
| Pixel Watch 2 | `com.sesamiwear.mobile.debug` | （versionCode） | （日時） | — |

- Play ストア版はそのまま残っています。**検証はすべて「(debug)」の付いた側で行ってください。**
- <資格情報の登録状況・権限・経路の方針など、2.4で確認した前提>
- <本番APIに接続している場合: 状態チップ（右下）をタップすると実際に施錠・解錠されます>

入れ直す場合のコマンド（リポジトリのルートで実行。ウォッチを先に入れる）:

```bash
ANDROID_SERIAL=<ウォッチのシリアル> ./gradlew :wear:installDebug
ANDROID_SERIAL=<スマホのシリアル> ./gradlew :mobile:installDebug
```

## 1. 事前準備

1. <ホーム画面へデバッグ版のウィジェットを置く、など>
2. <ウォッチにデバッグ版のタイルを追加する（コマンドは下記）>

   ```bash
   adb -s <ウォッチのシリアル> shell am broadcast \
     -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile \
     --ecn component com.sesamiwear.mobile.debug/com.sesamiwear.wear.tile.SesameTileService
   ```

3. スマホはUSB接続のまま、ウォッチは充電器に載せておく（ログを取りやすくするため）。

## 2. 確認に使うコマンド（任意）

画面だけで判定できない項目は、次のログ・保存値で確かめられます。Claude Code に
「ログを確認して」と依頼すれば代わりに実行します。

```bash
# 経路と成否（route=BLE op=STATUS detail=SUCCESS / NOT_FOUND など）
adb -s <スマホのシリアル> logcat -d -s SesameApi | tail -20
# API呼び出し回数
adb -s <スマホのシリアル> shell run-as com.sesamiwear.mobile.debug cat shared_prefs/sesami_wear_api_usage.xml
# ログを空にしてから1操作ずつ見る
adb -s <スマホのシリアル> logcat -c
```

## 3. 検証項目

<!-- BLごとに「### BL-xxx <件名>」の節を足す。同じリリースの検証はこのファイルへ集める。 -->

### BL-xxx <件名>

<前提・注意（鍵が実際に動く項目、操作の間隔など）>

| No. | 操作 | 期待結果 | 結果 |
| --- | --- | --- | --- |
| 1 | <操作> | <期待結果（判定に使えるログ・保存値も）> | |

## 4. 結果の記録

- 各項目の「結果」欄へ ○／×／未実施 と、気づいた点を書き込んでください。
- 終わったら Claude Code に「BL-xxxの結果を記録して」と伝えてください。この表をもとに
  `DESIGN.md` と `BACKLOG.md` を更新します（実資格情報・uuidは記録へ書きません）。
- ×がある場合は、そのときの画面の様子と時刻を添えてください（ログから原因を追えます）。

## 5. 後始末

- 検証用に置いたウィジェット・タイルは、残しても外しても構いません。タイルを外す場合は
  1の追加コマンドの `add-tile` を `remove-tile` に替えて実行します。
- <検証中に変えた設定（経路の方針など）を元へ戻す>
- デバッグ版アプリは両端末に残ります。Play版の動作には影響しません。

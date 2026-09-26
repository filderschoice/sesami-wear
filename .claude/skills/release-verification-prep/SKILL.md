---
name: release-verification-prep
description: 本リポジトリの人手検証（BACKLOG.md の `区分: 人手検証`）を始めるときの既定の手順。Claude Code は実機（Pixel 8 Pro / Pixel Watch 2）へのデバッグ版インストールまでを行い、検証の操作はユーザーが行う。検証項目は個別のBLに閉じず、次のリリースごとの手順書 `docs/verification/VERIFICATION-<バージョン>.local.md`（Git管理外）へBLごとの節として追記する。adb接続とインストール、インストール結果の確認、テキストでの前提確認、手順書の作成・追記、結果の記録の依頼の受け方、リリース（両トラック公開）時の手順書の削除までを含む。「実機検証を行うのでインストールして」「人手検証の準備をして」「検証手順書を作って」「BL-xxxを実機で確認したい」「リリース前の検証を準備して」「トラックで公開された」等の依頼で参照する。Claude Code自身がadbでUIを操作して検証するのは、ユーザーが明示的に求めた場合だけで、その手順は `realmachine-verification` が正本。
---

# リリース前の実機検証の準備（インストールと手順書）

**人手検証は「インストールまでを Claude Code、検証の操作をユーザー」で分担する**
（2026-09-23のユーザー判断）。実機の操作とスクリーンショットの読み込みは会話のコンテキストを
最も消費するため、Claude Code はインストールと手順書の作成で止める。

- Claude Code が adb で UI を操作して検証するのは、ユーザーが明示的に「操作まで任せる」と
  依頼した場合だけ。その手順は Skill `realmachine-verification` が正本。
- adb 接続そのものの落とし穴（サンドボックス、mDNS、`ANDROID_SERIAL`）は
  Skill `adb-device-connection` が正本。本スキルでは読み込まず、下の定石だけで足りる。
  繋がらないときに限り同スキルを読む。

## 1. 手順書のファイル

| 項目 | 内容 |
| --- | --- |
| 置き場所 | `docs/verification/VERIFICATION-<バージョン>.local.md` |
| バージョン | 次にリリースする版。`docs/RELEASE_NOTES.md` の先頭にある「（未リリース）」の見出しの番号。無ければ `scripts/version.properties` の `VERSION_NAME` のマイナー版を1つ上げた番号を仮に置き、報告で伝える |
| Git | 管理外（`.gitignore` の `*.local.md`）。端末のシリアルや検証時の状態を書くため |
| 単位 | **1リリース1ファイル**。BLごとに節を追加し、同じリリースの検証項目を1か所へ集める |
| 雛形 | 本スキルの `template.md`。ファイルが無ければ雛形から作り、あれば「3. 検証項目」へ節を足す |

**そのリリースが Google Play の両トラック（電話・タブレット系 / Wear OS 専用）で公開中になった時点で
削除する**（手順は第4節）。結果は `DESIGN.md` / `BACKLOG.md` へ記録済みで、残しておくと次のリリースの
手順書と取り違えるため。

## 2. 手順

### 2.1 対象を決める

1. 現在のブランチを確認する（`git branch --show-current`）。ユーザーの指定が無ければ現ブランチの
   ビルドを入れる。
2. `docs/records/managed/BACKLOG.md` から、今回検証する `区分: 人手検証` の項目を選ぶ
   （ユーザーが指定したBL、または現ブランチの変更に対応するBL）。`タスク内容` と `完了条件` が
   手順書の検証項目の元になる。
3. 変更の詳細は `EXECUTE.md` の該当記録と `git log main..HEAD` で把握する。コードを読み直すのは、
   期待結果（表示文言・ログの書式など）を確かめる必要がある箇所だけにする。

### 2.2 端末へ接続する

adb を使うコマンドは**すべて `dangerouslyDisableSandbox: true` で実行する**
（サンドボックス下では adb サーバーが呼び出しごとに消える）。

```bash
adb devices -l
# ウォッチが出ないとき（ペアリング済みなら mDNS で現れる。12秒待つ）
adb kill-server; ADB_MDNS_OPENSCREEN=1 adb start-server; sleep 12; adb devices -l
```

- スマホ（`model:Pixel_8_Pro`）は USB 接続のシリアル（例 `39181FDJG008MY`）を使う。
  USB で出ていなければ、まず USB 接続を依頼する。
- ウォッチ（`model:Google_Pixel_Watch_2`）は `adb-<シリアル>-xxxxxx._adb-tls-connect._tcp` の形で現れる。
  出ないときは画面を点けてもらう。それでも出なければ `adb-device-connection` を読む。

### 2.3 インストールする

**ウォッチを先に入れる**（画面消灯で切れるため）。`ANDROID_SERIAL` とGradleは同じコマンドで渡す。

```bash
ANDROID_SERIAL=<ウォッチのシリアル> ./gradlew :wear:installDebug --console=plain -q
ANDROID_SERIAL=<スマホのシリアル>  ./gradlew :mobile:installDebug --console=plain -q
```

- デバッグ版は `com.sesamiwear.mobile.debug`（「Sesami Wear (debug)」）として Play 版と併存する。
  **Play 版はアンインストールしない**（資格情報が消える）。
- モック API で検証する項目がある場合だけ `-PsesameApiBaseUrl=...` を付ける
  （`realmachine-verification` 第3節）。付けなければ本番 API に接続する。
- 入ったことを確認する（「BUILD SUCCESSFUL」だけで報告しない）。

  ```bash
  for s in <スマホ> <ウォッチ>; do adb -s $s shell dumpsys package com.sesamiwear.mobile.debug \
    | grep -E "versionCode|lastUpdateTime" | head -2; done
  ```

  スマホが `versionCode=1` 系、ウォッチが `1001` 系で、`lastUpdateTime` が今回の時刻であること。

### 2.4 前提をテキストで確認する（画像を使わない）

手順書の「事前準備」を正しく書くために、スマホのデバッグ版の状態を**テキストだけで**確かめる。
スクリーンショットは撮らない。

```bash
P=com.sesamiwear.mobile.debug; S=<スマホ>
adb -s $S shell run-as $P ls shared_prefs                      # 資格情報・割り当ての有無
adb -s $S shell dumpsys package $P | grep -oE "android.permission.(BLUETOOTH_(SCAN|CONNECT)|POST_NOTIFICATIONS): granted=[a-z]+" | sort -u
adb -s $S shell run-as $P cat shared_prefs/sesami_wear_ble_reachability.xml | grep route_policy
adb -s $S shell run-as $P cat shared_prefs/sesami_wear_api_usage.xml | grep -oE 'api_usage_count">[0-9]+'
```

- **出力に uuid・BLEアドレスが含まれても、手順書・記録・応答へ書き写さない**
  （`grep` で必要なキーだけに絞る）。
- 資格情報の中身は読まない（暗号化されており、読む必要も無い）。

### 2.5 手順書を書く

1. ファイルが無ければ `template.md` を `docs/verification/VERIFICATION-<バージョン>.local.md` へ写し、
   「0. インストール状況」「1. 事前準備」を今回の結果で埋める。
2. 「3. 検証項目」へ BL ごとの節（`### BL-xxx <件名>`）を足す。項目は表で、
   **操作・期待結果・結果（空欄）** の3列にする。期待結果には、画面で分かることに加えて、
   判定に使えるログ・保存値（例 `route=BLE op=STATUS ... result=OK`、API呼び出し回数が増えない）を書く。
3. 既存の節がある BL を再検証する場合は、節を書き換えず、結果欄を空に戻したうえで
   インストール状況の日時を更新する。
4. **実際に鍵が動く操作**（状態チップのタップ等）には、その旨を項目内に明記する。
5. 手順書は Markdown 静的解析の対象外（`*.local.md`）だが、体裁は他の文書に揃える。

### 2.6 報告する

- インストール結果（端末・versionCode・更新時刻）と手順書のパスを伝える。
- 検証後は「BL-xxx の結果を記録して」と伝えてもらえばよいことを添える。
- **ここで止める。** 検証の操作や、結果の予想による記録の更新はしない。

## 3. 結果の記録を依頼されたら

ユーザーが手順書の結果欄を埋めて記録を依頼したら、手順書を読み、次のとおり更新する
（書式は Skill `guardrail-records-update` と `docs/records/spec/FORMAT.md` が正本）。

- 全項目が○: `BACKLOG.md` の該当項目を削除し、`DESIGN.md` の該当機能の節へ
  `実機検証（BL-xxx、日付、機種）` として要点を追記する。コード修正を伴わないため `EXECUTE.md` は更新せず、
  `CHANGELOG.md` も不要（記録ファイルだけの変更）。
- ×・未実施がある: `BACKLOG.md` の項目を残して `状態: 進行中` とし、`完了条件` の末尾へ
  何が未確認か・何が失敗したかを追記する。不具合なら新しい BL を起票する。
- ユーザーが実施した検証であることを明記する（Claude Code がadbで確認した検証と区別する）。

## 4. リリース時の削除

ユーザーから「両トラックで公開中になった」と伝えられたら（配信のBLを閉じる・タグを作るのと同じ契機）、
そのバージョンの手順書を削除する。削除の前に次を確かめる。

1. 手順書の各節の BL が `BACKLOG.md` に `区分: 人手検証` で残っていないか。残っていれば結果が
   未記録のため、第3節の記録を先に済ませるか、ユーザーへ確認する。
2. 結果欄が空の項目があれば、その節の BL が記録済み（BACKLOG から削除済み）かを確かめ、
   報告で「空欄のまま削除した項目」として伝える。未記録なら削除せずユーザーへ確認する。
3. 次のリリースの手順書（別バージョンのファイル）は削除しない。

削除は `rm docs/verification/VERIFICATION-<バージョン>.local.md` で行う。Git管理外のため差分には現れず、
記録（`CHANGELOG.md` 等）への追記も不要。報告で削除したファイル名を伝える。

## やってはいけないこと

- ユーザーの明示的な依頼なしに、adb で UI を操作して検証を進める・スクリーンショットを撮る。
- 手順書を BL ごとの別ファイルに分ける（リリース単位の1ファイルへ集める）。
- 手順書・記録・応答へ uuid・BLEアドレス・資格情報を書き写す。
- Play 版をアンインストールする、`ANDROID_SERIAL` を付けずにインストールする。
- インストール結果を `lastUpdateTime` で確かめずに報告する。
- 結果が未記録の BL を含む手順書を、確認せずに削除する。

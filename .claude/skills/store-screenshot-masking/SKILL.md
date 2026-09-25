---
name: store-screenshot-masking
description: ユーザーが実機（Pixel 8 Pro）で撮ったGoogle Play掲載用のスクリーンショットから、実機の状態が写る部分（ステータスバーの時刻・通知・電池、ドックのアプリ、壁紙、ホーム画面のほかのアイコン）を隠し、掲載サイズ1350x2400（9:16、帯色 `#1E3A5F`）へ整えて `docs/store/images/screenshots/` へ保存する手順。元画像の退避先（Git管理外の `captures/`）、ステータスバーの高さを `dumpsys window` から取る方法、残す枠（ウィジェット・ダイアログ）の境界を色の連続区間で求める方法、同梱スクリプト `mask_screenshot.py` の使い方、縮小版での確認、Git Bashで `adb shell` のパスが書き換わる事故の回避を含む。「スクショにモザイクを掛けて」「ステータスバーとドックをぼかして」「壁紙もぼかして」「実機で撮ったスクショを掲載用に加工して」等の依頼で参照する。エミュレータでの撮影と撮影後のドキュメント更新は `store-screenshot-capture`、撮影用の状態を作る撮影モードは README.md「撮影モード（デバッグ版のみ）」が正本で、本スキルは撮った後の加工だけを扱う。
---

# 実機スクリーンショットの掲載用加工（モザイク・ぼかし・9:16化）

ホーム画面ウィジェットのように**実機でしか撮れない画面**は、ユーザーが実機で撮り、Claude Code が
実機の状態が写る部分を隠してから掲載用に保存する（2026-09-26、BL-198でユーザーが決定）。
撮影する画面の状態はデバッグ版の撮影モードで作るため、デバイス名・施錠状態は見本（玄関・勝手口・
ガレージ）で、隠す必要があるのは**端末そのものの状態**だけになる。

| 隠す部分 | 方法 | 理由 |
| --- | --- | --- |
| ステータスバー（時刻・通知アイコン・電池） | モザイク | 利用者の通知・端末の状態が写る |
| ドック（最下段のアプリ列と検索バー）とナビゲーションバー | モザイク | インストール済みアプリが写る |
| 壁紙・ホーム画面のほかのアイコン | 残す枠の外をぼかす | 壁紙が私物の写真のことがある。ランチャーのアプリ名に「(debug)」が出る |

アプリ内の画面（カード一覧・メニュー等）は壁紙が写らないため、ステータスバーのモザイクだけでよい
（`--no-blur`）。ただし**デバッグ版のアプリ内の画面は撮影モードの表示（「📷 撮影モード」、メニューの
「撮影モード」）が写るため、掲載用には使わない**（アプリ内の画面はエミュレータで撮る。
`store-screenshot-capture`）。

## 1. 元画像を受け取る

ユーザーの撮り方は3通りある。どれでも、**元画像はGit管理外の `captures/`（`.gitignore` 済み）へ
`<掲載名>.raw.png` として置く**。元画像には実機のステータスバー・ドックがそのまま写っている。

- **掲載用のパスへ直接保存された**（BL-198で実際に起きた）: `git status` で `docs/store/images/screenshots/`
  の変更として見える。加工の前に `captures/` へ退避する。

  ```bash
  mkdir -p captures
  cp docs/store/images/screenshots/phone_1_widget_locked.png captures/phone_1_widget_locked.raw.png
  ```

- **端末のボタンで撮った**: 端末の `/sdcard/Pictures/Screenshots/` の最新を取り出す。
- **adbで撮った**: `captures/` へ直接 `pull` してもらう。

adb は `dangerouslyDisableSandbox: true` で実行する（`adb-device-connection`）。**Git Bashでは
`adb shell` へ渡す `/sdcard/...` が `C:/Program Files/Git/sdcard/...` に書き換わる**ため、
`MSYS_NO_PATHCONV=1` を付ける。スマホはUSBでなくWi-Fiで繋がっていることがあり、そのときの
シリアルは `adb-39181FDJG008MY-xxxxxx._adb-tls-connect._tcp` の形になる（`adb devices -l` で確かめる）。

```bash
export MSYS_NO_PATHCONV=1; S=<スマホのシリアル>
adb -s $S shell ls -lt /sdcard/Pictures/Screenshots/ | head -3
adb -s $S pull /sdcard/Pictures/Screenshots/<ファイル名>.png captures/phone_1_widget_locked.raw.png
```

## 2. 隠す範囲を決める

### 2.1 ステータスバーとドック

ステータスバーの高さは画像を読まずに端末から取る。

```bash
adb -s $S shell "dumpsys window | grep -E 'type=(statusBars|navigationBars)' | head -2 | cut -c1-120"
# statusBars frame=[0,0][1344,151]      → --status-bar 151
# navigationBars frame=[0,2920][1344,2992]
```

ドックの上端はナビゲーションバーより上にあり、`dumpsys` では取れない。**縮小版（1/4）を1枚だけ読んで**
目で決める（縮小版の座標×4）。ページ送りの点（ドックのすぐ上）より下を目安にする。

| 端末 | 元画像 | `--status-bar` | `--dock-top` |
| --- | --- | --- | --- |
| Pixel 8 Pro（2026-09-26） | 1344x2992 | 151 | 2700 |

### 2.2 残す枠（ウィジェット・ダイアログ）

縮小版でおおよその位置を見たら、**正確な境界はスクリプトの `regions` で色の連続区間から求める**。
枠の背景色を採る点（`--at`）を枠の内側の縁寄りに取り、枠を横切る行と列を指定する。

```bash
S=.claude/skills/store-screenshot-masking/mask_screenshot.py
python $S regions captures/phone_1_widget_locked.raw.png --at 50,650 --row 900 --col 50 --col 1300
# row 900: [(36, 59), (348, 365), (1284, 1307)]   → 枠の左右は36〜1307付近
# col 50: [(612, 1357)]                          → 角丸のため列によって上下が縮む
```

- 角丸の枠は、角に近い列ほど区間が短い。枠の中央寄りの列の値を採り、4〜8px外側へ広げて `--keep` にする。
- ウィジェットの背景（`(18,18,18)`）と2x1ウィジェットの背景は色が違うことがある。枠ごとに `--at` を採り直す。
- 同じ配置で状態だけ変えた複数枚は、`PIL.ImageChops.difference` の `getbbox()` で差分が枠の中に
  収まることを確かめれば、同じ `--keep` を使い回せる（画像を読む必要が無い）。
- ダイアログの背後のウィジェットは暗く表示されるが、枠として残す（ぼかすとダイアログの文脈が消える）。

BL-198（2026-09-26、Pixel 8 Pro、ホーム画面の配置は2x1を右上・4x2をその下）で使った値:

| 枠 | `--keep` |
| --- | --- |
| 4x2ウィジェット | `32,594,1312,1376` |
| 2x1ウィジェット | `692,192,1312,544` |
| 解錠確認ダイアログ | `236,1345,1108,1724` |

## 3. 加工する

```bash
SP=<スクラッチパッドのディレクトリ>
python $S mask captures/phone_1_widget_locked.raw.png docs/store/images/screenshots/phone_1_widget_locked.png \
  --status-bar 151 --dock-top 2700 --keep 32,594,1312,1376 --keep 692,192,1312,544 \
  --preview $SP/phone_1_s.png
```

スクリプトがすること:

1. `--keep` の枠（角丸、半径48px）の外をガウスぼかし（半径40）する。この強さでランチャーの
   アプリ名（「Sesami Wear (debug)」）も読めなくなる。
2. ステータスバー（上端〜`--status-bar`）と、`--dock-top` から下端までを36pxのマスでモザイクにする。
3. 高さ2400へ縮小し、左右へ帯（`#1E3A5F`）を足して1350x2400（9:16）にする。
   既存のエミュレータ撮影分（1080x2400由来）と同じサイズになる。

## 4. 確かめる

- `--preview` の縮小版（270x480）を**1枚ずつ**読み、残す枠が欠けていないか・ステータスバーとドックの
  文字が読めないか・ぼかしの外にアプリ名が残っていないかを見る。フル解像度の画像は読まない
  （`verification-cost-control`）。同じ `--keep` を使った残りの枚数は、1枚を確認したら読まなくてよい。
- `git diff --stat docs/store/images/screenshots/` で、差し替えたファイルだけが変わっていることを見る。

## 5. 後始末と記録

- `captures/` の元画像は、加工の後はユーザーに確認して削除する（Git管理外だが実機の状態が写っている）。
- ドキュメントの更新（`docs/store/README.md` の内訳と撮影方法、`STORE_LISTING.md`、`CHANGELOG.md`）は
  `store-screenshot-capture` 第6節に従う。モザイク・ぼかしを掛けた画像はその旨を内訳に書く。

## やってはいけないこと

- 元画像を `docs/store/images/screenshots/` に残したままコミットする（実機のステータスバー・ドックが写る）。
- 加工前の元画像を退避せずに上書きする（範囲を直して加工し直せなくなる）。
- 残す枠の座標を縮小版の目測だけで決める（角丸の欠けや、ぼかしの滲み込みが出る）。
- デバッグ版のアプリ内の画面を掲載用に撮る（撮影モードの表示が写る）。

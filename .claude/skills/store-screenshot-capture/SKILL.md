---
name: store-screenshot-capture
description: Google Play ストア掲載用のスクリーンショットを、実機ではなく Android エミュレータで撮影して `docs/store/images/screenshots/` へ反映するための手順。エミュレータの起動とウィンドウ位置の矯正、ステータスバーを時刻と電池だけに整える SystemUI デモモード、ダミー資格情報とモック API で施錠/解錠の表示を作る手順、`input text` が文字を落とす原因（スタイラス手書きのチュートリアル割り込み）とその無効化、Pixel Launcher の ANR への対処、Play Console の比率要件に合わせる 9:16 レターボックス加工（帯色 `#1E3A5F`）、撮影後のドキュメント更新と後始末までを含む。「ストアのスクショを撮り直して」「掲載画像を更新して」「スクリーンショットをエミュレータで撮って」「アセット画像が古い」等、掲載アセットの更新を伴う依頼で参照する。実機を使う検証そのものは `realmachine-verification`、adb 接続の落とし穴は `adb-device-connection` が正本で、本スキルは掲載用スクリーンショットの撮影と加工だけを扱う。
---

# ストア掲載スクリーンショットの撮影手順（エミュレータ）

Google Play のスマートフォン用スクリーンショットは、**実機ではなくエミュレータで撮る**のを既定とする
（2026-09-18 にユーザーが決定）。実機は Play 版と実資格情報が入っており、撮影のために状態を
作り替えられない。エミュレータなら実 Sesame・実資格情報を使わずに任意の状態を作れ、
デバイス名のモザイク処理も不要になる。

Wear OS 用は Wear のシステムイメージが入っていないためエミュレータでは撮れない。ウォッチ側の
表示を変えたときだけ Pixel Watch 2 の実機で撮る（`realmachine-verification` の第4節）。

## 0. 撮り直しの要否を先に判定する

**全点を撮り直す前に、変更が表示へ影響したかを差分で確かめる。** 影響しない画面は撮り直さない
（Wear OS 用6点は 2026-09-18 時点でこの判定により据え置いた）。

```bash
# 前回の撮影コミット以降に、その画面を描くファイルが変わったか
git log --oneline --since=<前回撮影日> --name-only -- mobile/src/main/ wear/src/main/ | grep -E "\.kt$" | sort -u
```

- リファクタ（文言・色の移設など）は表示不変のことが多い。**文字列リテラルを抽出して突き合わせる**と
  確証が得られる。

  ```bash
  git show <commit>^:<旧ファイル> | grep -oE '"[^"]{2,}"' | sort -u > /tmp/old.txt
  grep -rhoE '"[^"]{2,}"' <新ファイル群> | sort -u > /tmp/new.txt
  comm -23 /tmp/old.txt /tmp/new.txt   # 空なら文言は不変
  ```

- 新機能を追加したリリースでは、**その機能の画面が1枚も無いこと**が最大の欠落になりやすい
  （0.11.0 ではウィジェットの画像が無いまま掲載文だけ先に更新されていた）。

## 1. エミュレータを起動する

AVD は `nocompanion`（Pixel 6 相当、1080x2400、Android 15）を流用する。作り方は
`docs/INSTALL.md`「1.7」が正本。

```bash
/c/Android/sdk/emulator/emulator.exe -avd nocompanion -no-boot-anim -gpu swiftshader_indirect
```

- **`$TMPDIR` は空になることがある。** リダイレクト先はスクラッチパッドの絶対パスで書く。
- 起動待ちは until ループをバックグラウンドで回す。

  ```bash
  until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 5; done
  ```

- **ウィンドウが画面外へ出ることがある。** 利用者が画面を見られないので、中央へ寄せる。

  ```powershell
  Add-Type -AssemblyName System.Windows.Forms
  # user32.dll の GetWindowRect / MoveWindow を P/Invoke し、ProcessName -match 'qemu' の
  # MainWindowHandle を作業領域の中央へ移動する（高さは作業領域 - 40 に丸める）
  ```

- **ウォッチや実機が同時に繋がっていることがある。** 以降のコマンドは必ず `-s emulator-5554` を付ける。

## 2. 撮影前に端末を整える

```bash
E="adb -s emulator-5554"
# ステータスバーを時刻と電池だけにする
$E shell settings put global sysui_demo_allowed 1
$E shell am broadcast -a com.android.systemui.demo -e command enter
$E shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
$E shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
$E shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# スタイラス手書きのチュートリアルが入力へ割り込むのを止める（第3節）
$E shell settings put secure stylus_handwriting_enabled 0
```

## 3. ダミー資格情報を入れる（`input text` の落とし穴）

デバッグ版をモック API 付きで入れる。`10.0.2.2` がホストPCを指す。

```bash
python scripts/mock-sesame-api.py &
ANDROID_SERIAL=emulator-5554 ./gradlew :mobile:installDebug -PsesameApiBaseUrl=http://10.0.2.2:8080/api/sesame2
```

入力時に実際に踏んだ問題と対処:

- **スタイラスのチュートリアル（"Try out your stylus"）が入力中に割り込み、文字が途中で落ちる。**
  `Front Door` が `Fr`、uuid が `11111` で止まる形で現れた。第2節の
  `stylus_handwriting_enabled 0` を**入力を始める前に**設定する。踏んだ場合は `pm clear` して
  最初からやり直す（中途半端に入った値を消すより速い）。
- **`input text` に日本語は入らない。** 表示名は ASCII にする。`Entrance` / `Garage` のように
  **スペースを含まない1単語**にすると、`%s` のエスケープ（`Front%sDoor`）も不要で確実。
- **1フィールドずつ、間に端末側の `sleep` を挟む。** ホスト側の `sleep` は使えないため
  `$E shell "sleep 1"` と書く。まとめて送ると取りこぼす。
- **入力のたびにスクリーンショットで実際に入った文字列を確認する。** uuid はモックのログと
  突き合わせる必要があるので、実際に入った文字列を採用する。
- **キーボードが出るとフォームが上へ詰まる。** 座標は毎回スクリーンショットから測り直す
  （縮小して確認したときは、縮小率で割って実座標に戻す）。

## 4. 画面を撮る

```bash
adb -s emulator-5554 exec-out screencap -p > shots/<名前>.png
```

- **swiftshader のエミュレータは重く、Pixel Launcher が ANR を出す。** 「Pixel Launcher isn't
  responding」が出たら `Wait` をタップし、以降の操作の間隔を広げる。撮影対象の画面には影響しない。
- **アプリ起動直後はスプラッシュが出る。** `am force-stop` → 起動のあと 8〜10 秒待ってから撮る。
  フォーカス枠（直前に触った入力欄の紫枠）を消したいときも、この再起動が最も確実。
- **ウィジェットは未設定のものが残っていればタップするだけで設定画面が開く。** 新規に置く場合は
  ホーム長押し → Widgets → ドラッグ＆ドロップ（`input draganddrop`）。タップでは置けない。
- **状態表示（施錠中/解錠中）はモック API で作る。** デバイス名部分のタップで状態取得、
  右側のタップで施錠/解錠。モックのログ（`GET ... -> locked` / `POST ... cmd=83 -> unlocked`）が
  撮影内容の裏付けになる。
- **デバッグ版でも `(debug)` は掲載画像に写らない。** `app_name` / `widget_label` の上書きが出るのは
  ランチャーとウィジェットピッカーだけで、アプリ内画面とウィジェット本体には出ない。
  **ランチャーとウィジェットピッカーは撮らない。**
- ホーム画面を撮ると Google 製アプリのアイコンとドックが写る。ウィジェットを見せる以上避けられず、
  掲載上の問題も無いのでそのまま使う。
- **撮った画像を確認のために読むときは、切り出し・縮小してから読む。** 掲載用に保存するファイルは
  フル解像度のままでよいが、エージェントが「意図した画面が撮れているか」を見るだけなら
  縮小版で足りる。画像は会話の履歴に残って以降のやり取りで再送されるため、利用量に直結する
  （`realmachine-verification` 第8節）。

## 5. Play Console の比率へ加工する

Play Console は縦横比に上限があり、1080x2400（9:20）のままでは弾かれうる。**内容を切らずに
左右へ背景色の帯を足して 9:16 にする。**

```python
BAND = (30, 58, 95)                    # #1E3A5F。既存の掲載画像と同じ帯色
tw = round(h * 9 / 16)                 # 2400 -> 1350
canvas = Image.new("RGB", (tw, h), BAND)
canvas.paste(im, ((tw - w) // 2, 0))
```

- 保存先は `docs/store/images/screenshots/`。ファイル名の番号が **Play Console へ投入する順序**に
  なるので、訴求したい順（新機能 → 操作 → 設定 → 補助画面）に振る。
- **差し替えで不要になった旧ファイルは削除する。** Play Console 側も旧画像を消してから入れ直す。

## 6. 撮影後にやること

- `docs/store/README.md` の内訳表（ファイル名・内容・サイズ）と、撮影方法・撮影日の記述を更新する。
  モザイクの有無や撮影環境が変わったら、その説明も書き換える。
- `docs/store/STORE_LISTING.md`「スクリーンショット」の枚数・撮影環境を更新する。
- `BACKLOG.md` の配信タスクへ「掲載アセットの差し替えも Play Console の作業に含む」ことを追記する。
  画像を更新しただけでは掲載は変わらない。
- `CHANGELOG.md` へ記録する（コード修正を伴わないため `EXECUTE.md` は更新しない）。
- 後始末: モックサーバーを停止し、エミュレータを落とす（`adb -s emulator-5554 emu kill`）。
  デバッグ版を実機へ入れていた場合は `pm uninstall com.sesamiwear.mobile.debug`（**Play 版
  `com.sesamiwear.mobile` は消さない**）。ウォッチに Tile を追加していたら
  `--es operation remove-tile` で外してからアンインストールする。

## やってはいけないこと

- 変更が表示へ影響したか確かめずに、全点を撮り直す（あるいは古いまま放置する）。
- 実機の Play 版で撮ろうとする（実資格情報・実デバイス名が写り、操作すると実際の鍵が動く）。
- ランチャーやウィジェットピッカーを撮る（`Sesami Wear (debug)` が写る）。
- `input text` の結果を確認せずに次のフィールドへ進む。
- 1080x2400 のまま Play Console へ投入する（比率で弾かれうる）。
- 画像だけ差し替えて `docs/store/README.md` の内訳表を古いままにする。
- 撮影後にモックサーバー・エミュレータ・デバッグ版を残したまま完了報告する。
- 確認のためだけにフル解像度の画像を読む（縮小版で足りる）。

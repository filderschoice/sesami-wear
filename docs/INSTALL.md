# アプリのインストール方法（スマホ・スマートウォッチ）

本ドキュメントは、`mobile`（スマホ側）と`wear`（スマートウォッチ側）のアプリを実機へインストールする
手順をまとめたものです。`mobile`と`wear`は同一の`applicationId`（`com.sesamiwear.mobile`）を共有する
**独立した2つのアプリ**で、それぞれ別のAndroid App Bundle（AAB）としてビルドします（BL-090。
[docs/records/managed/DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）。
Google Play経由の配布では、標準的なWear OSアプリの配布方式（スマホへのインストール後、
ペアリング済みのWatchへウォッチ用アプリが自動的にプッシュインストールされる）が利用できます
（2成果物・2トラック構成での動作を2026-09-06に実機で確認済み）。

> ⚠️ `applicationId`が同一のため、**1台のデバイスにスマホ用とウォッチ用の両方は入りません**。
> 後からインストールした方が前のものを置き換えます。ローカルビルドでは、デバイスの種別に応じて
> 実行するGradleタスクを使い分けてください。

## 現状

このアプリはGoogle Playの限定公開（クローズドテスト）で配信しており、一般公開はまだ行っていません。
利用できる経路は次の2つです。

| 立場 | 経路 |
| --- | --- |
| テストへ参加したテスター | [2. Google Play経由](#2-テスター向けgoogle-play経由のインストール) |
| 開発者・検証者、テスターに参加しない方 | [1. ローカルビルド](#1-開発者検証者向けローカルビルドを実機にインストールする) |

配布状況の正本は [SUPPORT.md](SUPPORT.md)「現在の配布状況」です。テストへの参加手順は
[CLOSED_TEST.md](CLOSED_TEST.md) にまとめています。

インストール後の使い方（資格情報の登録、タイル・コンプリケーションの設定、施錠/解錠の操作）は
[USER_GUIDE.md](USER_GUIDE.md) を参照してください。

## 1. 開発者・検証者向け：ローカルビルドを実機にインストールする

### 1.1 前提条件

- リポジトリのビルド環境が整っていること
  （[README.md](../README.md)「前提環境」「セットアップ手順」参照。JDK 17、Android SDK等）
- スマホとスマートウォッチ（Pixel Watch等）が、スマホ側のWear OS companion appで
  Bluetoothペアリング済みであること
- スマホとの接続用USBケーブル

### 1.2 スマホ側の開発者向けオプションを有効化する

1. 設定 → デバイス情報 → ビルド番号を7回タップ（機種により文言が異なる場合があります）
2. 設定 → システム → 開発者向けオプション → 「USBデバッグ」をON

### 1.3 スマートウォッチ側の開発者向けオプションを有効化する

> 以下はWear OS一般の公開手順に基づく記述です。2026-09-05にPixel Watch 2（Wear OS）で本手順により
> Wi-Fi経由のADB接続とインストールに成功していますが、Wear OSのバージョンや機種により文言・手順が
> 異なる場合があります。

1. スマートウォッチの設定 → デバイス情報（または「バージョン情報」）→ ビルド番号を7回タップ
2. スマートウォッチの設定 → 開発者向けオプション → 「ADBデバッグ」をON
3. 同じく開発者向けオプション → 「Wi-Fi経由のデバッグ」をON
   （スマートウォッチとPCが同一Wi-Fiネットワークに接続されている必要があります）
4. 「Wi-Fi経由のデバッグ」画面に、接続用のIPアドレスとポート番号が表示される

### 1.4 ADBでスマートウォッチへ接続する

Wi-Fi経由のデバッグでは、**ペア設定用のポートと接続用のポートは別番号**です。PCとのADBペアリングが
未確立の状態では、ネットワーク的に到達できていても `adb connect` は `failed to connect` で失敗します。
初回は必ず `adb pair` から実行してください（ペアリングはデバイスごとに一度成立すれば以降は不要です）。

ペア設定用のIPアドレス:ポートと6桁のペア設定コードは、「Wi-Fi経由のデバッグ」画面の
「新しいデバイスとペア設定」に表示されます。コードはこの画面を閉じると無効になるため、表示したまま
実行してください。

```bash
# 初回のみ（ペア設定画面に表示された値を使う）
adb pair <IPアドレス>:<ペア設定用ポート> <6桁のペア設定コード>

# 接続（「Wi-Fi経由のデバッグ」画面トップに表示されるポートを使う）
adb connect <IPアドレス>:<接続用ポート>
adb devices -l
```

スマホとスマートウォッチの両方が `adb devices` の一覧に表示されることを確認してください。`-l` を
付けると `model:` / `product:` が併記され、どのデバイスIDがどちらの端末かを判別できます。

```text
192.168.1.105:37515  device product:aurora model:Google_Pixel_Watch_2 device:aurora
192.168.1.149:41235  device product:husky  model:Pixel_8_Pro          device:husky
```

スマホ側もWi-Fi経由で接続する場合は同じ手順です（USB接続の場合はケーブル接続とUSBデバッグの許可のみで
`adb devices` に現れます）。

### 1.5 アプリをビルド・インストールする

インストール先のデバイスに応じて、実行するタスクが異なります。

| インストール先 | Gradleタスク |
| --- | --- |
| スマートフォン | `:mobile:installDebug` |
| スマートウォッチ | `:wear:installDebug` |

接続しているデバイスが1台だけの場合は、そのまま実行できます。

```bash
./gradlew :mobile:installDebug   # スマホのみ接続時
./gradlew :wear:installDebug     # スマートウォッチのみ接続時
```

スマホとスマートウォッチを同時に接続している場合は、`ANDROID_SERIAL` 環境変数でインストール先を
1台に固定し、デバイスごとに実行します。デバイスIDは `adb devices` の出力の1列目
（`List of devices attached` の下に並ぶ識別子。Wi-Fi接続なら `IPアドレス:ポート` そのもの）です。

```bash
# スマホへ
ANDROID_SERIAL=192.168.1.149:41235 ./gradlew :mobile:installDebug
# スマートウォッチへ
ANDROID_SERIAL=192.168.1.105:37515 ./gradlew :wear:installDebug
```

Windows PowerShellでは、環境変数の設定とビルドを同一コマンドで実行してください
（別々に実行すると設定が引き継がれません）。

```powershell
$env:ANDROID_SERIAL = "192.168.1.105:37515"; .\gradlew.bat :mobile:installDebug
```

`:mobile:installDebug` はAABからデバイス構成に応じたsplit APKを生成して配信します。実行ログの
`Installing APKs '...'` 行に `wear-*.apk` が含まれていれば、`wear` モジュールが配信されています。

```text
Generating APKs for device 'Google Pixel Watch 2 - 17' for :mobile:debug
Installing APKs 'base-xhdpi.apk, base-master_2.apk, base-ja.apk, base-armeabi_v7a.apk,
                 wear-xhdpi.apk, wear-master_2.apk, wear-ja.apk' on 'Google Pixel Watch 2 - 17'
Installed on 1 device.
```

インストール後は、端末上の実際の状態でも確認できます（`lastUpdateTime` が今回の実行時刻になって
いること、`splits` に `wear` が含まれることを確認します）。

```bash
adb -s <デバイスID> shell dumpsys package com.sesamiwear.mobile
```

2026-09-05に、Pixel 8 Pro（スマホ）とPixel Watch 2（スマートウォッチ）をWi-Fi経由で2台同時接続した
状態で上記手順を実施し、双方へのインストールとウォッチ側への`wear`モジュール配信を確認しました。
Android Studioで`mobile`の実行構成を開き、実行対象デバイスを選んで「Run」する方法も同等に使えます。

コマンドラインでAPK Setを明示的に生成したい場合は、Googleの[bundletool]公式ツールで
AABからデバイス構成ごとのAPK Setを作り、`install-apks`で配信する方法もあります
（本リポジトリでは未検証、参考情報。`bundletool`は別途入手が必要です）。

```bash
./gradlew :mobile:bundleDebug
bundletool build-apks --bundle=mobile/build/outputs/bundle/debug/mobile-debug.aab --output=mobile-debug.apks
bundletool install-apks --apks=mobile-debug.apks --device-id=<スマホのデバイスID>
bundletool install-apks --apks=mobile-debug.apks --device-id=<スマートウォッチのデバイスID>
```

[bundletool]: https://developer.android.com/tools/bundletool

### 1.6 インストール後の初期設定

1. スマホ側で`mobile`アプリを起動し、資格情報（uuid / secretKey / apikey）を設定する
   （[README.md](../README.md)「セットアップ手順」参照）
2. スマートウォッチ側で`wear`アプリのTileをウォッチフェイスに追加する

各手順の詳細（デバイスの割り当て、コンプリケーションの設定、操作方法、トラブル時の確認事項）は
[USER_GUIDE.md](USER_GUIDE.md) にまとめています。

### 1.7 Wear OSコンパニオンアプリ未導入の環境で検証する（開発者向け）

`mobile`は、Wear OSコンパニオンアプリ（Pixel Watchアプリ、`com.google.android.apps.wear.companion`）が
入っていないスマホでも、資格情報の保存とホーム画面ウィジェットの操作が動作する必要があります。
コンパニオンが無い端末ではWearable Data Layer APIの呼び出しが`ApiException`（`API_UNAVAILABLE`＝17）で
失敗しうるため、`mobile.messaging.DataLayerBestEffort`が失敗を握りつぶす設計になっています
（BL-118 / BL-134）。この経路を実際に確認するための環境の作り方をまとめます。

#### 環境の作り方

| 方法 | 忠実さ | 手間 | 備考 |
| --- | --- | --- | --- |
| A. Androidエミュレータ | 高 | 中 | コンパニオンが最初から入っていない。実機の設定を変えない |
| B. 実機のコンパニオンを一時無効化 | 中 | 小 | 「無効化」は「未導入」と同一ではない。ウォッチとの接続が切れる |
| C. コンパニオン未導入の別スマホ | 最高 | 大 | 該当端末が必要 |

##### 方法A: Androidエミュレータ（推奨）

素のAndroidエミュレータにはWear OSコンパニオンアプリが含まれないため、「未導入スマホ」をそのまま
再現できます。実機の設定を変更しないので、検証後の復旧作業も不要です。

本リポジトリの開発環境には、2026-09-17にエミュレータ本体とシステムイメージを追加済みです
（`%ANDROID_HOME%\emulator`、`system-images;android-35;google_apis;x86_64`。合計約4GB、
ダウンロードに10分程度かかります）。AVD `nocompanion` も作成済みで、無ければ以下で作り直せます。

```bash
sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n nocompanion -k "system-images;android-35;google_apis;x86_64" -d pixel_6
emulator -avd nocompanion -no-snapshot -no-boot-anim -gpu swiftshader_indirect
```

- `-d pixel_6` を付けると画面が1080x2400になり、実機（Pixel 8 Pro）に近い比率で確認できます。
- `avdmanager`が出す`Could not load devices from ...\devices.xml`は無害で、AVDは作成されます。
- 初回起動（コールドブート）は数分かかります。`adb -s emulator-5554 shell getprop sys.boot_completed`
  が`1`を返すまで待ってから操作してください。

起動したら、コンパニオンが入っていないことを確認してからインストールします。

```bash
# 何も出力されなければコンパニオン未導入
adb -s emulator-5554 shell pm list packages | grep wear.companion

ANDROID_SERIAL=emulator-5554 ./gradlew :mobile:installDebug
```

Google Play services自体は素のエミュレータにも入っており、コンパニオンだけが無い状態になります。
logcatに`Wear_Controller: Wearable module requires a companion app to be installed.`と
`WearableService: Wear is not available on this device.`が出ることで確認できます。

##### 方法B: 実機のコンパニオンを一時的に無効化

すぐ試せますが、パッケージ自体は端末に残るため「未導入」と完全に同じ状態にはなりません
（Google Play services側の判定が一致するかは未確認）。**実行するとウォッチとの接続が切れます。**
検証が終わったら必ず元へ戻してください。

```bash
# 無効化
adb shell pm disable-user --user 0 com.google.android.apps.wear.companion
# 復旧（必ず実施する）
adb shell pm enable com.google.android.apps.wear.companion
```

#### 動作確認ポイント

実資格情報は使いません。ダミーの資格情報と、`-PsesameApiBaseUrl`で差し替えたモックAPI
（`scripts/mock-sesame-api.py`）で確認します（`rules/guardrails-unified.v1.md` 12.5）。
エミュレータからホストPCのモックサーバーへは`10.0.2.2`でアクセスできます。

| # | 確認内容 | 期待結果 |
| --- | --- | --- |
| 1 | アプリを起動する | クラッシュしない |
| 2 | ダミー資格情報を保存する | 落ちずに保存され、一覧へ反映される |
| 3 | 保存した資格情報を削除する | 落ちずに削除される |
| 4 | ウィジェットを追加し対象デバイスを選ぶ | 選択画面が開き、割り当てが保存される |
| 5 | ウィジェットの「更新」をタップする（状態取得。0.13.0以前はデバイス名） | 落ちず、通信中を経て表示が確定する |
| 6 | ウィジェットから施錠・解錠する（モックAPI） | 成功し、ウォッチ同期の失敗に巻き込まれない |

`-PsesameApiBaseUrl`を付けたビルドの後で品質ゲートの`./gradlew assembleDebug`を実行すると、
**接続先を差し替えたAPKが本番URLのもので上書きされます**。品質ゲートを回したら、インストール前に
必ず`-PsesameApiBaseUrl`付きで組み直してください（上書きされたAPKを入れるとモックへ1件も届かず、
原因が分かりにくくなります）。

あわせてlogcatで次の2点を確認します。

- `ApiException`のステータスコード（`17`＝`API_UNAVAILABLE`）は出てよいが、`FATAL EXCEPTION`が
  出ていないこと（`DataLayerBestEffort`が握りつぶしている証拠）
- uuid形状の文字列・32桁の16進数が1件も出力されていないこと（秘密情報の非出力確認）

2026-09-17に方法Aで全6項目を確認済みです。結果は
[DESIGN.md](records/managed/DESIGN.md)「実機検証（BL-126、2026-09-17、Androidエミュレータ）」を
参照してください。

### 1.8 Wear OSエミュレータでウォッチ側の表示を検証する（開発者向け）

ウォッチ実機が手元に無くても、Tile・コンプリケーション・状態一覧の**表示**はエミュレータで
確認できます（BL-187）。

```bash
sdkmanager "system-images;android-34;android-wear;x86_64"
avdmanager create avd -n wearos -k "system-images;android-34;android-wear;x86_64" -d wearos_small_round
emulator -avd wearos -no-boot-anim -gpu swiftshader_indirect -port 5556
```

- システムイメージは **Wear OS 5（API 34）** で、Pixel Watch 2 と同じ世代です。画面は384x384。
- AVDの既定のデータ領域は10GBで、空きが足りないと
  `Not enough space to create userdata partition`で起動に失敗します。
  `~/.android/avd/wearos.avd/config.ini`の`disk.dataPartition.size`を`2047M`以下へ下げてください
  （エミュレータが受け付ける上限は2047MB）。
- 同じファイルの`hw.lcd.circular`を`yes`にすると円形画面になり、実機と同じ見切れ方を再現できます。
- **スマートフォンのエミュレータと同時に起動しないでください。** メモリ不足で強制終了されます。

**2台のエミュレータをペア設定することはできません。** Wear OS コンパニオンアプリは Play ストア入りの
システムイメージと Google アカウントのサインインを要するためです。代わりに、デバッグビルドにだけ
含まれる受信口から、スマートフォンが同期するはずの DataItem を**ウォッチ自身へ**書き込みます。

```bash
W="adb -s emulator-5556"
$W install -r wear/build/outputs/apk/debug/wear-debug.apk   # または ANDROID_SERIAL=emulator-5556 ./gradlew :wear:installDebug

# 登録済みデバイス一覧（uuid:表示名 のカンマ区切り）
$W shell am broadcast -a com.sesamiwear.wear.debug.STATE   -n com.sesamiwear.mobile.debug/com.sesamiwear.wear.debug.SesameWearDebugReceiver   --es devices "aaaa1111:Entrance,bbbb2222:Garage"

# 1台分の状態（route は BLE / WEB_API、failure は AUTH_OR_QUOTA / COMMUNICATION）
$W shell am broadcast -a com.sesamiwear.wear.debug.STATE   -n com.sesamiwear.mobile.debug/com.sesamiwear.wear.debug.SesameWearDebugReceiver   --es uuid aaaa1111 --ez locked true --ei battery 85 --ei position 0 --es route BLE
```

- 結果は`adb -s emulator-5556 logcat -s SesameWearDebug`で確認できます。
- 注入後の`SesameDisplayUpdateRequester`の再描画要求は、タイルのホストが無い環境では
  `SecurityException`で失敗します（注入自体は成功しています）。アプリを開き直すか、
  タイルを追加し直すと反映されます。
- **Tileの対象デバイスは`tileId`ごとに保存されます。** 設定画面をUIで操作するのが難しい場合は、
  `adb shell run-as com.sesamiwear.mobile.debug`で`shared_prefs/tile_device_assignments.xml`へ
  `tile_<tileId>`のキーを直接書けます（`tileId`は`logcat -s SesameTileService`に出ます）。
  タイルを外して入れ直すと`tileId`が変わるため、割り当ても付け直しになります。
- タイルの追加・削除は次のブロードキャストで行えます。

  ```bash
  $W shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE     --es operation add-tile     --ecn component com.sesamiwear.mobile.debug/com.sesamiwear.wear.tile.SesameTileService
  ```

- **スマートフォンとペアになっていないため、Tileの状態は常に「スマホ未接続」になります。**
  経路つきの最終取得時刻（「🔗11分前」）は表示されるため、経路アイコンの確認はできます。
  施錠/解錠の実行そのものは、スマートフォンへコマンドを送れないため確認できません。

2026-09-20に、状態一覧で🔗と🌐の両方、Tileで🔗を確認済みです。結果は
[DESIGN.md](records/managed/DESIGN.md)「エミュレータ検証（BL-187、2026-09-20、Wear OS 5）」を
参照してください。

## 2. テスター向け：Google Play経由のインストール

限定公開（クローズドテスト）のため、**テストへ参加したGoogleアカウントでのみ**インストール
できます。Play Consoleが発行するテスト参加用URLを開き、テスト参加に同意してから以下の手順に
進んでください（参加手順の詳細は [CLOSED_TEST.md](CLOSED_TEST.md)）。一般公開後は、この同意
なしでインストールできるようになります。

> テスト参加用URLはPlayストアのアカウントと一致している必要があります。端末に複数のGoogle
> アカウントがある場合、ブラウザでオプトインしたアカウントとPlayストアのアクティブアカウントが
> 食い違うと、インストール時に「エラーが発生しました」と表示されます。

`mobile`と`wear`は同一の`applicationId`を共有し、1つのストア掲載ページの中で、電話・タブレット系
トラックとWear OS専用トラックへ別々に配信されます（BL-090、
[DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）。利用者から見ると1つのアプリで、
標準的なWear OSアプリの配布方式に従います。

1. スマホのGoogle Playストアで本アプリのページを開き、インストールする
2. ペアリング済みのスマートウォッチへ、ウォッチ用アプリが自動的にプッシュインストールされる
   （2026-09-06に実機で確認済み。反映まで数分かかる場合があります）
3. インストール後の初期設定は [1.6](#16-インストール後の初期設定) と同様

自動プッシュインストールが行われない場合でも、スマートウォッチのPlay Store
（Wear OS上のGoogle Playストア）から本アプリを直接検索してインストールできる想定です（未確認）。
Wear OS向けの成果物はWear OS専用トラックで公開するため、ウォッチ側のPlay Storeからは
ウォッチ用アプリが配信されます。

限定公開（内部テスト・クローズドテスト）中は、Google Play Consoleが発行するテスターリンクを開き、
テスト参加への同意を行った上で、上記と同じ手順でインストールします。

## 関連ドキュメント

- [USER_GUIDE.md](USER_GUIDE.md): インストール後の初期設定・操作方法・トラブル時の確認事項
- [SUPPORT.md](SUPPORT.md): 現在の配布状況・アップデート方法・問い合わせ窓口
- [README.md](../README.md): 前提環境・セットアップ・ビルド手順
- [docs/records/managed/DESIGN.md](records/managed/DESIGN.md): Google Play配布方式の実装詳細
- [docs/records/managed/BACKLOG.md](records/managed/BACKLOG.md): Google Play公開に関する人手検証タスク

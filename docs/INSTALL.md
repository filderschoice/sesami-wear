# アプリのインストール方法（スマホ・スマートウォッチ）

本ドキュメントは、`mobile`（スマホ側）と`wear`（スマートウォッチ側）のアプリを実機へインストールする
手順をまとめたものです。`mobile`と`wear`は同一の`applicationId`（`com.sesamiwear.mobile`）を共有する
**独立した2つのアプリ**で、それぞれ別のAndroid App Bundle（AAB）としてビルドします（BL-090。
[docs/records/managed/DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）。
Google Play経由の配布では、標準的なWear OSアプリの配布方式（スマホへのインストール後、
ペアリング済みのWatchへウォッチ用アプリが自動的にプッシュインストールされる）が利用できる見込みです
（**未確認**: 実機・Play Console経由での動作確認はBL-038、人手検証）。

> ⚠️ `applicationId`が同一のため、**1台のデバイスにスマホ用とウォッチ用の両方は入りません**。
> 後からインストールした方が前のものを置き換えます。ローカルビルドでは、デバイスの種別に応じて
> 実行するGradleタスクを使い分けてください。

## 現状（2026-09-06時点）

このアプリはGoogle Playの内部テスト（限定公開）で配信中で、一般公開はまだ行っていません。
利用できる経路は次の2つです。

| 立場 | 経路 |
| --- | --- |
| 登録済みのテスター | [2. Google Play経由](#2-テスター向けgoogle-play経由のインストール) |
| 開発者・検証者、テスター以外 | [1. ローカルビルド](#1-開発者検証者向けローカルビルドを実機にインストールする) |

最新の配布状況は [SUPPORT.md](SUPPORT.md)「現在の配布状況」でも確認できます。

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

## 2. テスター向け：Google Play経由のインストール

内部テスト（限定公開）のため、**テスターとして登録されたGoogleアカウントでのみ**インストール
できます。Play Consoleが発行するテスト参加用URLを開き、テスト参加に同意してから以下の手順に
進んでください。一般公開後は、この同意なしでインストールできるようになります。

> テスト参加用URLはPlayストアのアカウントと一致している必要があります。端末に複数のGoogle
> アカウントがある場合、ブラウザでオプトインしたアカウントとPlayストアのアクティブアカウントが
> 食い違うと、インストール時に「エラーが発生しました」と表示されます。

`mobile`と`wear`は同一の`applicationId`を共有し、1つのストア掲載ページの中で、電話・タブレット系
トラックとWear OS専用トラックへ別々に配信されます（BL-090、
[DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）。利用者から見ると1つのアプリで、
標準的なWear OSアプリの配布方式に従います。

1. スマホのGoogle Playストアで本アプリのページを開き、インストールする
2. ペアリング済みのスマートウォッチへ、ウォッチ用アプリが自動的にプッシュインストールされる見込み
   （**未確認**: 実機・Play Console経由での動作確認はBL-038、人手検証）
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
- [docs/records/managed/BACKLOG.md](records/managed/BACKLOG.md):
  BL-034（Google Play限定公開の人手検証）、BL-038（Google Play経由の自動プッシュインストール確認）

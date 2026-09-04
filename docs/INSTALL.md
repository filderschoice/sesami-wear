# アプリのインストール方法（スマホ・スマートウォッチ）

本ドキュメントは、`mobile`（スマホ側）と`wear`（スマートウォッチ側）のアプリを実機へインストールする
手順をまとめたものです。`wear`は`mobile`のdynamic feature（BL-036）として統合されており、
`applicationId`・署名・バージョンは`mobile`から継承する単一のAndroid App Bundle（AAB）です
（[docs/records/managed/DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）。
Google Play経由の配布では、標準的なWear OSアプリの配布方式（スマホへのインストール後、
ペアリング済みのWatchへ自動的にwear分がプッシュインストールされる）が利用できる見込みです
（**未確認**: 実機・Play Console経由での動作確認はBL-038、人手検証）。

## 現状（2026-09-05時点）

このアプリはGoogle Playに未公開です（[docs/records/managed/BACKLOG.md](records/managed/BACKLOG.md)
BL-034が未着手）。そのため、現時点で実際にインストールできるのは
「[1. 開発者・検証者向け](#1-開発者検証者向けローカルビルドを実機にインストールする)」の
ローカルビルド経由のみです。「[2. 一般ユーザー向け](#2-一般ユーザー向けgoogle-play経由のインストール公開後の想定現状未対応)」は
Google Play公開後を見据えた想定手順であり、公開作業（BL-034）が完了するまでは実行できません。

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

`wear`は`mobile`のdynamic feature（BL-036）のため、`:wear:installDebug`という独立タスクは
存在しません（feature モジュール単体ではbaseのapplicationId解決に失敗しビルドできません）。
インストールは`:mobile:installDebug`経由で行います。

接続しているデバイスが1台だけの場合は、そのまま実行できます。

```bash
./gradlew :mobile:installDebug
```

スマホとスマートウォッチを同時に接続している場合は、`ANDROID_SERIAL` 環境変数でインストール先を
1台に固定し、デバイスごとに実行します。デバイスIDは `adb devices` の出力の1列目
（`List of devices attached` の下に並ぶ識別子。Wi-Fi接続なら `IPアドレス:ポート` そのもの）です。

```bash
# スマホへ
ANDROID_SERIAL=192.168.1.149:41235 ./gradlew :mobile:installDebug
# スマートウォッチへ
ANDROID_SERIAL=192.168.1.105:37515 ./gradlew :mobile:installDebug
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

## 2. 一般ユーザー向け：Google Play経由のインストール（公開後の想定、現状未対応）

**現状（2026-08-22時点）、このアプリはGoogle Playに公開されていません**
（[BL-034](records/managed/BACKLOG.md)が未着手）。以下は公開後を想定した手順であり、
実際の画面・文言は公開時の設定により変わり得ます（未確認）。

`mobile`と`wear`は単一の`applicationId`・単一AABとして登録されるため（BL-036、
[DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）、標準的なWear OSアプリの
配布方式に従います。

1. スマホのGoogle Playストアで本アプリのページを開き、インストールする
2. ペアリング済みのスマートウォッチへ、wear部分が自動的にプッシュインストールされる見込み
   （**未確認**: 実機・Play Console経由での動作確認はBL-038、人手検証）
3. インストール後の初期設定は [1.6](#16-インストール後の初期設定) と同様

自動プッシュインストールが行われない場合でも、スマートウォッチのPlay Store
（Wear OS上のGoogle Playストア）から本アプリを直接検索してインストールできる想定です（未確認）。

限定公開（内部テスト・クローズドテスト）中は、Google Play Consoleが発行するテスターリンクを開き、
テスト参加への同意を行った上で、上記と同じ手順でインストールします。

## 関連ドキュメント

- [README.md](../README.md): 前提環境・セットアップ・ビルド手順
- [docs/records/managed/DESIGN.md](records/managed/DESIGN.md): Google Play配布方式の実装詳細
- [docs/records/managed/BACKLOG.md](records/managed/BACKLOG.md):
  BL-034（Google Play限定公開の人手検証）、BL-038（Google Play経由の自動プッシュインストール確認）

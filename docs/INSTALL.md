# アプリのインストール方法（スマホ・スマートウォッチ）

本ドキュメントは、`mobile`（スマホ側）と`wear`（スマートウォッチ側）のアプリを実機へインストールする
手順をまとめたものです。両者は別々の`applicationId`を持つ独立したAndroidアプリのため
（[docs/records/managed/DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）、
Wear OS標準の「スマホにインストールすると、ペアリング済みのWatchへ自動的にインストールされる」機能は
使えません。**mobile / wear の両方を、それぞれ個別にインストールする必要があります。**

## 現状（2026-08-22時点）

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

> **未確認**: 以下はWear OS一般の公開手順に基づく記述であり、本リポジトリの実機検証タスク
> （[BL-011](records/managed/BACKLOG.md)、人手検証・未着手）でこの手順自体を確認した実績はまだ
> ありません。Wear OSのバージョンや機種により文言・手順が異なる場合があります。

1. スマートウォッチの設定 → デバイス情報（または「バージョン情報」）→ ビルド番号を7回タップ
2. スマートウォッチの設定 → 開発者向けオプション → 「ADBデバッグ」をON
3. 同じく開発者向けオプション → 「Wi-Fi経由のデバッグ」をON
   （スマートウォッチとスマホが同一Wi-Fiネットワークに接続されている必要があります）
4. スマホ側のWear OS companion appから、スマートウォッチとの「デバッグのペア設定」
   （Bluetooth経由でのADBペアリングコード入力）を行う
5. ペア設定成功後、スマートウォッチの開発者向けオプション画面にIPアドレスとポート番号が表示される

### 1.4 ADBでスマートウォッチへ接続する

```bash
adb connect <スマートウォッチのIPアドレス>:<ポート番号>
adb devices
```

スマホとスマートウォッチの両方が `adb devices` の一覧に表示されることを確認してください。

### 1.5 アプリをビルド・インストールする

リポジトリルートでGradle Wrapper経由で実行します（`gradlew.bat`はWindows用）。
`mobile` / `wear` はいずれも Android Application Gradle Plugin を使用しているため、
モジュールごとに `installDebug` タスクが利用できます。

```bash
# スマホ側にmobileアプリをインストール
./gradlew :mobile:installDebug

# スマートウォッチ側にwearアプリをインストール
./gradlew :wear:installDebug
```

`adb devices` に複数台が同時接続されている場合、`installDebug` はインストール先を一意に
決定できず失敗することがあります。その場合はAPKを個別にビルドし、デバイスIDを指定して
`adb install` してください。

```bash
./gradlew :mobile:assembleDebug :wear:assembleDebug
adb -s <スマホのデバイスID> install -r mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s <スマートウォッチのデバイスID> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

デバイスIDは `adb devices` の出力の1列目（`List of devices attached` の下に並ぶ識別子）で
確認できます。

### 1.6 インストール後の初期設定

1. スマホ側で`mobile`アプリを起動し、資格情報（uuid / secretKey / apikey）を設定する
   （[README.md](../README.md)「セットアップ手順」参照）
2. スマートウォッチ側で`wear`アプリのTileをウォッチフェイスに追加する

## 2. 一般ユーザー向け：Google Play経由のインストール（公開後の想定、現状未対応）

**現状（2026-08-22時点）、このアプリはGoogle Playに公開されていません**
（[BL-034](records/managed/BACKLOG.md)が未着手）。以下は公開後を想定した手順であり、
実際の画面・文言は公開時の設定により変わり得ます（未確認）。

公開後も`mobile`と`wear`は別々のPlay Storeページ（別々の`applicationId`）として登録される制約が
あるため（[DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）、Wear OSアプリ標準の
自動プッシュインストールは使えません。利用者は以下の手順で両方を個別にインストールする必要が
あります。

1. スマホのGoogle Playストアで本アプリ（mobile版）のページを開き、インストールする
2. スマートウォッチのPlay Store（Wear OS上のGoogle Playストア、またはスマホのWear OS companion app
   経由）で本アプリ（wear版）のページを開き、インストールする
3. インストール後の初期設定は [1.6](#16-インストール後の初期設定) と同様

限定公開（内部テスト・クローズドテスト）中は、Google Play Consoleが発行するテスターリンクを開き、
テスト参加への同意を行った上で、上記と同じ手順でインストールします。

## 関連ドキュメント

- [README.md](../README.md): 前提環境・セットアップ・ビルド手順
- [docs/records/managed/DESIGN.md](records/managed/DESIGN.md): Google Play配布方式の制約の詳細
- [docs/records/managed/BACKLOG.md](records/managed/BACKLOG.md): BL-011（実機Tile操作の人手検証）、
  BL-034（Google Play限定公開の人手検証）

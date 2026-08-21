# アプリのインストール方法（スマホ・スマートウォッチ）

本ドキュメントは、`mobile`（スマホ側）と`wear`（スマートウォッチ側）のアプリを実機へインストールする
手順をまとめたものです。`wear`は`mobile`のdynamic feature（BL-036）として統合されており、
`applicationId`・署名・バージョンは`mobile`から継承する単一のAndroid App Bundle（AAB）です
（[docs/records/managed/DESIGN.md](records/managed/DESIGN.md)「Google Play配布方式」参照）。
Google Play経由の配布では、標準的なWear OSアプリの配布方式（スマホへのインストール後、
ペアリング済みのWatchへ自動的にwear分がプッシュインストールされる）が利用できる見込みです
（**未確認**: 実機・Play Console経由での動作確認はBL-038、人手検証）。

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

`wear`は`mobile`のdynamic feature（BL-036）のため、`:wear:installDebug`という独立タスクは
存在しません（feature モジュール単体ではbaseのapplicationId解決に失敗しビルドできません）。
インストールは`:mobile:installDebug`経由で行います。

```bash
./gradlew :mobile:installDebug
```

> **未確認**: dynamic feature構成でのローカルインストール時、接続中のスマホとスマートウォッチ
> 双方へ自動的に適切なモジュール（base/wear feature）が振り分けられるかは、本リポジトリでは
> 実機未検証です（BL-038）。最も確実な方法はAndroid Studioで`mobile`の実行構成を開き、
> 実行対象デバイスとしてスマホとスマートウォッチの両方を指定して「Run」することです。

コマンドラインのみで両方へインストールしたい場合、Googleの[bundletool]公式ツールで
AABからデバイス構成ごとのAPK Setを生成し、`install-apks`で接続デバイスへ配信する方法があります
（未検証、参考情報。`bundletool`は別途入手が必要です）。

```bash
./gradlew :mobile:bundleDebug
bundletool build-apks --bundle=mobile/build/outputs/bundle/debug/mobile-debug.aab --output=mobile-debug.apks
bundletool install-apks --apks=mobile-debug.apks --device-id=<スマホのデバイスID>
bundletool install-apks --apks=mobile-debug.apks --device-id=<スマートウォッチのデバイスID>
```

[bundletool]: https://developer.android.com/tools/bundletool

デバイスIDは `adb devices` の出力の1列目（`List of devices attached` の下に並ぶ識別子）で
確認できます。`adb devices` に複数台が同時接続されている場合、`:mobile:installDebug`は
インストール先を一意に決定できず失敗することがあります。その場合は上記bundletool経由での
個別インストール、またはAndroid Studioでのデバイス選択を使ってください。

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
- [docs/records/managed/BACKLOG.md](records/managed/BACKLOG.md): BL-011（実機Tile操作の人手検証）、
  BL-034（Google Play限定公開の人手検証）、BL-038（dynamic feature統合後の実機インストール確認）

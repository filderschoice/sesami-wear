# BLE鍵の同一性確認手順（BL-150）

本アプリが保持している secretKey（SESAME Biz 由来、16進数32文字）で、Sesame 5 へ **BLE で直接接続して
状態を取得できるか** を確認するための手順です。PC から実施でき、Android アプリの実装を待つ必要はありません。

この確認は、BLE 直接操作（[docs/records/managed/DESIGN.md](records/managed/DESIGN.md)
「BLE直接操作の併用方針」）の前提となる唯一の未実証事項です。成り立たない場合は BL-151〜BL-154 を
すべて取りやめる判断になります。

- **施錠/解錠は行いません。** 実際に鍵が動くため、確認は状態取得までで足ります。
- **Sesame Web API のリクエストを消費しません。** BLE のみで完結するため、月間リクエスト上限
  （BL-141）に到達している状態でも実施できます。
- 所要時間の目安は、準備を含めて15〜30分です。

## 資格情報の取り扱い（MUST）

`rules/guardrails-unified.v1.md` 3.3 / 12.5 に従い、次を守ってください。

- secretKey を**スクリプトへ直接書かない**。下記のスクリプトは実行時に入力を促す形（`getpass`。
  画面に表示されず、シェルの履歴にも残らない）にしてあります。
- 作業用ディレクトリは**本リポジトリの外**に作る（例: `%USERPROFILE%\sesame-ble-check`）。
  リポジトリ配下へ置くと、コミットへ混入する事故が起きます。
- 確認結果を記録へ戻すときも、**secretKey・uuid・BLE アドレスの値そのものは書かない**。
  「一致した」「取得できた」といった事実だけを記録します。

## 前提環境

| 項目 | 条件 |
| --- | --- |
| Python | 3.12 以上（`gomalock` の要件） |
| PC | BLE 対応。Windows 11 version 22000 以降、Linux は BlueZ 5.82 以降、macOS 10.15 以降（未検証） |
| 場所 | 対象の Sesame 5 と同じ部屋（BLE の電波が届く範囲） |
| 資格情報 | アプリの資格情報設定画面へ入れているものと同じ secretKey（16進数32文字） |
| 対象デバイス | アプリへ登録済みの Sesame 5 の uuid（照合に使う。設定画面で確認できます） |

使用するライブラリは [`meronepy/gomalock`](https://github.com/meronepy/gomalock)（Python、MIT）です。
BLE アドレスと16進数32文字の secret key だけでスキャン・接続・状態取得ができ、クラウドへは接続しません。

> 本書のスクリプトは 2026-09-18 時点の gomalock の `examples/discover.py` と
> `examples/detailed_status.py` の API に基づいて書いています。**本リポジトリでは未実行です**
> （実資格情報と実 Sesame デバイスを要するため）。API が変わっていて動かない場合は、同リポジトリの
> `examples/` と `docs/` を参照してください。

## 手順

### 1. 作業ディレクトリと仮想環境を用意する

リポジトリの外で実行します（PowerShell の例）。

```powershell
mkdir $env:USERPROFILE\sesame-ble-check
cd $env:USERPROFILE\sesame-ble-check
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install gomalock
```

### 2. 対象デバイスの BLE アドレスを調べる

`discover.py` として保存し、実行します（資格情報は不要です）。

```python
import asyncio

import gomalock


async def main():
    devices = await gomalock.SesameScanner.discover(timeout=30)
    for scanned_device in devices.values():
        print(f"{'Address':11}: {scanned_device.address}")
        print(f"{'Model':11}: {scanned_device.advertisement_data.product_model.name}")
        print(f"{'Registered':11}: {scanned_device.advertisement_data.is_registered}")
        print(f"{'UUID':11}: {scanned_device.advertisement_data.device_uuid}")
        print("-" * 50)


if __name__ == "__main__":
    asyncio.run(main())
```

30秒スキャンして、周囲の Sesame デバイスを一覧表示します。出力のうち、

- `UUID` が**アプリへ登録済みの uuid と一致する**行を選ぶ（大文字小文字は無視して比較する）
- その行の `Model` が Sesame 5 系であること、`Registered` が `True` であることを確認する
- その行の `Address` を手順3で使う

対象が複数ある場合は、まず1台（玄関など主に使うもの）で確認すれば十分です。

### 3. secretKey で接続し、状態を取得する

`check_status.py` として保存し、`ADDRESS` を手順2で調べた値へ置き換えて実行します。
実行すると secretKey の入力を求められます（入力は画面に表示されません）。

```python
import asyncio
import getpass
import os

import gomalock

ADDRESS = "XX:XX:XX:XX:XX:XX"  # 手順2で調べた値へ置き換える
SECRET_KEY = os.environ.get("SESAME_SECRET_KEY") or getpass.getpass("secretKey (16進数32文字): ")

received = asyncio.Event()


def on_mech_status_changed(sesame5, status):
    print(f"{'Logged in':18}: {sesame5.is_logged_in}")
    print(f"{'Device status':18}: {sesame5.device_status.name}")
    print(f"{'Position (angle)':18}: {status.position}")
    print(f"{'Locked':18}: {status.is_in_lock_range}")
    print(f"{'Unlocked':18}: {status.is_in_unlock_range}")
    print(f"{'Battery percentage':18}: {status.battery_percentage}")
    print(f"{'Battery voltage':18}: {status.battery_voltage}")
    received.set()


async def main():
    async with gomalock.Sesame5(
        ADDRESS,
        secret_key=SECRET_KEY,
        mech_status_callback=on_mech_status_changed,
    ):
        await asyncio.wait_for(received.wait(), timeout=30)


if __name__ == "__main__":
    asyncio.run(main())
```

**成功の判定**: `Logged in` が `True` で、`Position (angle)` と `Battery percentage` に値が出れば成功です。
BL-150 の完了条件（角度または電池残量を取得できること）を満たします。

### 4. クラウドへ接続していないことを確認する

手順3が成功したら、**PC のネットワークを完全に切断**（Wi-Fi をオフ、有線を抜く）した状態で手順3を
もう一度実行します。Bluetooth はネットワーク接続と独立して動くため、BLE だけで完結していれば
ネットワークが無くても同じ結果になります。

ここで成功すれば、AWS Cognito などクラウドへ一切接続せずに到達できていることの確認になります。
これは BLE 併用方針の前提（クラウドへ接続しない）そのものの検証です。

### 5. 結果を記録する

次の内容をメモして戻してください。BACKLOG の BL-150 へ反映します。**値そのものは書かないでください。**

- 成功したか、失敗したか
- 成功時: `Position (angle)` と `Battery percentage` が取得できたか / ネットワーク切断状態でも
  成功したか / 使用した gomalock のバージョン（`pip show gomalock`）
- 失敗時: どの段階で止まったか（スキャンに出てこない / 接続できない / ログインできない /
  タイムアウト）と、エラーメッセージの種類

## うまくいかないときの切り分け

| 症状 | 見るところ |
| --- | --- |
| スキャンに何も出ない | PC の Bluetooth がオンか。Sesame の電波圏内か。他の Sesame 機器が1つも出ないなら PC 側の問題 |
| 対象の uuid が出てこない | 対象の Sesame が別の部屋にある。または Hub 3 経由でしか見えていない（BLE は Hub 3 を経由しません） |
| `Registered` が `False` | 未登録のデバイスを見ている。対象が違う |
| 接続はできるがログインできない | **secretKey が BLE 用の鍵と別物である可能性が高い**。BL-150 の答えが「同一でない」になるため、この結果をそのまま記録する |
| 接続自体ができない | Hub 3 や Sesame 純正アプリが BLE 接続を占有している可能性（**未確認の推測**）。Hub 3 の電源を一時的に抜き、純正アプリを終了してから再試行する |
| 途中でタイムアウトする | Sesame との距離を縮める。電池残量が極端に少ないと応答が不安定になることがある |

「接続はできるがログインできない」が出た場合は、可能であれば「QR Code Reader for SESAME」で
マネージャー権限以上の QR から抽出した secret key と、SESAME Biz の secretKey が同じ値かを
突き合わせてください（**値は記録へ書かず、一致するかどうかだけ**を戻してください）。
gomalock の README は両方を入手元として挙げており、同じ値に収束するはずというのが現在の想定です。

## この確認で分かること・分からないこと

分かること:

- SESAME Biz 由来の secretKey だけで、BLE のログインと状態取得まで到達できるか
- その到達がクラウドへ接続せずに成立するか

分からないこと（別途の確認が必要）:

- Android（公式 SDK の取り込み、または自前実装）で同じことができるか → BL-151 で確認する
- BLE 経由の施錠/解錠が成功するか → 認証が通れば可能と考えられるが**未実証**。BL-151 で確認する
- スマートフォンが圏外のときの挙動、複数デバイスでの挙動 → BL-152 の経路選択で扱う

## 参考

- [meronepy/gomalock](https://github.com/meronepy/gomalock)（MIT）。
  `examples/discover.py` と `examples/detailed_status.py` が本書のスクリプトの原典です
- [docs/records/managed/DESIGN.md](records/managed/DESIGN.md)「BLE直接操作の併用方針」:
  調査の結論（論点1〜5）と段階的移行案
- [docs/records/managed/BACKLOG.md](records/managed/BACKLOG.md): BL-150 と、依存する BL-151〜BL-154

# 背景・目的
Pixel WatchからSesami（CANDY HOUSE Sesame）のスマートロックを操作するAndroid/Wear OSアプリを開発したい。
使用デバイス: Sesame 5 + Hub 3（Wi-Fi Module 2の後継。クラウド経由リモート制御を担う）

# API仕様（CANDY HOUSE 新WebAPI）
- エンドポイント: https://app.candyhouse.co/api/sesame2/{uuid}
- 認証情報3点セット:
  - uuid（sesame2_uuid）: Sesameアプリの「鍵をシェア」QRコードから取得（マネージャー/オーナー権限のキーで発行すること。ゲストキーは不可）
  - secretKey: 同QRコードから取得。制御コマンドの署名鍵
  - apikey: partners.candyhouse.co で発行するx-api-key
- 状態取得（GET）: x-api-keyヘッダーのみで可能
- 施錠/解錠（POST）: secretKeyを使ったAES-CMAC署名付きコマンドが必須（cmd種別＋タイムスタンプ等をバイト列化して署名。素のJSON送信では通らない）
- 参考実装: pysesame3、chanshige/sesame（PHP）などのOSSにCMAC署名ロジックの実装例あり。Kotlin/Java向けの軽量ラッパーは少ないため、これらのロジックを移植する前提

# アーキテクチャ方針
secretKeyは機密性が高いためWatch単体には保持させない。
- スマホ側: apikey/secretKeyを保持し、CMAC署名生成とAPI呼び出しを担当
- Watch側: Tile/AppからWearable Data Layer API（MessageClient）で「lock」「unlock」の意図のみをスマホに送信
- スマホ側で実行→結果（成功/失敗）をWatchに返す
- 過去に検討したWear OS実装知見（RemoteActivityHelper、Data Layer API、Android 11 Package Visibility制約、Doze遅延）を踏襲する

# UX要件
- Tileはロック状態（施錠中/解錠中）をアイコン・色でひと目表示
- 施錠はワンタップ即実行、解錠は誤操作防止のためホールド確認 or 確認ダイアログを挟む（非対称なリスク設計）
- 通信中は明確な処理中表示＋ボタン無効化で二重送信防止
- 成功/失敗をハプティクスパターンで区別（画面を見なくても分かるように）
- スマホ未接続時はTile上で明示し操作不可にする（サイレント失敗を避ける）
- Complicationで常時ロック状態を文字盤表示

# 次にやりたいこと
1. secretKeyを使ったAES-CMAC署名処理をKotlinで実装（まずスマホ側の疎通確認から）
2. curlまたはPostmanでGET状態取得・POST施錠/解錠の疎通確認
3. Data Layer APIでのWatch⇔スマホ間メッセージング実装
4. Tile UIの実装（上記UX要件に沿って）

このプロンプトの続きから、まず1のCMAC署名実装を手伝ってほしい。
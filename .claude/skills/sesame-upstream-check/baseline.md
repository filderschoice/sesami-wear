# 前回確認の基準点

Skill `sesame-upstream-check` が差分だけを調べるための基準点です。確認を終えるたびに本ファイルを更新します。
基準点は「中身まで確認し終えた最新のコミット」で、確認先のHEADとは限りません（HEADより前なら、その差は未確認）。
取得できなかった値は「未記録」と書き、推測で埋めません。

| 確認先 | 基準点（確認済みの最新。次回はここより後を読む） | 備考 |
| --- | --- | --- |
| 確認日 | 2026-11-21 | 本ファイルの値を記録した日 |
| CANDY-HOUSE/API_document（master）の確認済みHEAD | `5fd2ac4c16ddf91eac86fc81d7963bd7d77542a6`（2026-11-16T06:46:47Z） | 次回はこの値より後を読む。2026-11-21 の調査で読んだ最新は `fa50a22ac`（2026-11-11）。それ以降 `5fd2ac4c` までの3コミットは、変更ファイル一覧（gh api の compare）で `docs/**/SesameOS3/mcp.md` の3ファイルだけと確かめ、照合点（webapi.md・Web API）に関わらないため基準点をHEADへ進めた |
| CANDY-HOUSE/SesameSDK_Android_with_DemoApp（master）の確認済みHEAD | `add8303f5725e07713a8df8d38f098c87c98b3f8`（2026-11-20T10:20:37Z） | 次回はこの値より後を読む。2026-11-21 に `2e7d27d7c`（2026-09-30）以降の37コミット・119ファイルを追加調査した。差分のうち照合点に関わる `sesameSdk/src` の5ファイルと `sesameApp` の ble 配下5ファイルを精読し、他は一覧のみ。デモアプリの CredentialSession / CredentialChannel と BleController 等はキーワードの grep で該当行のみ読んだ |
| ChangeLogで最後に確認した項目の日付 | 2026-11-14（Android app 3.0.266(939)） | 2026年10月・11月分を確認済み。これより新しい項目から読む |

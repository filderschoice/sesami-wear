<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-285
  区分: 人手検証
  タスク内容: >-
    0.16.0（mobile versionCode 15 / wear versionCode 1013）を Google Play のクローズドテストへ配信する。
    署名付きAABは作成済み（mobile/build/outputs/bundle/release/mobile-release.aab を電話・タブレット系トラックへ、
    wear/build/outputs/bundle/release/wear-release.aab を Wear OS 専用トラックへ。両AABの署名SHA256は一致確認済み）。
    「このリリースの新機能」欄には docs/RELEASE_NOTES.md の0.16.0「ストア掲載用の要約」を転記する。
    実機検証（docs/verification/VERIFICATION-0.16.0.local.md）の結果を確認してから提出する。
    両トラックで公開中になったら、リリース準備のマージコミットへ注釈付きタグ v0.16.0 を作成し GitHub Release を
    公開する（Skill github-release-publish）。紹介ページの gh-pages 反映もこのとき行う。
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: >-
    0.16.0 が両トラックで公開中で、タグ v0.16.0 と GitHub Release が作成されていること
  根拠: >-
    Play Console の操作・git push・タグ公開はユーザー作業（rules/guardrails-unified.v1.md セクション12.2）。
    実機検証の合否はユーザーの確認が要る。
  依存: []

- id: BL-284
  区分: 人手検証
  タスク内容: >-
    設定画面の再編・接続確認の追加に合わせて、掲載用スクリーンショット3点（phone_4_device_list / phone_5_menu /
    phone_7_help）を撮り直し済み（2026-11-08、Pixel 8 Pro実機の撮影モード。docs/store/images/screenshots/ と
    site/assets の画像、docs/store/README.md・STORE_LISTING.md・site/index.html の説明も更新済み）。
    あわせて接続確認（レーダー）の画像を追加済み（スマートフォン用 phone_6_radar / phone_7_radar_all は旧 phone_6_device_edit / phone_7_help と入れ替え。Wear OS用 wear_7_radar / wear_8_radar_connected / wear_9_radar_all）。
    残りは Play Console の掲載画像の差し替えで、スマートフォン用は旧 phone_4〜phone_7 を削除して新しい画像を入れ、Wear OS用は wear_3 を削除して wear_7〜wear_9 を追加する（上限8枚）。
    紹介ページ（site/）の反映は main へマージ後に site/README.md の手順で行う。
  優先度: P3
  状態: 進行中
  担当: ユーザー
  完了条件: >-
    Play Console のスマートフォン用（8枚）とWear OS用（8枚）のスクリーンショットが docs/store/images/screenshots/ と一致していること
  根拠: >-
    Play Console の画像差し替え（ブラウザ操作・外部への公開）は自律ループ実行モードでは行えない
    （rules/guardrails-unified.v1.md セクション12.2）。
  依存: []

- id: BL-195
  区分: 人手検証
  タスク内容: >-
    BLEが数分単位で届かなくなる時間帯がある。2026-09-21 12時台のBL-193の実機検証で、
    スマートフォンを同じ位置に置いたまま、到達確認の結果が12:40:04・12:42:33・12:45:05は
    `detail=MISS`（探索も予備アドレスへの直接接続も失敗）、12:43:43・12:47:47は`detail=SCAN`で成功、
    と数分おきに変動した。12:34:08にBLEでの状態取得が成功した直後から12:42までは一度も通らず、
    12:44:09の操作も`detail=NOT_FOUND`だった。原因の候補は (a) Hub 3 がSesameのBLE接続を
    掴んでいる時間帯がある（Sesame 5 は同時に1接続しか持てない）、(b) Androidのスキャン制限、
    (c) Sesame側の広告間隔。切り分けは、Hub 3 の電源を抜いた状態で同じ操作を繰り返し、
    `detail=MISS`の頻度が変わるかを見る。変わらなければ(b)を疑い、操作の間隔を5分以上空けて再測する。
    利用者から見た影響は「Bluetoothで操作できるときとできないときがある」で、失敗しても
    インターネット経由で成功するため操作自体は通る。
    2026-09-22、ユーザー判断により現時点では追加調査（Hub 3の電源を抜いての切り分け）を
    積極的には行わず、同様の症状が再発した際のユーザーからの報告を契機に対応を検討する
    方針とした。
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: >-
    `detail=MISS`が続く時間帯の原因が(a)〜(c)のいずれかに絞れ、対処の要否を判断できていること
  根拠: >-
    2026-09-21のBL-193の実機検証で観測した。Sesame実機とHub 3を含む電波環境が要り、
    JVM上の単体テストでは再現できないため人手検証とする。
    2026-09-22、単発の観測のみで再現頻度が未確認のため、ユーザー判断で積極調査を保留し、
    再発報告を待つ運用へ変更した。
  依存: []

- id: BL-106
  区分: 人手検証
  タスク内容: Google Playの製品版公開に必要なクローズドテスト（12人以上が14日間連続でオプトイン）を
    実施し、製品版へのアクセスを申請する。テスター管理はメールアドレスのリストではなく
    Googleグループ方式を採用し、参加者が自分でグループへ参加してオプトインする自己完結フローに
    する（ユーザーがメールアドレスを収集・保管しない方針）。手順の詳細と現在地は
    docs/store/PLAY_CONSOLE_STEPS.local.md のフェーズ8（クローズドテスト）を
    参照する。2026-09-14に0.10.0が両トラックで公開中となり、2026-09-15にQiita記事とXで募集を開始した。
    オプトイン数は日々変わるため本記録には書かず、Play Consoleのダッシュボードで確認する（12人に届くまで募集を続ける）。
    2026-09-18に0.11.0が両トラックで公開され、docs/CLOSED_TEST.mdの参加条件が変わった
    （スマートウォッチが無くてもホーム画面ウィジェットで試せる）ため、募集先（Qiita記事・X）への
    反映が必要。反映済みかは未確認
  優先度: P2
  状態: 進行中
  担当: ユーザー
  完了条件: クローズドテストで12人以上が14日間連続でオプトインした状態を満たし、製品版への
    アクセス申請が承認される
  根拠: Claudeによる代替実施は不可。Play ConsoleとGoogleグループはブラウザ専用の操作であり、
    テスターの募集も外部への発信にあたるため自律ループ実行モードでは実行できない
    （rules/guardrails-unified.v1.md セクション12.2）
  依存: []


```
<!-- COPILOT_RECORDS:END -->

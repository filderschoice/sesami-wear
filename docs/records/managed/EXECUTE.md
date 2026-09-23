<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用実施記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.execute.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- date: 2026-09-23 17:00
  summary: 撮影モードの操作画面と、モード中の控えめな表示を追加する（デバッグ版限定）
  details:
    変更内容: >-
      デバッグ版の設定メニューに「撮影モード」を追加し、操作画面`ShowcaseModeActivity`で、モードのオン/オフ、
      見本の3台への置き換え、全台の施錠中/解錠中、デバイスごとの施錠状態・電池残量・経路・直近の失敗・
      最終取得時刻の切り替えを行えるようにした。撮影モード中はカード一覧のタイトルの横へ「📷 撮影モード」を
      小さく出す。メニュー項目と表示は`ShowcaseModeUi`越しに呼び、リリース版は何も出さないスタブにした。
      `MainActivity`はモードが切り替わって戻ってきたら作り直す。手順を`README.md`とスキル
      （`store-screenshot-capture` / `realmachine-verification`）へ追記し、実機確認をBL-214として起票した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/showcase/ShowcaseMenuEntry.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/SettingsMenu.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - mobile/src/debug/AndroidManifest.xml
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseModeActivity.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseModeUi.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseStateOptions.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseDeviceState.kt
      - mobile/src/release/kotlin/com/sesamiwear/mobile/showcase/ShowcaseModeUi.kt
      - mobile/src/testDebug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseStateOptionsTest.kt
      - README.md
      - .claude/skills/store-screenshot-capture/SKILL.md
      - .claude/skills/realmachine-verification/SKILL.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      ./gradlew :mobile:compileReleaseKotlin / ./gradlew :mobile:detektDebug（新規・変更ファイルの指摘のみ確認） /
      npx markdownlint-cli2 "**/*.md" / npx markdownlint-cli2 ".claude/**/*.md" ".github/**/*.md" /
      python scripts/validate-records.py
    検証結果: >-
      成功 - 品質ゲートはすべて終了コード0。リリース版の`showcase`パッケージのクラスはスタブの
      `ShowcaseMode`・`ShowcaseModeUi`と、mainの`ShowcaseMenuEntry`だけであることを確認した。
      実機での表示と連携はBL-214（人手検証）で確認する
    関連ID:
      - BL-213
      - BL-214
- date: 2026-09-23 16:00
  summary: スクリーンショット撮影用の撮影モード（デバッグ版限定）の土台を追加する
  details:
    変更内容: >-
      施錠/解錠・状態取得の実行口を`SesameDeviceCommands`へ抽象化し、`SesameDeviceCommandExecutorFactory`が
      撮影モード中は撮影用の実行口（`ShowcaseDeviceCommands`、Web APIもBLEも呼ばない）を返すようにした。
      画面・ウィジェットが開く保存先を`SesameDeviceStores`へ集め、撮影モード中は撮影用の別ファイルへ差し替える
      （資格情報・ロック状態・ウィジェットの割り当て）。撮影用のAPIキー・秘密鍵は保存前にダミー値へ置き換える。
      実装は`mobile/src/debug`、リリース版は機能を持たないスタブを`mobile/src/release`へ置いた。
      モード切り替え時のウォッチ・ウィジェットへの再同期（`ShowcaseSync`）と見本3台（`ShowcasePresets`）を含む。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommands.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/SesameDeviceStores.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/RemovedDeviceCleaner.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunner.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetRepository.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/DeviceCard.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseMode.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseSync.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseDeviceCommands.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseDeviceState.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseCredentialsKeyValueStore.kt
      - mobile/src/release/kotlin/com/sesamiwear/mobile/showcase/ShowcaseMode.kt
      - mobile/src/testDebug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseDeviceCommandsTest.kt
      - mobile/src/testDebug/kotlin/com/sesamiwear/mobile/showcase/ShowcaseStoresTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      ./gradlew :mobile:compileReleaseKotlin / ./gradlew :mobile:detektDebug（新規ファイルの指摘のみ確認） /
      npx markdownlint-cli2 "**/*.md" / npx markdownlint-cli2 ".claude/**/*.md" ".github/**/*.md" /
      python scripts/validate-records.py
    検証結果: >-
      成功 - 品質ゲートはすべて終了コード0。リリース版の`showcase`パッケージのクラスはスタブの
      `ShowcaseMode`1つだけであることを`mobile/build/tmp/kotlin-classes/release`で確認した。
      通常の`detekt`タスクは`src/debug`を走査しないため、型解決付きの`detektDebug`で新規ファイルに
      指摘が無いことを別途確認した（既存ファイルの指摘は対象外）
    関連ID:
      - BL-212
- date: 2026-09-23 14:30
  summary: Tile・ウィジェットのデバイス名の帯を押せるチップより暗い色の表示専用にし、タップを外す
  details:
    変更内容: >-
      `core.display.SesameTileContent`へ帯の背景色`NAME_HEADER_COLOR_ARGB`（0xFF262626、押せるチップの
      0xFF424242より暗い）を追加し、Tileの`buildNameHeader`とウィジェットの`SesameWidgetChips.NameHeader`で使う。
      両方から帯のクリック（状態取得）を外した（ユーザーの選択。「更新」と機能が重複していた）。
      帯の色がチップより暗いことを`SesameTileContentTest`へ追加した。利用者向けの`docs/USER_GUIDE.md`・
      `docs/RELEASE_NOTES.md`（0.14.0）を追随させた。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameTileContent.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameTileContentTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetChips.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / npx markdownlint-cli2 ".claude/**/*.md" ".github/**/*.md" /
      python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。実機での見た目とタップが効かないことはBL-211（人手検証）で確認する。
    関連ID:
      - BL-210
- date: 2026-09-23 14:00
  summary: Tileのデバイス名の帯で経路マークを名前の前へ移して見切れを防ぎ、左列を狭めて右列へ幅を回す
  details:
    変更内容: >-
      `SesameTileService.buildNameHeader`で経路アイコンを名前の前へ移した（`Row`は子を先頭から順に測り、
      後ろの子へ残り幅しか渡さないため、長い名前で後ろのアイコンが見切れていた）。名前は残り幅で末尾省略される。
      左列`LEFT_COLUMN_WIDTH_DP`を76dpから56dpへ狭め、右列（帯と状態チップ）を約78dpから約98dpへ広げた。
      ウィジェット（4x2）の`SesameWidgetChips.NameHeader`も同じ順へ揃えた（左列96dpは据え置き、ユーザー選択）。
      利用者向けの`docs/USER_GUIDE.md`・`docs/RELEASE_NOTES.md`（0.14.0）を追随させた。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetChips.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / npx markdownlint-cli2 ".claude/**/*.md" ".github/**/*.md" /
      python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0（初回はktlintの`spacing-between-declarations-with-comments`で失敗し、
      定数の前へ空行を入れて解消）。TileService・Glance描画は単体テスト対象外のため、実機での見た目は
      BL-211（人手検証）で確認する。
    関連ID:
      - BL-209
- date: 2026-09-23 12:10
  summary: Tileのデバイス名を状態の上の帯へ移し、左上を「更新」、経路をベクターアイコンで帯へ出す
  details:
    変更内容: >-
      `SesameTileService`を、左列＝「更新」「変更」、右列上＝デバイス名の帯（`buildNameHeader`、中立色で
      デバイス名と経路のベクターアイコンを並べる）、右列下＝状態チップへ組み替えた。経路アイコンは
      スマホと同じMaterialのドローアブルをwearへ複製し、`onTileResourcesRequest`で登録（`RESOURCES_VERSION`を
      "2"へ）して白で着色する。状態チップの最終取得時刻の行からは経路の絵文字を外し、
      `SesameTileStatus`へ`route`と、Complication向けの`detailLabelWithRouteIcon`（絵文字を前置）を追加した。
      帯のぶん高さが減るため状態アイコンの書体を`DISPLAY1`から`DISPLAY2`へ下げた。Complicationの表示は従来どおり。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileStateResolver.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/main/res/drawable/ic_route_bluetooth.xml
      - wear/src/main/res/drawable/ic_route_internet.xml
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0（初回はdetektの`LongMethod`で失敗し、経路アイコンの生成を`buildRouteIcon`へ
      切り出して解消）。TileServiceはAndroid Tiles API依存のため単体テスト対象外で、円形画面での見切れの
      有無とベクターアイコンの描画はBL-208で実機確認する。
    関連ID:
      - BL-206
- date: 2026-09-23 11:20
  summary: ウィジェット（4x2）のデバイス名を状態の上の帯へ移し、左上を「更新」、経路アイコンを中立色の帯へ移す
  details:
    変更内容: >-
      `SesameWidget`のFULL表示を、左列＝「更新」「変更」、右上＝デバイス名の帯（`SesameWidgetChips.NameHeader`、
      中立色でデバイス名と経路のベクターアイコンを横に並べる）、右下＝状態表示へ組み替えた。経路アイコンは
      状態色（緑・赤）の上で見分けにくかったため、最終取得時刻の行（旧`DetailRow`）から帯へ移し白で描く。
      帯のタップも「更新」と同じ状態取得にした（旧来のデバイス名タップを引き継ぐ）。帯のぶん高さが増えるため
      `SesameWidgetLayout.FULL_MIN_HEIGHT_DP`を140dpから172dpへ引き上げた。2x1・1x1は変更していない。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetChips.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayout.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayoutTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。`SesameWidgetLayoutTest`はしきい値の定数を参照しており、172dpでも成功する。
      実機での表示確認はBL-208へ残す。
    関連ID:
      - BL-205
- date: 2026-09-23 10:30
  summary: 状態の「更新」で到達実績によらずBLEを試し、届かなければWeb APIへフォールバックする
  details:
    変更内容: >-
      `SesameBleAccess.checkStatus`を追加し、`SesameDeviceCommandExecutor.refreshStatus`の経路選択を
      置き換えた。到達実績の有無にかかわらずBLEで状態取得を試し、失敗したらWeb APIへ倒す。
      圏外（`NOT_REACHED`）の場合はWeb APIと並行する到達確認を間隔によらず行い（`withReachabilityProbe`へ
      `forceProbe`を追加）、BLEアドレスを覚え直せるようにした。方針が「常にインターネット経由」、または
      権限が無い・Bluetoothが無効な場合（`SesameBleOperations.isAvailable`を新設し、Factoryで
      `SesameBlePermissions.hasAll`とアダプタの有効状態を配線）は従来どおり。施錠/解錠の経路選択は変えていない。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameBleAccess.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTestFixture.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorStatusCheckTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。新規テスト6件（到達実績なしでもBLEを試す、圏外でWeb APIへ倒れ到達確認を
      強制する、圏内の失敗では強制しない、Bluetoothが使えない・方針が「常にインターネット経由」では試さない、
      施錠は従来どおり）を含む。実機での確認はBL-208へ残す。
    関連ID:
      - BL-204
- date: 2026-09-21 12:55
  summary: 到達確認がどの手段で成立したかをログへ残し、BL-193・BL-194を実機検証する
  details:
    変更内容: >-
      BL-193の実機検証で、到達確認（`SesameBleConnector.probeReachable`）が探索で当たったのか
      予備のアドレスへの直接接続で拾えたのかを切り分けられなかった。どちらの経路でも
      `SesameBleAddressCache.save`で同じ保存値になるため、保存値の差分からは区別できない。
      `route=BLE op=PROBE detail=SCAN|LAST_KNOWN|MISS`の1行を追加した。既存の
      `route=BLE op=<コマンド> detail=<結果>`と同じタグ（`SesameApiFailureLog.TAG`）を使い、
      BLEアドレスは出さない（rules/guardrails-unified.v1.md 3.3）。到達確認の挙動そのものは
      変えていない（`scanner.findDevice`の結果をローカル変数へ受けただけ）。
      この1行により、BL-193の実装（探索が外れたときに予備のアドレスへ直接つなぐ）が実際に
      使われたかを実機で判定できるようになった。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleConnector.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py /
      実機（Pixel 8 Pro + Pixel Watch 2 + 登録済みのSesame 5 2台）での状態取得
    検証結果: >-
      成功 - 自動の品質ゲートはすべて終了コード0。実機ではBL-193の完了条件
      （保存済みアドレスを失った状態からBLE経路へ復帰できること）と、BL-194の完了条件
      （バックグラウンドデータの制限でウィジェットに「通信エラー（端末の設定を確認）」が出ること・
      制限の解除で成功すること）をどちらも満たした。内訳はDESIGN.md
      「実機検証（BL-193 / BL-194、2026-09-21 12時台）」に記録した。
      予備のアドレスへ直接つなぐ分岐（`detail=LAST_KNOWN`）の成功は今回観測できておらず、
      BLEが数分単位で届かなくなる事象をBL-195として起票した。
    関連ID:
      - BL-193
      - BL-194
      - BL-195
- date: 2026-09-21 15:10
  summary: 最後に成功したBLEアドレスを残し、到達確認の探索が外れても直接接続で復帰できるようにする
  details:
    変更内容: >-
      2026-09-21のBL-191の実機検証で、同じ端末・同じ位置で**保存済みアドレスへの直接接続は成功する
      一方、到達確認の探索は3回とも当たらない**と観測した。`SesameBleConnector.connect`は直接接続に
      失敗すると保存値を捨てるため、`NOT_FOUND`が一度出ると「アドレス無し → 探索が当たらない →
      到達実績が付かない → BLEを試さない」から戻れなくなっていた。
      (1) `SesameBleAddressCache`を2段構えにした。現用（`load`、`ble_addresses`）と、最後に成功した
      アドレス（`loadLastKnown`、`ble_last_addresses`）を同じSharedPreferencesの別キーで持ち、
      `save`は両方へ書き、`remove`は現用だけを消す。既存の保存値はキー名も形式も変えていないため
      そのまま読める。
      (2) 到達確認を`SesameBleClient.probeReachable`から`SesameBleConnector.probeReachable`へ移し、
      探索が外れたときに予備のアドレスへ直接つないで圏内かを確かめるようにした。つながった接続は
      すぐ閉じ、現用の記録として書き戻して次の操作で探索を飛ばせるようにする。
      移設先を`SesameBleConnector`にしたのは、`SesameBleClient`の関数数がdetektの`TooManyFunctions`
      の上限（11）に達していて増やせないため。
      (3) `SesameBleClient.Timeouts`へ`probeConnectMillis`を追加した（実配線は1,500ms）。
      予備のアドレスがある場合はこのぶんだけ探索を短くし、**到達確認全体の4,000msは変えない**。
      到達確認はWeb APIと並行するが`withReachabilityProbe`が完了を待つため、伸ばすと利用者の
      待ちもそのぶん伸びるためである。
      現用を捨てる挙動そのものは変えていない（古いアドレスへ毎回接続を試みて利用者を待たせないため）。
      予備のアドレスが古いままでも捨てる契機は設けない。探索が当たれば`save`が両方を上書きして
      自然に直り、外れたときの損は到達確認の中の1,500ms（最短5分に1回、Web APIと並行）で頭打ちになる。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleAddressCache.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleConnector.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleClient.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleAddressCacheTest.kt
      - docs/RELEASE_NOTES.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 自動の品質ゲートはすべて終了コード0。現用を捨てても予備が残ること、
      新しく見つけたアドレスが予備も上書きすること、一度も到達していないデバイスには予備が
      無いことを`SesameBleAddressCacheTest`で固定した。
      到達確認そのものはAndroidのBLEスキャン・GATT接続に依存するためJVM上では再現できず、
      実機での復帰確認はBL-193へ人手検証として残す（区分を`性能`から`人手検証`へ変更）。
    関連ID:
      - BL-193
- date: 2026-09-21 14:20
  summary: バックグラウンドデータの制限による失敗を「端末の設定を確認」として区別し、対処を案内する
  details:
    変更内容: >-
      2026-09-21の実機検証で、端末の「バックグラウンドデータの制限」が本アプリに掛かっていると、
      モバイル回線が既定の経路のときにウィジェット・タイルからの操作が約0.4秒で名前解決の失敗に
      なることを確認した（Pixel 8 Proの`cmd netpolicy list restrict-background-blacklist`に
      Play版・デバッグ版の両方のUIDが含まれていた）。アプリを前面にしている間とWi-Fiでは成功する
      ため、利用者からは「モバイル回線のときだけ、たまに通信エラーになる」としか見えず、
      従来の文言「通信エラー（電波状況を確認）」では端末側の設定へ辿り着けなかった。
      (1) `core.SesameStatusFailure`へ`BACKGROUND_RESTRICTED`を追加し、`of`へ
      `backgroundDataRestricted`引数を足した。**応答を1度も受け取れていない**（`httpStatusCode`が
      null）失敗で、かつ制限が掛かっているときだけこの分類にする（応答が返っている時点でOSの制限は
      掛かっていないため、ステータスコードを持つ失敗を端末設定のせいにしない）。`shortLabel`は
      `COMMUNICATION`と同じ「通信エラー」（Tileの5文字では書き分けられず、書き分けても利用者の
      次の行動が変わらないため）、`detailedLabel`だけ「通信エラー（端末の設定を確認）」とした。
      `worstOf`は宣言順（`AUTH_OR_QUOTA` → `BACKGROUND_RESTRICTED` → `COMMUNICATION`）で
      優先する実装へ整理した。
      (2) 判定は`mobile.network.BackgroundDataRestriction`が
      `ConnectivityManager.getRestrictBackgroundStatus()`で行う。データセーバーとアプリごとの制限
      （`POLICY_REJECT_METERED_BACKGROUND`）のどちらも`RESTRICT_BACKGROUND_STATUS_ENABLED`として
      表れ、非従量制の回線では`DISABLED`になるため、「従量制の回線でバックグラウンド通信が
      止められている」ことを1つの値で判定できる。
      (3) `SesameApiAccess`へ`backgroundDataRestricted`を追加して
      `SesameDeviceCommandExecutorFactory`から配線した。既定は常にfalseのため、配線していない
      呼び出し元（テスト・BLEを使わない構成）の分類は従来どおり。分類の呼び出しは
      `SesameDeviceCommandExecutor`の外のトップレベル拡張関数へ置いた（同クラスの関数数が
      detektの`TooManyFunctions`の上限11に達していたため）。
      (4) `mobile.help.HelpContent`へ「モバイル回線のときだけ失敗する」を追加し、確認先
      （アプリごとのバックグラウンドデータとデータセーバーの除外）と、Bluetoothが届く範囲では
      影響を受けないことを説明した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusFailure.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameStatusFailureTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/network/BackgroundDataRestriction.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/help/HelpContent.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTestFixture.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/help/HelpContentTest.kt
      - wear/src/debug/kotlin/com/sesamiwear/wear/debug/SesameWearDebugReceiver.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 自動の品質ゲートはすべて終了コード0。分類の条件（応答なし＋制限ありのときだけ
      `BACKGROUND_RESTRICTED`、応答があれば`COMMUNICATION`、集約の優先順位）と、ヘルプ本文が
      ウィジェットの表示文言と一致することをユニットテストで固定した。
      実機での表示確認（制限を掛けたモバイル回線での操作）はBL-194として人手検証に残す。
    関連ID:
      - BL-192
- date: 2026-09-21 10:35
  summary: BLEで届かないときの対処（セサミ公式アプリの終了）を通知とヘルプで案内する
  details:
    変更内容: >-
      2026-09-21の実機検証で、**セサミ公式アプリを前面で開いている間はSesameが本アプリの探索から
      見つからなくなることがある**と分かった（約10分にわたり`detail=NOT_FOUND`が続き、
      `am force-stop`で公式アプリを止めた直後から見つかるようになり、以降9回連続でBLE経路が成立した）。
      Sesameは同時に1台としかBLEでつながれないため、公式アプリが接続を掴んでいる間は広告が
      届かなくなることによる。利用者からは「近くにいるのにインターネット経由のまま」としか見えず、
      自力で切り分けられないため、対処を2か所で案内するようにした。
      (1) `core.display.SesameRouteLabel`へ`OFFICIAL_APP_HINT`を追加し、
      `mobile.notification.SesameRouteNotifier`が経路をインターネット経由へ切り替えた通知の
      **展開時の本文**（`BigTextStyle`）へ2行目として足す。折りたたみ時の1行は従来のまま
      （短い行に詰め込まないため）。
      (2) `mobile.help.HelpContent`へ「Bluetoothで届かないとき」を追加し、同じ`OFFICIAL_APP_HINT`を
      本文として使う（通知とヘルプで文言が食い違わないよう定数を共有する）。権限・経路の方針・
      Androidのスキャン回数制限も切り分け手順として並べた。
      文言は実測に合わせて「必ず妨げる」ではなく「妨げることがある」とした
      （公式アプリを前面で開いたままでも成功する場合があり、同日の確認では3回中2回が成功した）。
      (3) あわせて、スマートフォンのデバイスカードの経路の行が折り返る不具合を直した。
      失敗の理由が長いとき（「通信エラー（電波状況を確認）」）に経路の語だけが押し出され、
      「Bluet／ooth」と途中で改行されていた。理由側の`Text`へ`weight(1f, fill = false)`と
      `maxLines = 1` / `TextOverflow.Ellipsis`を、経路の語へ`softWrap = false`を与え、
      **経路（アイコン＋語）は必ず1行に収まり、収まらないときは理由側の末尾が省略される**ようにした
      （BL-172(4)の「1行で出る」を維持するため、行を増やす方向では直さない）。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameRouteLabel.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameRouteLabelTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/notification/SesameRouteNotifier.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/help/HelpContent.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/DeviceCard.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/help/HelpContentTest.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py /
      実機（Pixel 8 Pro、デバッグ版）でのヘルプ表示と通知本文の確認
    検証結果: >-
      成功 - 自動の品質ゲートはすべて終了コード0。実機では、ヘルプの「Bluetoothで届かないとき」が
      4段落とも省略・見切れなく表示され、経路を「常にインターネット経由」へ切り替えた操作で出た通知の
      `android.bigText`が「玄関上：Bluetoothで届かないため、インターネット経由で操作します」に続けて
      公式アプリの案内を含むことを`dumpsys notification`で確認した。
      デバイスカードの経路の行も、修正後は「通信エラー（電波状況を確認）」を伴う状態で
      折り返さないことを実機で確認した。
    関連ID:
      - BL-165
      - BL-189
- date: 2026-09-21 01:55
  summary: BLEの失敗理由で到達実績を消すかどうかを分け、圏内なら次の操作で再試行する（BL-191）
  details:
    変更内容: >-
      BLEが1回失敗しただけで、次の到達確認まで（最短5分）BLEを試さなくなる挙動を見直した。
      `SesameBleAccess.recordAttempt`は失敗時に常に`SesameBleReachability.record`へ
      `reachable=false`を渡して到達実績を消していたため、圏外（`NOT_FOUND`）ではなく
      繋げなかっただけ（`CONNECTION_FAILED` / `LOGIN_FAILED`）の場合も同じ扱いになっていた。
      2026-09-20の実機検証では、Sesameの目の前でも後者が起きている。
      (1) `mobile.ble.SesameBleAttempt` / `SesameBleFailure`を追加し、BLEの結果を経路選択が使う
      2値（`NOT_REACHED` / `REACHED_BUT_FAILED`）へ落とす。`SesameBleClient.Result`は6種類あるが、
      経路選択が知りたいのは「圏内にいた証拠があるか」だけのため。`SesameBleAccess`は
      Android非依存のユニットテスト対象なので、Android依存の`SesameBleClient`へ直接は依存させない。
      (2) `SesameBleOperations`の`execute` / `fetchStatus`の戻り値をnullableから
      `SesameBleAttempt`へ変え、失敗理由を`SesameBleAccess`まで運ぶ。
      (3) `SesameBleReachability.record`へ`foundInRange`を足し、圏内の失敗では到達実績を残して
      次の操作でもBLEを試す。連続失敗が`MAX_CONSECUTIVE_FAILURES`（3回）に達したら打ち切って
      実績を消し、毎回BLEの上限（3,000ms）を払い続けないようにする。連続失敗の回数は保存値へ
      持ち（`consecutiveFailures`）、成功と到達確認の記録で数え直す。
      実績の時刻は成功時のみ更新するため、`REACHABLE_TTL_MILLIS`（30分）は最後の成功から数える。
      (4) `SesameDeviceCommandExecutorTest`がdetektの`LargeClass`上限に達したため、
      共通の土台を`SesameDeviceCommandExecutorTestFixture`へ切り出し、BLE経路のテストを
      `SesameDeviceCommandExecutorBleRouteTest`へ分割した（振る舞いの変更は無い）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleAttempt.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleReachability.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameBleAccess.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleAttemptTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleReachabilityTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTestFixture.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorBleRouteTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
      - docs/RELEASE_NOTES.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      すべて成功（終了コード0、markdownlintはSummary 0 issues、validate-recordsはOK）。
      追加したユニットテストは、圏内の失敗で到達実績が残ること・連続3回で打ち切ること・
      成功と到達確認で数え直すこと・実績の時刻が延命されないこと（SesameBleReachabilityTest）、
      `SesameBleClient.Result`から2値への写像（SesameBleAttemptTest）、
      圏内の失敗の直後もBLEを再試行し圏外では倒れること（SesameDeviceCommandExecutorBleRouteTest）。
      実機（Pixel 8 Pro + 実Sesame 5）での再確認は未実施（未確認）で、BL-191へ
      「進行中・実機確認待ち」として残している。
    関連ID:
      - BL-191

- date: 2026-09-20 20:52
  summary: BLE経路を実測に合わせて粘るようにし、経路の変化と到達状況を利用者へ見せる（BL-189 / BL-190）
  details:
    変更内容: >-
      実機検証（BL-165）で判明した2件へ対応した。
      (1) BL-189 - BLE1往復の実測が2.0〜3.5秒あり、経路選択の上限（合計1,800ms / 探索900ms /
      到達確認1,500ms）では毎回打ち切られてインターネット経由へ倒れていた。支配的なのは探索
      （スキャン）のため、見つけたBLEアドレスをuuidごとに覚える SesameBleAddressCache を追加し、
      接続手順を SesameBleConnector へ切り出して**保存済みアドレスへの直接接続を先に試す**形にした。
      探索は、Web APIの通信と並行して走る到達確認（SesameBleClient.probeReachable）へ寄せて
      そこでアドレスを保存する。上限は合計1,800→2,600ms、到達確認1,500→4,000ms、
      到達確認の間隔は15→5分、ウィジェットの受信上限は8→9秒（BLEが倒れたときの最悪値
      2.6+6.0=8.6秒に合わせる。BroadcastReceiverの約10秒制限までは1秒残る）。
      アドレスはログへ出さない。
      (2) BL-190 - フォールバックのトーストが、通知の無効な端末では背景から出せず
      （NotificationService が「Suppressing toast ... by user request」で抑止）、
      ウィジェット操作・ウォッチ操作では一度も出ていなかった。通知
      （mobile.notification.SesameRouteNotifier、POST_NOTIFICATIONS を追加）へ置き換え、
      SesameRouteChangeTracker で**経路が変わった瞬間だけ**出すようにした。
      通知のオン・オフは⋮メニューから選べる（SesameRouteNotificationStore、既定はオン）。
      あわせて、アプリのデバイスカードへBluetoothの到達状況の行を足した
      （core.display.SesameBleConnectionLabel、SesameBleReachability.status）。
      トーストは前景でのみ機能する補助として残している。
      実機検証の途中で、既定のオンのまま通知が未許可の端末から許可へ進む導線が無いことが分かり、
      ダイアログの確定ボタンを「オンにする」／「通知を許可する」／「オフにする」の3通りへ変えた。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameBleConnectionLabel.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameRouteLabel.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameBleConnectionLabelTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameRouteLabelTest.kt
      - mobile/src/main/AndroidManifest.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleAddressCache.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleClient.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleConnector.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleReachability.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleScanner.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameRouteChangeTracker.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameRouteNotificationStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameBleAccess.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/DeviceCard.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/RouteNotificationState.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/SettingsMenu.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/notification/SesameRouteNotifier.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandReceiver.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleAddressCacheTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleReachabilityTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameRouteChangeTrackerTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameRouteNotificationStoreTest.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py /
      実機（Pixel 8 Pro + Pixel Watch 2 + 登録済みのSesame 5）での確認
    検証結果: >-
      成功 - 自動の品質ゲートはすべて終了コード0。実機で次を確認した。
      BLE経路が実際に選ばれて成功し（`route=BLE op=STATUS detail=SUCCESS` / `result=OK`）、
      その操作では「今月のAPI呼び出し回数」が増えない（56→56）。上限調整後の成功率は3回中2回で、
      残り1回は`NOT_FOUND`でインターネット経由へフォールバックし操作自体は成功する。
      経路が変わったときの通知は両方向とも表示される（「Bluetoothで届かないため、インターネット経由で
      操作します」「Bluetoothで直接操作できるようになりました」）。
      デバイスカードは「Bluetooth：圏内（11分前に確認）」「Bluetooth：未確認」を表示する。
      ウォッチの状態一覧とTileに🔗（Bluetooth）と🌐（インターネット）が出る。
      通知をオフにすると出なくなること、同じ経路が続く間は出ないこと、デバイスカードの
      「Bluetooth：圏内／圏外／未確認」、機内モード（Wi-Fiも切り`Active default network: none`）での
      `op=unlock`の成功（API呼び出し回数は増えない）も確認した。BL-190は完了。
      **未確認**: ウィジェットのタップが打ち切られないこと、同じ経路が続く間は通知が出ないこと
      （ユニットテストでは検証済み）、Complicationの経路アイコン。
      BL-189 / BL-190 / BL-165 / BL-182へ残している。
    関連ID:
      - BL-189
      - BL-190
      - BL-165
      - BL-182

- date: 2026-09-20 18:14
  summary: 診断ログの画面を追加し、Wear OSエミュレータでの検証手段を用意する
  details:
    変更内容: >-
      (1) BL-188 - 施錠・解錠・状態取得の成功と失敗を直近50件だけ残す診断ログを追加し、
      上部バーの設定メニューへ「診断ログ」を足した。組み立てと整形はAndroid非依存の
      core.diagnostics（SesameDiagnosticsEntry / SesameDiagnosticsLog）で、
      記録は mobile.command.SesameCommandDiagnostics が SesameDeviceCommandExecutor の
      結果から作る。**uuid・apikey・secretKeyは記録しない**（対象は表示名だけ）。
      画面は全画面ダイアログで「コピー」と「共有」を持ち、共有用の全文には解析に要る
      アプリ版・Androidバージョン・機種名だけをヘッダとして添える。
      保存先は非暗号化SharedPreferences（機密を含まないため）。
      detektの上限に収めるため、実行口の引数は SesameCommandGuard（重複抑止＋診断ログ）へまとめ、
      refreshStatus は when 式へ、SettingsMenu はダイアログ部分を分離した。
      (2) BL-187 - Wear OSのシステムイメージ（android-34 / android-wear / x86_64＝Wear OS 5）を
      導入し、AVD `wearos`（384x384・円形・データ領域2047MB）を作成した。
      2台のエミュレータのペア設定はWear OSコンパニオンアプリがPlayストア入りイメージと
      Googleアカウントを要するため行わず、代わりにwearのdebugビルドにだけ存在する
      SesameWearDebugReceiver を追加して、デバイス一覧と状態のDataItemをウォッチ単体で
      注入できるようにした（mobile側のSesameBleDebugReceiverと同じ形式）
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/diagnostics/SesameDiagnosticsEntry.kt
      - core/src/main/kotlin/com/sesamiwear/core/diagnostics/SesameDiagnosticsLog.kt
      - core/src/test/kotlin/com/sesamiwear/core/diagnostics/SesameDiagnosticsLogTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameCommandDiagnostics.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/diagnostics/DiagnosticsLogDialog.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/diagnostics/DiagnosticsLogFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/SettingsMenu.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/SharedPreferencesKeyValueStore.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunnerTest.kt
      - wear/src/debug/kotlin/com/sesamiwear/wear/debug/SesameWearDebugReceiver.kt
      - wear/src/debug/AndroidManifest.xml
      - docs/INSTALL.md
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt testDebugUnitTest test lintDebug assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py /
      エミュレータ（nocompanion = Android 15、wearos = Wear OS 5）での確認
    検証結果: >-
      成功 - すべて終了コード0。エミュレータで次を確認した。
      診断ログは設定メニューから開き、記録が新しい順に並び、コピーと共有ができる。
      保存値（shared_prefs/sesami_wear_diagnostics.xml）にuuid・apikey・secretKeyが含まれず、
      表示名・操作・結果・時刻だけであることを直接確認した。
      Wear OSエミュレータでは、注入したデバイス一覧と状態がウォッチのアプリ本体の状態一覧へ反映され、
      経路アイコン🔗（Bluetooth）と🌐（インターネット）の両方が表示された。
      Tileでも「🔗11分前」が表示された（スマートフォン未接続のため状態自体は「スマホ未接続」）
    関連ID:
      - BL-187
      - BL-188
- date: 2026-09-20 14:35
  summary: ウィジェットを2マス×1マスでも置けるようにする
  details:
    変更内容: >-
      ウィジェット一覧へ「2 × 1」の項目を追加した（BL-186）。Androidは1つのproviderへ初期サイズを
      1つしか持たせられないため、2マス×1マス用のprovider（sesame_widget_small_info.xml）と
      レシーバ（SesameWidgetSmallReceiver）をもう1組宣言している。
      表示・操作は既存と完全に同じで、SesameWidgetSmallはSesameWidgetを継承しただけ。
      別クラスにしているのはGlanceAppWidgetManagerの都合で、同じ実装クラスを2つのレシーバへ
      割り当てるとクラス→レシーバの対応表が片方で上書きされ、getGlanceIdsが一方を取りこぼして
      再描画が届かなくなるため。SesameWidgetUpdater.updateAllは両方のクラスを走査するようにした。
      対象デバイスの割り当てはappWidgetIdごとで、appWidgetIdはproviderをまたいで一意のため
      2種類が混在しても取り違えは起きない
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetSmall.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetUpdater.kt
      - mobile/src/main/res/xml/sesame_widget_small_info.xml
      - mobile/src/main/res/values/strings.xml
      - mobile/src/main/AndroidManifest.xml
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt testDebugUnitTest test lintDebug assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py /
      エミュレータ（nocompanion、Android 15）での確認
    検証結果: >-
      成功 - すべて終了コード0。エミュレータでウィジェット一覧に
      「Sesami Wear（小）2 × 1」と「Sesami Wear 4 × 2」の2種類が並ぶこと、
      小さい方を置くと最初から2マス×1マスで配置され対象デバイスの選択画面が開くこと、
      2種類を同時に置いても別々のデバイスを対象にでき、状態取得の結果が双方へ反映されることを
      確認した
    関連ID:
      - BL-186
- date: 2026-09-20 14:20
  summary: エミュレータ検証で見つけた表示・レイアウトの不具合3件を修正する
  details:
    変更内容: >-
      BL-182のエミュレータ検証（Pixel 6相当 / Android 15）で観測した3件を修正した。
      (1) BL-183 - 2マス×1マス表示で「▶」が描画されなかった。`NeutralChip`が内部で
      `fillMaxWidth()`を適用しており、`Row`の`defaultWeight()`と競合して先頭のチップが
      全幅を占めていた。`fillWidth`引数を足し、順送りの2チップでは`false`を渡す。
      (2) BL-184 - 同表示で「全デバイス」が「全デバ…」と省略されていた。左1マスを60dpから
      72dpへ広げ、11sp×全角5文字＋チップの内側パディング（計67dp）が収まるようにした。
      (3) BL-185 - 「操作の経路」ダイアログで行頭の文字が左端で欠けていた（●が細い弧にしか見えず、
      折り返し最終行の「消」も半分欠けていた）。`TextButton`の中の`Column(fillMaxWidth)`をやめ、
      Material標準の`RadioButton`＋テキストを`Row`へ並べて行全体を`selectable`にする形へ変えた。
      ●／○の文字による選択表現も廃止した
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetChips.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetMedium.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/RoutePolicyState.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt testDebugUnitTest test lintDebug assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py /
      エミュレータ（nocompanion、Android 15）での再確認
    検証結果: >-
      成功 - すべて終了コード0。エミュレータで3件とも解消を確認した。
      「◀」「▶」が並んで表示され、どちらのタップでも選択画面と同じ順で切り替わること
      （全デバイス → Entrance → Garage、端で回り込み）、「全デバイス」が省略されないこと、
      経路ダイアログの行頭が欠けず選択状態がラジオボタンで分かることを確認している
    関連ID:
      - BL-183
      - BL-184
      - BL-185
- date: 2026-09-20 10:33
  summary: スマホのアプリ画面をダークテーマへ対応させる
  details:
    変更内容: >-
      MaterialThemeへcolorSchemeを渡していなかったためライト固定だった配色を、
      端末の設定へ追随させた（mobile.ui.SesameTheme）。Material Youの動的カラーは採らない。
      施錠状態の色（緑／赤／紫）が壁紙由来の色と競合し、状態の読み取りを鈍らせるため。
      Activityのウィンドウ側は res/values/themes.xml と res/values-night/themes.xml の
      Theme.SesamiWear / Theme.SesamiWear.Dialog へ切り出した（AppCompatを使わないため
      親はplatformのTheme.Material系。DeviceDefaultのDayNightはAPI 29以上のみでminSdk 26に合わない）。
      システムバーのアイコン色は MainActivity.applySystemBarIcons が isSystemInDarkTheme に応じて
      切り替える（BL-103の固定指定を置き換え）。ウィジェットの選択画面・解錠確認画面も同じテーマを使う
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ui/SesameTheme.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetConfigurationActivity.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetUnlockConfirmActivity.kt
      - mobile/src/main/res/values/themes.xml
      - mobile/src/main/res/values-night/themes.xml
      - mobile/src/main/AndroidManifest.xml
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt testDebugUnitTest test lintDebug assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。実機でのダークテーマ表示とシステムバーの判読性の確認は
      BL-182として起票済み
    関連ID:
      - BL-181
- date: 2026-09-20 10:29
  summary: ヘルプと接続の設定を上部バーの設定メニューへ集約する
  details:
    変更内容: >-
      ヘルプ（見出し横のボタン）と接続の設定（操作の経路・Bluetooth権限の2行）を、
      上部バーの⋮メニュー（mobile.credentials.SettingsMenu）へまとめた。3つとも
      たまに開く設定で、主画面へ常時置くと情報量を押し上げるため。
      現在の経路の方針とBluetoothの許可状況はメニュー項目の副題に出し、開くだけで分かるようにした。
      Bluetoothの項目は、許可済みで求めるものが無いときは押せない状態表示になる。
      RoutePolicySection / BlePermissionSection は、状態を返すComposable関数
      （RoutePolicyState / BlePermissionState）とダイアログへ分け、
      detektのMatchingDeclarationNameに合わせてファイル名も変更した
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/SettingsMenu.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/RoutePolicyState.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/BlePermissionState.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt testDebugUnitTest test lintDebug assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。実機での表示確認はBL-182として起票済み
    関連ID:
      - BL-180
- date: 2026-09-20 10:25
  summary: スマホ側の経路表示をMaterialのベクターアイコンへ置き換える
  details:
    変更内容: >-
      アプリ画面のデバイスカードとホーム画面ウィジェットのFULL表示で、経路を絵文字ではなく
      ベクタードローアブルで描くようにした。res/drawableへic_route_bluetooth.xml（Material Iconsの
      bluetooth）とic_route_internet.xml（同 public）を追加し、対応はmobile.ui.SesameRouteIconが持つ。
      色は描画側で与えるためドローアブルは白塗りで、ComposeはIcon＋painterResource、
      GlanceはImage＋ImageProvider＋ColorFilter.tintで描く。
      カードはアイコンと語（Bluetooth／インターネット）を併記する。
      ウィジェットは経路を文言へ前置せずSesameWidgetModel.Configured.routeとして別に持ち、
      描画側（SesameWidgetChips.DetailRow）が画像と文言を横へ並べる（行数は増やさない）。
      ウォッチ側（Tile・Complication）は絵文字🔗／🌐のまま。Complicationの
      SHORT_TEXT/LONG_TEXTはテキストしか持てず画像を埋め込めないため、全面の統一は仕様上できない。
      アイコンの出典（Google Material Icons、Apache-2.0）をREADME.mdのライセンス節へ記載した
      （Googleは表示を義務付けていないが出典として残す）
    変更ファイル:
      - mobile/src/main/res/drawable/ic_route_bluetooth.xml
      - mobile/src/main/res/drawable/ic_route_internet.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ui/SesameRouteIcon.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/DeviceCard.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetChips.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModel.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModelResolverTest.kt
      - README.md
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt testDebugUnitTest test lintDebug assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。パスデータは推測せず、Googleが公開しているMaterial Iconsの
      SVG（fonts.gstatic.com と github.com/google/material-design-icons の2系統）から取得して
      一致を確認している。実機での表示確認はBL-182として起票済み
    関連ID:
      - BL-176
- date: 2026-09-20 10:18
  summary: スマホのアプリ画面を上部バー＋カード一覧＋追加ボタンの構成へ作り替える
  details:
    変更内容: >-
      資格情報設定画面を、TopAppBar＋デバイスのカード一覧＋ExtendedFloatingActionButtonの構成へ
      作り替えた（BL-177）。1台＝1枚のCardとし、(1)施錠状態のアイコンと表示名、
      (2)施錠状態・電池・角度、(3)最終取得と経路、の3行へ分けて1行への詰め込みをやめた。
      2行版の組み立てとして core.display.SesameDeviceStatusLine.lines を追加している
      （既存の1行版 label はこれを組み合わせる形へ変更し、出力は従来と同一）。
      追加・編集は全画面ダイアログ CredentialsEditorDialog へ移し（BL-178）、
      保存完了はSnackbarで知らせる。削除には確認ダイアログを追加した（BL-179）。
      画面全体を単一のLazyColumnにして縦スクロールできるようにした
      （従来はColumnの中にLazyColumnが入れ子で、一覧の外側はスクロールできなかった）。
      detektのLongMethod（上限60行）を避けるため、保存・削除と同期の呼び分けを
      CredentialsScreenControllerへ、ヘルプのダイアログをHelpDialogsへ分離している。
      DeviceListSection.kt は DeviceCard.kt へ置き換えた
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameDeviceStatusLine.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameDeviceStatusLineTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsScreenController.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsEditorDialog.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/DeviceCard.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/HelpDialogs.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/DeviceListSection.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt testDebugUnitTest test lintDebug assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。ktlintの「A multiline expression should start on a new line」は
      ktlintFormatで解消した。実機での表示確認はBL-182として起票済み
    関連ID:
      - BL-177
      - BL-178
      - BL-179
- date: 2026-09-20 10:10
  summary: ウィジェットの下限を2マス×1マスへ上げ、◀▶でのデバイス順送りを追加する
  details:
    変更内容: >-
      ホーム画面ウィジェットの縮小の下限を1マスから横2マス×縦1マスへ引き上げ、高さ1マスのときの
      表示（SesameWidgetLayout.MEDIUM）を新設した。左1マス（60dp）にデバイス名チップと「◀ ▶」、
      右1マスに状態アイコンと状態文言を出す。◀▶は対象デバイスの順送りで、巡回する並びは
      選択画面と同じ（SesameDeviceTargets.choices）、端で反対側へ回り込む。
      判定はAndroid非依存のWidgetDeviceCycleへ置き、受信はWidgetCommandReceiverの
      ACTION_CYCLE_DEVICEが担う（割り当てを保存して当該インスタンスだけ再描画。Sesame APIは呼ばない）。
      FULL（高さ2マス以上）の左列は従来どおり「変更」のままにした（ユーザー指示）。
      幅が足りない表示領域が来た場合の保険として、従来の1マス表示はCOMPACTとして残している。
      1ファイルの関数数がdetektのTooManyFunctions（上限11）に達したため、Glanceの部品を
      SesameWidget / SesameWidgetMedium / SesameWidgetChips の3ファイルへ分割した。
      あわせてWidgetUnlockConfirmActivityの重複定数（NEUTRAL_TEXT_ARGB / CHIP_CORNER_RADIUS_DP）を
      共用へ寄せた
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayout.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetMedium.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetChips.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetDeviceCycle.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandReceiver.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetUnlockConfirmActivity.kt
      - mobile/src/main/res/xml/sesame_widget_info.xml
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayoutTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetDeviceCycleTest.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt / ./gradlew testDebugUnitTest test /
      ./gradlew lintDebug assembleDebug / npx markdownlint-cli2 "**/*.md" /
      python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。SesameWidgetLayoutTest（3段階の判定）と
      WidgetDeviceCycleTest（順送り・回り込み・削除済みデバイスの扱い）を追加している。
      実機での表示確認はBL-182として起票済み
    関連ID:
      - BL-174
      - BL-175
- date: 2026-09-20 09:57
  summary: 経路アイコンを📶／☁から🔗／🌐へ変更する
  details:
    変更内容: >-
      Tile・Complication・ウォッチの状態一覧・ホーム画面ウィジェットで使う経路アイコンを、
      📶（BLE）／☁（Web API）から🔗／🌐へ変更した。📶は携帯電話の電波強度として広く使われており、
      Bluetoothでの直接操作を表すものとして読み取れないという指摘による。
      Unicodeに「Bluetooth」の絵文字は存在せず（ロゴはルーン文字の合字で絵文字フォントに含まれず、
      端末によっては豆腐になる）、搭載率の高いEmoji 1.0の範囲から選んでいる。
      文字数は従来と同じ1文字分のため、表示幅の要件（BL-102 / BL-104 / BL-158）に影響しない。
      DESIGN.mdの実機検証記録に残る📶／☁は当時観測した表示のため書き換えず、
      現在は変更済みである旨の注記を先頭へ足した
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameRouteLabel.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameStatusDetail.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameDeviceStatusLine.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameRouteLabelTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameStatusDetailTest.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/status/SesameStatusListContentTest.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt / ./gradlew testDebugUnitTest test /
      ./gradlew lintDebug assembleDebug / npx markdownlint-cli2 "**/*.md" /
      python scripts/validate-records.py
    検証結果: >-
      成功 - すべて終了コード0。markdownlintは37ファイルで0 issues、
      記録ファイル検証はBACKLOG 15件・EXECUTE 116件で OK
    関連ID:
      - BL-173
- date: 2026-09-19 21:00
  summary: ホーム画面ウィジェットのFULL表示へ電池残量を併記する
  details:
    変更内容: >-
      ウィジェットのFULLレイアウトで、電池残量を最終取得時刻と同じ行へ併記するようにした。
      高さ予算が既に埋まっている（最小140dpに対して約136dp使用）ため行は増やしていない。
      COMPACTでは出さない。「全デバイス」対象では最も少ない台の値を代表値にする
      （失敗・鮮度と同じく、利用者が対処すべき側を見せる）。1台も分かっていなければ出さない。
      電池の書き方はcore.display.SesameTileContent.batteryLabelへ切り出し、
      ウォッチの状態一覧（BL-170）と共通にした。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameTileContent.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModel.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModelResolverTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/status/SesameStatusListContent.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。未取得なら行が従来どおりであること、複数台では
      最も少ない値が出ること、デモ用デバイスでは出ないことをユニットテストで確認した。
      実機での収まりはBL-172で確認する。
    関連ID:
      - BL-171

- date: 2026-09-19 20:30
  summary: ウォッチのアプリ本体を登録済みセサミの状態一覧にする
  details:
    変更内容: >-
      「Sesami Wear」と表示するだけのスタブだったwear.MainActivityを、登録済みセサミの
      状態一覧へ置き換えた。1台につきデバイス名・施錠状態と電池残量・最終取得時刻と経路アイコンの
      3行を出す。円形画面では行が長いと行頭・行末が見切れるため、スマートフォンの1行表示は
      そのまま使わず短い行へ分け、各行が幅の目安に収まることをユニットテストで固定した。
      角度は狭い画面では情報量が勝ちすぎるため出さない。施錠状態のアイコンと文言はTileと同じ
      ものを使う。この画面から状態取得のリクエストは送らず、最後に同期された値をそのまま出す。
      画面の再開を契機に読み直すため、wearへlifecycle-runtime-composeを追加した
      （mobileと同じ版で依存グラフは変わらない）。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/status/SesameStatusListContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/status/SesameStatusListScreen.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/MainActivity.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/status/SesameStatusListContentTest.kt
      - wear/build.gradle.kts
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。SesameStatusListContentTestは8件が成功し、
      各行が円形画面の幅の目安（全角11文字相当）に収まることも確認した。
      実機での表示確認はBL-172で行う。
    関連ID:
      - BL-170

- date: 2026-09-19 19:50
  summary: スマートフォンのデバイス一覧へセサミの状態を1行で表示する
  details:
    変更内容: >-
      資格情報設定画面のデバイス一覧で、各デバイスの名前の下に施錠状態・電池残量・角度・
      最終取得時刻・経路を1行で出すようにした。分かっていない項目は出さず、何も分かっていなければ
      「未取得」だけになる。組み立てはcore.display.SesameDeviceStatusLineへ切り出し、
      ウォッチの状態一覧（BL-170）と共用できるようにしている。
      経路はアイコンではなく語で書く（この画面は表示領域に余裕があるため）。角度はこの画面でだけ
      出す（生の値で意味を読み取りにくいため）。画面の再開時に読み直すが、この表示のために
      状態取得のリクエストは送らない。
      ファイルあたりの関数数の上限に達したため、デバイス一覧をDeviceListSection.ktへ分離した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameDeviceStatusLine.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameDeviceStatusLineTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/DeviceListSection.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。分かっていない項目が出ないこと、失敗時は鮮度の代わりに
      理由が出ることをユニットテストで確認した。実機での表示確認はBL-172で行う。
    関連ID:
      - BL-169

- date: 2026-09-19 19:10
  summary: 経路を表示し、BLEからフォールバックしたときにトーストで知らせる
  details:
    変更内容: >-
      Tile・Complication・ホーム画面ウィジェットの「最終取得時刻」の行へ経路アイコン
      （BLE=📶、インターネット=☁）を前置するようにした。3つの面はいずれも表示余白を使い切って
      おり、過去に文言が収まらず省略された事例があるため行は増やしていない。
      アイコンが1コードポイントに収まることをユニットテストで固定した。
      「全デバイス」対象の集約表示では、全デバイスが同じ経路のときだけアイコンを出す。
      BLEを試したのに届かずWeb APIへ倒れた場合は、スマートフォンでトーストを出す。
      そもそもBLEを試していない場合（到達実績が無い・権限が無い・方針が常にインターネット経由）は
      出さない。通知ではなくトーストにしたのは、Android 13以降の通知権限の要求と
      データセーフティ申告を増やさないため。
      SesameBleAccessの引数が上限に達したため、BLEの実行3種をSesameBleOperationsへまとめた。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameRouteLabel.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameStatusDetail.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameStatusDetailTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameBleAccess.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModel.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileStateResolver.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。フォールバック通知が「実際に試して失敗したときだけ」
      出ることを4通りのケースで確認した。実機での表示の収まりはBL-172で確認する。
    関連ID:
      - BL-168

- date: 2026-09-19 18:20
  summary: 経路の方針（自動/常にインターネット経由）を設定画面から選べるようにする
  details:
    変更内容: >-
      core.SesameRoutePolicyと保存先（SesameRoutePolicyStore）を追加し、SesameBleAccessが
      操作のたびに読み直すようにした。「常にインターネット経由」を選ぶとBLEの探索・接続・
      到達確認をいずれも行わない。圏外で操作できなくなるため「Bluetooth固定」は用意せず、
      選択肢が2つのままであることをユニットテストで固定した。
      設定画面には現在の方針の1行と「変更」だけを置き、選択肢と説明はダイアログへ回している
      （画面が縦スクロールしないため）。経路の方針とBLE権限はどちらも「どうやってつなぐか」の
      設定のため、ConnectionSettingsSectionとして隣り合わせにまとめた。
      表示文言はcore.display.SesameRouteLabelへ置き、wearとmobileで食い違わないようにしている。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameRoutePolicy.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameRouteLabel.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameRouteLabelTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameRoutePolicyStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameBleAccess.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/RoutePolicySection.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameRoutePolicyStoreTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。「常にインターネット経由」で施錠/解錠・状態取得とも
      BLEを試さず、到達確認のスキャンも行わないことをユニットテストで確認した。
    関連ID:
      - BL-167

- date: 2026-09-19 17:40
  summary: 状態のスナップショットへ電池残量・角度・経路を追加する
  details:
    変更内容: >-
      core.SesameStatusSnapshotへbatteryPercentage / position / lastRouteを追加し、
      保存（LockStateStore）とウォッチへのDataItem同期まで通した。分からなかった項目は
      前回の値を残すmergeを用意し、Web API経由の施錠/解錠では経路だけが更新されるようにした。
      電池残量はBLE専用ではなく、Sesame Web APIの状態取得レスポンスも電圧と角度を返している。
      これまで捨てていた値を使うようにし、電圧から残量への換算表をmobile.bleからcoreへ移して
      両経路で同じ換算を通す。BLE経由の施錠/解錠ではログイン直後の機構状態から電池残量も
      更新するが、角度はコマンド送信前の値になるため使わない。
      保存値・DataItemのいずれも、キーが無い場合は未取得として扱うため旧バージョンと互換がある。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusRoute.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameBatteryLevel.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusMeasurement.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusSnapshot.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusSnapshotFactory.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameBatteryLevelTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameStatusSnapshotFactoryTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleMechStatus.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleClient.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameBleAccess.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameStatusSyncer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/LockStateStore.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/ble/SesameBleDebugReceiver.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/state/LockStateStoreTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameStatusSnapshotReader.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。旧形式の保存値を読んでも壊れないことと、
      分からなかった項目が前回の値を残すことをユニットテストで固定した。
    関連ID:
      - BL-166

- date: 2026-09-19 16:20
  summary: BLE直接操作の権限をマニフェストへ宣言し、任意で許可を求めるUIを追加する
  details:
    変更内容: >-
      AndroidManifestへBLUETOOTH_SCAN（neverForLocationを宣言）・BLUETOOTH_CONNECT・
      ACCESS_FINE_LOCATION（maxSdkVersion 30）と、required=false の bluetooth_le を追加した。
      資格情報設定画面には許可状況の1行と「設定」ボタンを置き、要求の直前に説明ダイアログを出す。
      説明には、何に使うのか・許可しなくても従来どおりWeb API経由で動くこと・API 30以下で必要な
      位置情報を近くの機器の探索にのみ使い収集も送信もしないことを含めた。
      一度拒否された場合は端末のアプリ設定画面を開く導線へ切り替えるため、要求済みかを
      SesameBlePermissionAskedStoreへ保存する。設定画面は縦スクロールしないため、
      長い説明は常時表示せずダイアログへ回している。
      あわせて docs/USER_GUIDE.md と docs/RELEASE_NOTES.md を追随させた（BL-154の一部）。
    変更ファイル:
      - mobile/src/main/AndroidManifest.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBlePermissionPrompt.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBlePermissionAskedStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/BlePermissionSection.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBlePermissionPromptTest.kt
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。SesameBlePermissionPromptTestは10件が成功。
      実機での許可・拒否の挙動確認はBL-165（人手検証）に含める。
    関連ID:
      - BL-153

- date: 2026-09-19 15:05
  summary: 施錠/解錠と状態取得の経路選択（BLE優先・Web APIフォールバック）を実装する
  details:
    変更内容: >-
      SesameDeviceCommandExecutorが、施錠/解錠と状態取得の両方でBLEとWeb APIを使い分けるようにした。
      uuidごとの到達実績（SesameBleReachability）を非暗号化SharedPreferencesへ保存し、直近30分以内に
      BLEで到達できたデバイスだけBLEを先に試す。成功時はWeb APIを呼ばないため月間リクエスト上限を
      消費しない。到達実績が無い場合はWeb APIから始め、その通信と並行してスキャンだけの到達確認を
      15分に1回まで行う。BLEが失敗したら到達実績を消してWeb APIへ倒す。
      利用者からは経路が見えず、どちらで実行したかは診断ログにのみ残す（uuidは先頭8文字のみ）。
      引数の数を抑えるためWeb APIとBLEの経路をSesameRouteAccessへまとめ、既存の呼び出し元を追随させた。
      資格情報を削除したデバイスの到達実績はRemovedDeviceCleanerが消す。
      実行時間の設計値（全体1800ms）はウィジェットの8秒制限から逆算したもので、実測値ではない。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleReachability.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleClient.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameBleAccess.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/SharedPreferencesKeyValueStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/RemovedDeviceCleaner.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleReachabilityTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunnerTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。SesameDeviceCommandExecutorTestは33件
      （BLE経路の9件を追加）、SesameBleReachabilityTestは13件がいずれも成功。
      実行時間の設計値の妥当性はBL-165（人手検証）で実測する。
    関連ID:
      - BL-152

- date: 2026-09-19 14:10
  summary: BLE直接操作のクライアントをmobile.bleへ自前実装する
  details:
    変更内容: >-
      公式SesameSDKの取り込みを断念した判断（DESIGN.md「公式SDK取り込みの実測」）を受け、
      Sesame OS3のBLEプロトコルをmobile.bleへ自前実装した。AES-CCM（RFC 3610）は
      AndroidのJCEが提供しないため、AES/ECB/NoPaddingの上へcore.crypto.AesCcmとして
      組み立てた。プロトコル層（パケット分割・組み立て、セッション鍵の導出と暗号化・復号、
      メッセージの解釈、機構状態とアドバタイズの解釈、権限の判定）はAndroid非依存の
      クラスへ切り出してユニットテストで検証し、GATT接続とスキャンだけをAndroid依存にした。
      この段階では経路の自動切り替えを入れていない（BL-152で対応）。
      検証用のブロードキャスト受信口はsrc/debugにのみ置き、リリースビルドには含めない。
      期待値はRFC 3610の公開テストベクタとPyCryptodomeで生成し、実資格情報は使用していない。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/crypto/AesCcm.kt
      - core/src/test/kotlin/com/sesamiwear/core/crypto/AesCcmTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleProtocol.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBlePacketCodec.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleSession.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleMessage.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleMessageReader.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleMechStatus.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleAdvertisement.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBlePermissions.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleScanner.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleConnection.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/ble/SesameBleClient.kt
      - mobile/src/debug/kotlin/com/sesamiwear/mobile/ble/SesameBleDebugReceiver.kt
      - mobile/src/debug/AndroidManifest.xml
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBlePacketCodecTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleSessionTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleMessageTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleMechStatusTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBleAdvertisementTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/ble/SesameBlePermissionsTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / python scripts/validate-records.py
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。markdownlintはSummary 0 issues、
      記録ファイルのYAML検証もOK。実機での疎通確認はBL-165（人手検証）として起票した。
    関連ID:
      - BL-151

- date: 2026-09-18 23:40
  summary: 記録ファイルのYAML検証をスクリプト化し品質ゲートの定義の乖離を解消した
  details:
    変更内容: >-
      品質ゲートの定義が`CLAUDE.md`と`CONTRIBUTING.md`・`.github/PULL_REQUEST_TEMPLATE.md`の間で
      乖離しており、「記録ファイルのYAML検証」が参照側に無かった（BL-163）。検証手順は
      `FORMAT.md`の文章でのみ定義されていたため、実行のたびにエージェントが使い捨てのスクリプトを
      書いており再現性が無かった。`scripts/validate-records.py`を追加してコマンド1つで実行できる
      ようにし、`CLAUDE.md`の品質ゲート表・`CONTRIBUTING.md`「品質ゲート」・PRテンプレートの
      チェックリスト・`README.md`「ビルド・実行・テスト」の4箇所から同じコマンドを参照させた。
      スクリプトはマーカー内のYAML読み込みに加えて、`BACKLOG.md`の必須キーと`優先度`・`状態`の
      許容値、改行コードがLFであることを検査する（いずれも`FORMAT.md`が定める体裁）。
      `CONTRIBUTING.md`と`.github/`配下はルールファイルのため、変更前にユーザーの承認を得ている。
    変更ファイル:
      - scripts/validate-records.py
      - CLAUDE.md
      - CONTRIBUTING.md
      - .github/PULL_REQUEST_TEMPLATE.md
      - README.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      python scripts/validate-records.py / npx markdownlint-cli2 "**/*.md" /
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: >-
      成功 - 正常な記録ファイルで終了コード0。意図的に壊した記録ファイル（「半角コロン＋半角空白」を
      含むプレーンスカラー、必須キー欠落、許容値外の`優先度`・`状態`）を一時ディレクトリへ置いて
      実行し、終了コード1と該当箇所の指摘が出ることを確認した。markdownlintは0 issues。
    関連ID:
      - BL-163

- date: 2026-09-18 18:40
  summary: BL-156〜BL-160の修正をPixel 8 Pro + Pixel Watch 2で実機検証した
  details:
    変更内容: >-
      本ブランチで修正したBL-156 / BL-157 / BL-158 / BL-159 / BL-160の5件を、adb経由で実機検証した。
      実資格情報は使わず、ダミー資格情報2台（DevA=`MOCKA1` / DevB=`MOKB2`）とモックAPI
      （`scripts/mock-sesame-api.py`と`-PsesameApiBaseUrl`、BL-132）で状態を作っている。
      ウォッチのタイルカルーセルは`input swipe`で移動できないため、Tileのタップは
      `SesameActionActivity`（exported）を`am start`で直接起動して再現した。
      結果はDESIGN.md「実機検証（BL-156 / BL-157 / BL-158 / BL-159 / BL-160、2026-09-18）」へ
      記載し、5件をBACKLOGから削除した。コードの変更は無い（記録ファイルのみ）。
      後始末として、ダミー資格情報の削除、検証用ウィジェットの撤去、BL-149の検証で残っていた
      孤立ロック状態の削除、モックサーバーの停止、ウォッチの`screen_off_timeout`の復元（30000）、
      本番URLでのデバッグ版の入れ直しを行った。
    変更ファイル:
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      adb -s <スマホ> shell run-as com.sesamiwear.mobile.debug cat shared_prefs/... /
      adb -s <スマホ> logcat -d | grep -i vibrat / adb exec-out screencap /
      adb -s <ウォッチ> shell am start -n ...SesameActionActivity
    検証結果: >-
      成功 - BL-157は「全デバイス」操作で2台が同一ミリ秒で更新（修正前は片方が失われていた）。
      BL-156はウィジェット操作でアクチュエータが動作し`Ignoring incoming vibration`は0件。
      BL-158は4マス×1マスでCOMPACTへ切り替わり見切れ無し。BL-159は画面の再開で23回から25回へ更新。
      BL-160は削除したuuidだけがロック状態とウィジェット割り当てから消えた。
      ウォッチのDataItem削除はベストエフォートの失敗ログが出ないことのみで確認しており、
      DataItemの中身は未確認。
    関連ID:
      - BL-156
      - BL-157
      - BL-158
      - BL-159
      - BL-160

- date: 2026-09-18 18:00
  summary: 資格情報を削除したデバイスの残存状態を消すようにした
  details:
    変更内容: >-
      BL-160: 削除は`credentialsStore.remove(uuid)`だけを行っており、`LockStateStore.remove`は
      本体のコードから呼ばれていなかった。同じuuidを登録し直すと、一度も取得していないのに
      削除前の施錠状態と失敗文言がそのまま表示されていた。
      `mobile.state.RemovedDeviceCleaner`を追加し、`CredentialsSettingsScreen`の削除操作から
      呼ぶようにした。消す対象は(1)`LockStateStore`のロック状態、(2)ホーム画面ウィジェットの
      対象デバイス割り当て、(3)ウォッチへ同期済みのDataItem
      （`SesameWearProtocol.statusDataItemPath`、`wear://`のURIで`deleteDataItems`）の3つ。
      (2)は表示自体が未登録uuidなら「タップして設定」へ倒れるため必須ではないが、同じuuidを
      登録し直したときに利用者が設定し直していないウィジェットが黙って結び付くため消す判断とした。
      (3)はBACKLOGに挙がっていなかったが、残すとウォッチ側で同じ症状が出るため同時に消す。
      DataItemの削除はsuspendかつ失敗しても削除操作を止めてはならないため、
      `DataLayerBestEffort`で包み、画面のコルーチンスコープで実行する。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/RemovedDeviceCleaner.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。保存先をつなぐだけのAndroid依存アダプタのためユニットテストは
      追加していない（各ストアの削除そのものは既存のテストで検証済み）。実機での確認は
      本ブランチの最後に行う。
    関連ID:
      - BL-160

- date: 2026-09-18 17:55
  summary: 今月のAPI呼び出し回数を画面の再開ごとに読み直すようにした
  details:
    変更内容: >-
      BL-159: `CredentialsSettingsScreen`が`remember`でコンポジション生成時に1回だけ
      呼び出し回数を読んでいたため、Activityが破棄されずに再表示された場合（ホームへ退避してから
      戻った場合など）に古い値が残っていた。`rememberApiUsageCount`へ切り出し、
      `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)`で読み直すようにした。
      `LifecycleRegistry`は追加した監視者へ現在の状態までのイベントを送るため、初回表示でも
      同じ経路で読まれる（初期値も同じ値で組み立てるため表示のちらつきは無い）。
      切り出しは`CredentialsSettingsScreen`がdetektの`LongMethod`（60行）へ達したための対応も
      兼ねる。
      直接使う`androidx.lifecycle:lifecycle-runtime-compose`を`libs.versions.toml`と
      `mobile/build.gradle.kts`へ明示した。compose-uiが推移的に持ち込む版と同一（2.8.7、
      `lifecycle-runtime-ktx`と同じバージョン参照）のため、依存グラフのバージョンは変わらない。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/build.gradle.kts
      - gradle/libs.versions.toml
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。Compose画面のためユニットテストの対象外で、表示の更新は
      実機での確認を本ブランチの最後に行う。
    関連ID:
      - BL-159

- date: 2026-09-18 17:45
  summary: 横長へ縮めたウィジェットで文言が見切れないようレイアウトのしきい値を上げた
  details:
    変更内容: >-
      BL-158: `SesameWidgetLayout.FULL_MIN_HEIGHT_DP`が100dpだったため、高さ1マス
      （Pixel 8 Pro + Nova Launcherで約128dp）でも`FULL`が選ばれ、4要素
      （アイコン・状態文言・最終取得時刻または失敗文言・操作文言）が入りきらず操作文言が
      縦に見切れていた。`SesameWidget`の文字サイズとパディングから必要な高さを見積もると
      約136dpのため、余裕を見て140dpへ引き上げた。`SizeMode.Responsive`へ渡す候補サイズは
      同じ定数を参照しているため、候補・判定ともに140dpで揃う。
      対策候補のうち中間レイアウトは採らなかった。4要素のうち何を落とすかの判断が必要で、
      0.12.0で追加した最終取得時刻・失敗文言（BL-140 / BL-142）を隠すことになるため、
      横長では`COMPACT`（アイコンと状態文言のみ）へ落とす方を選んだ。
      既定の配置（4x2）は2マス分の高さがあり140dpを上回るため`FULL`のままになる。
      `sesame_widget_info.xml`の`minHeight`は、API 30以下で既定の配置が3マスへ広がるのを
      避けるため110dpのまま変更していない（2マス＝約140dpとなり`FULL`が選ばれる）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayout.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayoutTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。4x1相当（250x128dp）で`COMPACT`が選ばれること、
      既定の4x2相当では`FULL`のままであることをユニットテストで確認した。
      実機での見え方の確認は本ブランチの最後に行う。
    関連ID:
      - BL-158

- date: 2026-09-18 17:40
  summary: ホーム画面ウィジェットの操作で振動が鳴るよう用途を指定した
  details:
    変更内容: >-
      BL-156: `mobile.haptics.SesameHapticPlayer.play`が用途を指定せずに
      `Vibrator.vibrate(VibrationEffect)`を呼んでいたため、用途がUNKNOWNとなり、
      アプリがバックグラウンドのまま実行されるウィジェット操作ではシステムが振動を破棄していた
      （`VibratorManagerService: Ignoring incoming vibration ... is background`）。
      Android 13（API 33）以上では`VibrationAttributes.USAGE_HARDWARE_FEEDBACK`を指定する
      オーバーロードへ、未満では[AudioAttributes]版（`USAGE_NOTIFICATION`へ写像される）へ
      切り替えた。いずれもバックグラウンドからの振動が許可される用途で、
      `USAGE_HARDWARE_FEEDBACK`は利用者のタップに対する手応えという意味に最も近く、
      サイレントモードや通知の設定に左右されないため既定に選んだ。
      `USAGE_HARDWARE_FEEDBACK`へ写像できる[AudioAttributes]の用途が存在しないため、
      API 33未満だけ用途が`USAGE_NOTIFICATION`になる点はコメントへ残した。
      wear側の同名クラスは前面のActivityから鳴らしており実機で成功しているため変更していない。
      あわせて、0.11.0で配信済みの不具合であるBL-157の修正を`docs/RELEASE_NOTES.md`の
      0.12.0へ「修正」として追記した（BL-156の振動は0.12.0の新機能で未配信のため追記不要）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/haptics/SesameHapticPlayer.kt
      - docs/RELEASE_NOTES.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。実際に鳴るかはAndroid依存のためユニットテストの対象外で、
      実機での確認は本ブランチの最後に行う。
    関連ID:
      - BL-156
      - BL-157

- date: 2026-09-18 17:35
  summary: 全デバイス操作でロック状態の一部が失われる競合を修正した
  details:
    変更内容: >-
      BL-157: `SesameMessageListenerService`がメッセージごとに別コルーチンで
      `SesameDeviceCommandExecutorFactory.create`を呼ぶため、「全デバイス」操作では
      デバイス数ぶんの`LockStateStore`インスタンスが並行して単一キー（`lock_states`）へ
      read-modify-writeを行っていた。`@Synchronized`はインスタンス単位のロックのため
      排他にならず、後勝ちで一方の更新が失われ、実際には解錠されているのに「施錠中」と
      表示され続けていた。
      `LockStateStore`の`load` / `save` / `saveFailure` / `remove`を、ファイルスコープの
      共有ロック`LOCK`による`synchronized(LOCK)`へ置き換えた。対策候補のうちクラス単位の
      ロックを選んだのは、保存先が同一プロセス内のSharedPreferences1ファイルのみで
      プロセス内の排他で足り、呼び出し側（Service・ウィジェット・設定画面）の生成方法を
      変えずに済むため。シングルトン化はContextの保持先を増やし、1コルーチンへの直列化は
      `SesameMessageListenerService`の構造変更を伴うため採らなかった。
      検出できる回帰テストとして、読み出しに20ms要する保存先へ8スレッドが別インスタンスから
      同時に`save`するユニットテストを追加した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/LockStateStore.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/state/LockStateStoreTest.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug
    検証結果: >-
      成功 - 全ゲート終了コード0。追加したテストが修正前は失敗し（一部uuidの保存が消える）、
      修正後は8デバイスぶんすべて残ることを確認した。実機での確認は本ブランチの最後に行う。
    関連ID:
      - BL-157

- date: 2026-09-18 17:50
  summary: 状態取得の連打を抑止してSesame APIの無駄な消費を防いだ
  details:
    変更内容: >-
      BL-148: 施錠/解錠は`CommandDebouncer`が同一uuidへの2秒以内の重複を無視するが、状態取得は
      対象外で、Tileのデバイス名チップやウィジェットのデバイス名を連打するとその回数ぶんGETが
      飛んでいた。BL-142で自動状態取得を廃止し、Sesame Web APIの消費が利用者のタップ回数と
      等しくなったため、誤タップ・二度押しがそのまま月間リクエスト上限（BL-141）へ効くように
      なっていた。
      `SesameDeviceCommandExecutor.refreshStatus`を`CommandDebouncer`の対象へ加えた。抑止した
      場合はAPIを呼ばず保存済みの状態を返し、失敗ではないため失敗の記録（BL-140）も残さない。
      施錠/解錠と状態取得は`cmd:{uuid}` / `status:{uuid}`と別のキーで数えるため、施錠した直後に
      状態を取り直すことはできる（BL-061の巻き戻り防止と衝突させない）。「全デバイス」対象の
      タップで登録台数ぶん飛ぶのは意図した動作のため対象外（uuidが異なる）。
      間隔は施錠/解錠と同じ2秒とした。「連打」の定義を経路で揃えるためで、二度押し・誤タップは
      確実に弾き、「取れなかったのでもう一度」という意図的な再試行（通常は2秒以上あく）は通す。
      利用者が明示的に意図した取得は抑制しないという方針（BL-142）を崩さない範囲で最大の効果を
      取る値として選んだ。
      detektの`TooManyFunctions`（11）に達したため、キーの組み立ては関数を足さず
      companion objectの接頭辞定数と文字列連結で行い、`refreshStatus`は`ReturnCount`（2）に
      収まるよう式へ書き直した。
      既存テスト`a later success clears the recorded failure`は連続2回の取得を行っていたため、
      2回目が抑止されないよう時間を進めるよう修正した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。追加したユニットテストで、窓内の2回目がAPIを呼ばず保存済みの
      状態を返すこと、窓を超えれば再び呼ばれること、別デバイスは抑止されないこと、抑止された
      呼び出しが失敗の記録を上書きも消去もしないことを確認した。実機での確認はBL-149へ含めた。
    関連ID:
      - BL-148

- date: 2026-09-18 17:10
  summary: 今月のSesame Web API呼び出し回数を数えて資格情報設定画面へ表示するようにした
  details:
    変更内容: >-
      BL-147: Web APIの月間リクエスト上限（BL-141）に達するとアプリ全体が機能停止するが、利用者が
      「今月どれだけ使ったか」を知る手段がアプリ内に無く、SESAME Bizのサイトを見に行くしかなかった
      問題に対応した。
      `mobile.state.ApiUsageCounter`（Android非依存、ユニットテスト対象）を追加し、暦月ごとに
      呼び出し回数を数える。`SesameApiAccess`へ`recordApiCall`を足し、実際にAPIを呼ぶ直前に
      成否によらず数える（上限は成功・失敗を問わず消費されるため）。デモ用デバイス・重複として
      無視した操作・資格情報が無い場合はAPIを呼ばないため数えない。
      保存値は「対象の年月」と「回数」の2つだけで機密情報を含まないため、保存先は非暗号化
      SharedPreferences（`sesami_wear_api_usage`）。月の境界を判定するタイムゾーンは注入可能で、
      既定は端末のタイムゾーンとした（CANDY HOUSE側のカウンタがどのタイムゾーンで月を区切るかは
      未確認のため、利用者の体感に合う側を既定とする。目安である旨の明示と合わせて許容する）。
      表示は資格情報設定画面の見出し直下。数えるのはこのアプリからの呼び出しだけで他経路の消費を
      含まず、上限値そのものも契約内容によって変わりアプリからは取得できないため、「上限までの残り」
      ではなく消費の目安として出す。文言へ「このアプリからの分のみ・目安」を含めることを
      ユニットテストで固定した。
      画面の関数がdetektの`LongMethod`閾値（60）に達したため、見出しと呼び出し回数の表示を
      `ScreenHeader`へ切り出した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/ApiUsageCounter.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/SharedPreferencesKeyValueStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/state/ApiUsageCounterTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。追加したユニットテストで、同月内の加算、月が変わったときの
      数え直し、過去月の参照が0になり保存値を壊さないこと、月の境界がタイムゾーンに従うこと
      （JSTでは月をまたぐがUTCではまたがない時刻で確認）、壊れた保存値を0として扱うこと、
      表示文言が目安である旨を含むことを確認した。実機での表示確認はBL-149へ含めた。
    関連ID:
      - BL-147

- date: 2026-09-18 16:20
  summary: ホーム画面ウィジェットの施錠/解錠の成否をスマートフォンの振動でも伝えるようにした
  details:
    変更内容: >-
      BL-129: ウォッチはハプティクスで成否を区別していたが、ウィジェットは失敗時に操作前の表示へ
      戻すだけで分かりにくかった問題に対応した。起票時の3点のうち「失敗を一定時間表示する状態」は
      BL-140で（一定時間ではなく次の成功まで持続する形で）、「状態が古い場合の表示の区別」は
      BL-142で対応済みのため、残っていた「スマートフォンのハプティクス」を実装して本項目を閉じる
      （2026-09-18、ユーザー確認済み）。
      wearのTile経由とmobileのウィジェット経由で手触りを揃えるため、`wear.haptics.HapticPattern`と
      `SesameHapticPatternResolver`を`core.haptics`へ移し、波形（`timingsMillis`）も
      `HapticPattern`が持つようにした。`SesameHapticPlayer`はAndroid依存でcoreへ置けず、
      mobileはwearへ依存できないため、同じ実装をmobile側にも持たせている（共通化できるのは
      波形の定義まで）。
      鳴らすかどうかの判定は`mobile.widget.WidgetHapticResolver`（Android非依存）が持つ。
      「全デバイス」対象では登録台数ぶんの結果が返るため、1台でも失敗していればFAILURE、
      すべてDEBOUNCED（連打として無視）なら鳴らさない、それ以外はSUCCESSとする。
      状態取得（デバイス名のタップ）では鳴らさない（wear側の状態取得もFire-and-forgetで結果を
      返さず振動しないため、BL-061）。
      mobileのAndroidManifestへ`VIBRATE`権限を追加した（wearは宣言済み）。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/haptics/HapticPattern.kt
      - core/src/main/kotlin/com/sesamiwear/core/haptics/SesameHapticPatternResolver.kt
      - core/src/test/kotlin/com/sesamiwear/core/haptics/SesameHapticPatternResolverTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/haptics/HapticPattern.kt（削除）
      - wear/src/main/kotlin/com/sesamiwear/wear/haptics/SesameHapticPatternResolver.kt（削除）
      - wear/src/test/kotlin/com/sesamiwear/wear/haptics/SesameHapticPatternResolverTest.kt（削除）
      - wear/src/main/kotlin/com/sesamiwear/wear/haptics/SesameHapticPlayer.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameResultHandler.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/messaging/SesameResultHandlerTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/haptics/SesameHapticPlayer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetHapticResolver.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandReceiver.kt
      - mobile/src/main/AndroidManifest.xml
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetHapticResolverTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。追加したユニットテストで、単一の成功・失敗、複数台の一部失敗、
      連打のみ、成功と連打の混在、対象0台の各ケースを確認した。移設した
      `SesameHapticPatternResolver`のテストも、波形が空でなく負の値を含まないことと、
      成功と失敗の波形が異なることを追加で検証している。
      実機での振動の体感確認はBL-149へ含めた。
    関連ID:
      - BL-129

- date: 2026-09-18 15:40
  summary: ホーム画面ウィジェットをリサイズ可能にし1マス相当のコンパクト表示を追加した
  details:
    変更内容: >-
      BL-128: ウィジェットがTile相当の1サイズ固定で、ホーム画面の1マスへ置きたい場合に大きすぎた
      問題に対応した。`SesameWidgetLayout`（Android非依存、ユニットテスト対象）が表示領域（dp）から
      `FULL`/`COMPACT`を決め、`SesameWidget`は`SizeMode.Responsive`で候補サイズを提示して
      `LocalSize`を受け取る。しきい値は幅200dp・高さ100dpで、幅は「左列（96dp）＋間隔（6dp）＋
      状態表示」を横に並べて成立する下限、高さは状態アイコン・状態文言・最終取得時刻・操作文言の
      4行が入る下限から決めた。
      `COMPACT`は状態アイコンと状態文言だけを出し、デバイス名・「変更」・最終取得時刻・操作文言は
      出さない。タップの挙動は`FULL`と同じで、対象デバイスの変更は長押しメニュー
      （`widgetFeatures="reconfigurable"`）から行う。未設定時の文言は1マスに収まらないため
      「タップして設定」から「設定」へ短縮する。
      `sesame_widget_info.xml`は`resizeMode`を`none`から`horizontal|vertical`へ変え、
      `minResizeWidth`/`minResizeHeight`を50dpで追加した。`minWidth`/`minHeight`はAPI 30以下で
      既定の配置サイズを決めるため250x110dpのまま据え置いた（下げると旧端末で既定が1マスへ
      縮むため）。
      複数台を横に並べる表示（4x1等）は採らない（2026-09-18、ユーザー確認済み）。wear側に無い機能に
      なり、以降の表示変更で両方を追従させる必要が出るため。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayout.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/res/xml/sesame_widget_info.xml
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetLayoutTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。追加したユニットテストで、4x2相当・1x1相当・幅はあるが低い場合・
      高さはあるが狭い場合の判定と、しきい値の境界（以上でFULL、1dp下回るとCOMPACT）を確認した。
      実機での表示崩れとリサイズ操作の確認はBL-149へ含めた。
    関連ID:
      - BL-128

- date: 2026-09-18 14:30
  summary: Sesame Web APIの月間リクエスト上限をアプリのヘルプと利用者向けドキュメントへ明示した
  details:
    変更内容: >-
      BL-144: 2026-09-18に月間リクエスト上限へ到達して状態取得・施錠/解錠がすべてHTTP 403で
      失敗した（BL-141）際、アプリ・ドキュメントのどこにも上限の存在に触れた記述が無く、
      利用者が原因へ到達できなかった問題に対応した。
      `mobile.help.HelpContent` へヘルプ項目「APIのリクエスト回数の上限」を追加し、
      「値の取得方法」の直後へ置いた。上限の存在、上限到達時の表示が「認証エラー」になること
      （BL-140で追加した文言と同じ言葉を使い、利用者が結び付けられるようにする）、確認先
      （SESAME Biz）、対処（翌月のリセット待ちか引き上げの問い合わせ）、本アプリが0.12.0から
      自動取得を行わないことを説明する。上限の具体的な回数（1000回）は利用者の環境での実測値で
      公式ドキュメント上の記載を確認できていないため断定せず、書かないことをユニットテストで固定した。
      1項目から複数の公式ページへ誘導する必要が出たため、`HelpTopic.link: HelpLink?` を
      `links: List<HelpLink>` へ変更し、`CredentialsSettingsScreen.HelpTopicDialog` を追従させた。
      併記する公式リンクは、既存のSESAME Biz 開発者ページと、APIキーの取得手順を説明した
      CANDY HOUSE公式記事（`jp.candyhouse.co/blogs/how-to/...`）の2本。いずれも実際にアクセスして
      認証なしで開けることを確認した。Web APIのリファレンス（`doc.candyhouse.co/ja/SesameAPI/`）は
      GitHub Pagesの認証（`github.com/pages/auth`）へ302でリダイレクトされ認証なしでは開けなかった
      ため、リンクとして採用していない。公式記事のURLは日本語のパスを持つため、`Uri.parse`が
      そのまま扱えるようパーセントエンコード済みで保持し、非ASCII文字を含まないことをテストで固定した。
      あわせて `docs/USER_GUIDE.md`（「APIのリクエスト回数の上限」節と「困ったときは」の2行）、
      `docs/SUPPORT.md`（問い合わせ前の自己診断項目）、`README.md`（既知の未確認事項・制約）へ
      上限到達時の症状と確認先を記載した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/help/HelpContent.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/help/HelpContentTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
      - docs/SUPPORT.md
      - README.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md" / WebFetchによるリンク到達性の確認
    検証結果: >-
      成功 - 全ゲート終了コード0。リンクの到達性は実際にアクセスして確認し、
      biz.candyhouse.co と jp.candyhouse.co の記事は認証なしで到達、
      doc.candyhouse.co はGitHub Pagesの認証へリダイレクトされるため不採用とした。
      ユニットテストで、項目の並び順、上限の説明が「1か月あたり」「上限」「認証エラー」
      「biz.candyhouse.co」を含み「1000」を含まないこと、全リンクがhttpsかつ非ASCIIを
      含まないことを検証した
    関連ID:
      - BL-144

- date: 2026-09-18 13:15
  summary: 状態取得・施錠/解錠の失敗理由を表示し「まだ取得していない」と区別できるようにした
  details:
    変更内容: >-
      BL-140: 「状態不明」が「まだ一度も取得していない」と「取得を試みたが失敗した」を区別せず、
      apikeyが拒否されていても利用者が設定を見直す契機を得られなかった問題に対応した。
      `core.SesameStatusFailure` を新設し、HTTPステータスコード（BL-139で保持させた
      `SesameApiException.httpStatusCode`）から `AUTH_OR_QUOTA`（401 / 403 / 429）と
      `COMMUNICATION`（それ以外・通信失敗）へ分類する。403は「apikey無効」と「月間上限到達」の
      両方で返り本文も同一のため区別できず（BL-141）、文言も両方を含む案内にしている。
      表示文言は `core.display.SesameStatusDetail` が決め、直近が失敗ならその理由、成功なら
      BL-142で追加した最終取得時刻を返す。失敗を優先するのは、より新しい情報であり利用者が次に
      取るべき行動へ直結するため。Tile・Complicationは5文字の `shortLabel`（「認証エラー」
      「通信エラー」）、表示領域に余裕があるホーム画面ウィジェットは `detailedLabel`
      （「認証エラー（設定を確認）」「通信エラー（電波状況を確認）」）を使う（ユーザー確認済み）。
      失敗しても表示中の施錠状態は「状態不明」へ戻さず、最後に分かった状態を残す（ユーザー確認済み。
      BL-142の「最後に分かった状態を出し続ける」設計と揃え、Tileからの施錠/解錠も引き続き行える）。
      保持と同期のため、`SesameStatusSnapshot` へ `lastFailure` を追加し `updatedAtEpochMillis` を
      null許容にした（一度も取得できないまま失敗した場合を表せるようにするため）。
      `LockStateStore.saveFailure` は状態を残したまま失敗だけを上書きし、成功時の `save` が消す。
      `SesameWearProtocol.KEY_LAST_FAILURE` を追加し、`SesameStatusSyncer.sync` が
      スナップショットを丸ごとDataItemへ載せる（値の無い項目はキーごと載せず、wear側は
      `SesameStatusSnapshotFactory` がキーの有無から復元する）。これに伴い
      `LockStateListener.onLockStateChanged(uuid, isLocked)` を
      `onStatusChanged(uuid, snapshot)` へ変更した。
      「全デバイス」対象では `SesameStatusFailure.worstOf` が集約し、利用者が対処できる
      `AUTH_OR_QUOTA` を優先する。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusFailure.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusSnapshot.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusSnapshotFactory.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameStatusDetail.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameStatusFailureTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameStatusSnapshotFactoryTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameStatusDetailTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameStatusSyncer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/LockStateStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModel.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModelResolverTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameStatusSnapshotReader.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileStateResolver.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。追加・更新したユニットテストで、401 / 403 / 429 と
      それ以外・ステータスコード無しの分類、失敗が鮮度表示より優先されること、失敗しても
      施錠状態と取得時刻が残ること、次の成功で失敗の記録が消えること、「全デバイス」で
      認証エラーが優先されること、未知の保存値を失敗なしとして扱うことを確認した。
      実機での表示崩れの確認はBL-149へ含めた。
    関連ID:
      - BL-140

- date: 2026-09-18 11:30
  summary: 自動状態取得を廃止しSesame APIの月間リクエスト上限の超過を止めて最終取得時刻を表示するようにした
  details:
    変更内容: >-
      BL-142: 自動状態取得がSesame Web APIの月間リクエスト上限（1000回）を使い切る問題に対応した。
      消費ペースは実機計測ではなく設計値から算出した（ユーザー確認済み）。Complicationのマニフェスト
      `UPDATE_PERIOD_SECONDS`は600秒のため定期更新は144回/日/枠で、鮮度閾値30秒はこの間隔より常に
      短いため更新のたびに必ず状態取得が飛ぶ。登録2台で「全デバイス」を対象にすると288回/日
      （約8,600回/月）となり、Complication枠1つだけで上限（約33回/日）を8倍以上超過していた。
      対応方針はユーザーと合意のうえ「自動取得そのものを廃止し、タップでの更新前提とする。
      代わりに前回取得日時を表示する」とした。`wear.tile.SesameTileStateResolver` から
      `requestStatusIfStale` を削除し、`resolveState` を `resolveStatus` へ変更して
      表示状態と最終取得時刻の文言を持つ `SesameTileStatus` を返すようにした。
      文言の決定はAndroid非依存の `core.display.SesameStatusFreshness` へ置き、
      1分未満「たった今」/ 1時間未満「N分前」/ 24時間未満「N時間前」/ 24時間以上は日付のみ「9/17」/
      未取得「未取得」とする（表示形式はユーザー確認済み）。「全デバイス」対象は `oldestOf` が
      最も古い取得時刻を代表値とし、1台でも未取得なら全体を未取得とする（集約状態の判定と同じ
      最悪値の考え方）。デモ用デバイスは取得という概念が無いため表示しない。
      表示位置はTileの右チップ（`TYPOGRAPHY_CAPTION3`）、ウィジェットの同位置（11sp）、
      Complicationは`LONG_TEXT`のみ末尾へ括弧付き（`SHORT_TEXT`は文字数が足りないため対象外）。
      ウィジェットは `SesameWidgetModelResolver.resolve` の `lockStateOf` を `snapshotOf`
      （`SesameStatusSnapshot` を返す）へ変え、更新時刻も同じ経路で受け取るようにした。
      detektの `LongMethod` と `TooManyFunctions` に達したため、`SesameTileService` の
      ステータスチップ組み立てを `buildStatusColumn` としてトップレベル関数へ切り出した。
      BL-142の「フルセット」で挙げた抑制策のうち、自動取得の廃止で対象が変わった
      (3)uuid単位の最小取得間隔と(4)月間カウンタは、BL-148 / BL-147 として分離起票した
      （ユーザー確認済み）。BL-143（エラー後のバックオフ）は抑制対象だった自動取得が消えたため
      対象消滅として閉じた（ユーザー確認済み。失敗の種類を利用者へ伝える側面はBL-140が引き取る）。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameStatusFreshness.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameStatusFreshnessTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileStateResolver.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/complication/SesameComplicationContentTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModel.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetRepository.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModelResolverTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
      - README.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。追加したユニットテストで、相対表記と日付表記の境界（1分・1時間・
      24時間）、端末時計のずれ（未来の時刻）、タイムゾーンによる日付の違い、「全デバイス」での
      最古値の採用と1台未取得時の扱い、デモでの非表示を検証した。
      Tile / Complication / ウィジェットの実機での見え方は未検証（BL-149として起票）。
    関連ID:
      - BL-142

- date: 2026-09-18 09:45
  summary: Sesame APIの失敗理由をリリースビルドのlogcatへ残せるようにした
  details:
    変更内容: >-
      BL-139: リリースビルドでSesame APIの失敗理由がどこにも残らず、状態取得が「状態不明」に
      なる原因（HTTPエラーなのか通信失敗なのか）を切り分けられなかった問題に対応した。
      `core.api.SesameApiException` へ `httpStatusCode` を追加し、HTTPのエラー応答では
      ステータスコードを保持する。あわせて例外メッセージから応答本文を除いた（従来は
      `Sesame API error: HTTP 403 - {本文}` の形で本文を載せており、メッセージがログへ流れると
      応答内容が露出しうるため。`rules/guardrails-unified.v1.md` 3.3）。
      失敗の説明文を組み立てる `core.api.SesameApiFailureLog` と操作種別の
      `core.api.SesameApiOperation` を新設し、`status failed: HTTP 403` のように
      「どの操作が」「どの種類の失敗で」落ちたかだけを1行で表す。apikey・secretKey・uuid・URL・
      応答本文は一切含めない。
      `mobile.command.SesameDeviceCommandExecutor` はAndroid非依存のユニットテスト対象で
      `android.util.Log` を直接呼べないため、出力先を注入可能にした。引数が
      detektの `LongParameterList` 閾値（7）に達したため、API呼び出し口と失敗ログの出力先を
      `SesameApiAccess` へまとめ、既定のAPIクライアント生成（`-PsesameApiBaseUrl` の差し替え、
      BL-132）も同クラスへ移した。`SesameDeviceCommandExecutorFactory` が
      `Log.w(SesameApiFailureLog.TAG, ...)` へ配線する（`Log.w` は
      `mobile/proguard-rules.pro` の `-assumenosideeffects` の対象外でリリースビルドにも残る、BL-083）。
      施錠/解錠側は `mobile.messaging.SesameCommandHandler` に `onFailure` を足し、
      実行口が同じ経路でログを出す。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiException.kt
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiClient.kt
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiFailureLog.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameApiClientTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameApiFailureLogTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameCommandHandler.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunnerTest.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest test / ./gradlew assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >-
      成功 - 全ゲート終了コード0。追加したユニットテストで、ログ文字列が
      HTTPステータスコードまたは原因例外の型名だけになり、uuid・apikey・secretKey・
      応答本文・URLを含まないことを確認した
    関連ID:
      - BL-139

- date: 2026-09-17 00:10
  summary: ウィジェットのタップ処理を受信の実行時間制限内へ収め実機で固着とANRの解消を確認した
  details:
    変更内容: >-
      BL-137（BL-136 の実機検証で判明）: 実機の logcat で、ウィジェットのデバイス名タップが
      `ANR in com.sesamiwear.mobile.debug / Reason: Broadcast of Intent (act=...REFRESH_STATUS,
      flg=0x10000010)` となり `Killing ...: bg anr` でプロセスが強制終了されることを観測した。
      `flg` の `0x10000000` は `FLAG_RECEIVER_FOREGROUND` で、Glance の `actionSendBroadcast` が
      付けるため実行時間の制限は約10秒（バックグラウンド受信の60秒ではない）。BL-133 で設定した
      接続10秒・全体20秒では間に合わないため、`SesameApiClient` のタイムアウトを接続3秒・
      読み書き3秒・全体6秒へ短縮し、`WidgetCommandReceiver` の処理全体を `withTimeoutOrNull`
      （`WORK_TIMEOUT_MILLIS` = 8秒）で囲んで制限より手前から自分で打ち切るようにした。
      打ち切りは取り消しとして伝わり、`WidgetCommandRunner` の `NonCancellable` な再描画が状態を戻す。
      BL-136: 修正前後の挙動を実機で比較し、DESIGN.md へ結果を記録した。あわせて
      `.claude/skills/realmachine-verification/SKILL.md` へ、接続先を到達不能な宛先へ固定して
      通信失敗だけを再現する手順と、実資格情報が入った端末での安全確保の手順を追記した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiClient.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandReceiver.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunnerTest.kt
      - .claude/skills/realmachine-verification/SKILL.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    関連ID:
      - BL-137
      - BL-136
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証 /
      ANDROID_SERIAL=<Pixel 8 Pro> ./gradlew :mobile:installDebug
      -PsesameApiBaseUrl=http://192.168.137.1:8080/api/sesame2（および http://127.0.0.1:1/api/sesame2）/
      adb の input tap・exec-out screencap・logcat による実機確認
    検証結果: >-
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。実機では、修正前ビルドが
      タップから10秒でANR・強制終了となり「通信中...」が固着したのに対し、修正後は同じ操作で
      ANRもFATALも発生せずプロセスが生存し、約3.9秒で「状態不明」へ戻った（接続が即座に拒否される
      宛先では約0.4秒）。`am force-stop` で途中終了させたウィジェットも、アプリを開くだけで
      再描画された。logcatのSesame系タグの行にuuid形状・32桁16進数の文字列は0件だった。
      検証は接続先を到達不能な宛先へ固定したデバッグ版のみで行い、実Sesame APIへは送信していない。

- date: 2026-09-17 00:12
  summary: ウィジェットの通信中表示が解除されずに固まらないようにした
  details:
    変更内容: >-
      BL-135: `widget.WidgetInProgressTracker` の登録を開始時刻つきにし、
      `IN_PROGRESS_TIMEOUT_MILLIS`（30秒）を過ぎた登録は `finish` が呼ばれていなくても実行中と
      みなさないようにした。従来は参照カウントだけで、実行が途中で打ち切られて解除の再描画が
      行われないと、ウィジェットは定期更新を持たない（`updatePeriodMillis=0`）ため、右側のタップも
      効かない「通信中...」がホーム画面に残り続けた。上限はSesame APIの呼び出し全体のタイムアウト
      （20秒、BL-133）より長くとり、正常に終わる操作を誤って打ち切らない値にしている。
      あわせて `MainActivity.onStart` でウィジェットの再描画を要求するようにし、表示が固まった場合でも
      利用者が最初にとる行動（アプリを開く）で復帰できるようにした。DESIGN.md の
      「Sesame APIクライアント」「mobileホーム画面ウィジェット」「mobile側コマンド処理」の各節を
      BL-133〜BL-135 の実装に合わせて更新し、RELEASE_NOTES.md の 0.11.0 へ利用者向けの修正内容を追記した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetInProgressTracker.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetInProgressTrackerTest.kt
      - docs/RELEASE_NOTES.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    関連ID:
      - BL-135
      - BL-133
      - BL-134
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。追加した単体テストで、
      上限の直前までは通信中のままであること、上限に達すると単一デバイスでも全デバイスでも
      通信中と判定されないこと、古い登録の期限切れが同一デバイスの新しい登録を消さないことを確認した。
      既存の参照カウントの挙動（重ねて実行したときは両方の解除まで通信中）は変えていない。

- date: 2026-09-16 23:52
  summary: ウィジェットとData Layerの受信口で例外がプロセスを落とさないようにした
  details:
    変更内容: >-
      BL-134: システムからの入口で起動したコルーチンから例外が漏れるのを止める
      `com.sesamiwear.mobile.EntryPointGuard`（Android非依存）を追加し、
      `widget.WidgetCommandReceiver`（ウィジェットのタップ）、
      `messaging.SesameMessageListenerService`（ウォッチからのメッセージ）、
      `widget.WidgetConfigurationActivity`（割り当て後の再描画）の3か所へ適用した。
      これらは結果を受け取る呼び出し元がいないため、漏れた例外がそのままプロセスを終了させ、
      ホーム画面のウィジェットが最後に描いた「通信中...」のまま取り残される原因になっていた。
      通知するのは例外の型名だけで、接続先URL（uuidを含む）が入りうるメッセージは渡さない。
      `messaging.DataLayerBestEffort` も `ApiException` 以外の失敗を握りつぶすよう広げ
      （`UNKNOWN_STATUS_CODE`）、資格情報設定画面の `syncDeviceList` は、ウォッチ同期の失敗で
      ウィジェット再描画が飛ばないよう2つの独立したコルーチンへ分けた。
      いずれもコルーチンのキャンセルは従来どおり再送出する。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/EntryPointGuard.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/DataLayerBestEffort.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandReceiver.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetConfigurationActivity.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/EntryPointGuardTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/messaging/DataLayerBestEffortTest.kt
      - docs/records/managed/BACKLOG.md
    関連ID:
      - BL-134
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。追加した単体テストで、
      任意の例外を握りつぶして呼び出し元が継続すること、通知されるのが型名だけであること、
      キャンセルは再送出されることを確認した。detektの InstanceOfCheckForException は、
      キャンセルのみを再送出する判定のため関数単位で抑止し、理由をKDocへ残した。

- date: 2026-09-16 23:35
  summary: Sesame API呼び出しの失敗をSesameApiExceptionへ正規化しタイムアウトを設定した
  details:
    変更内容: >-
      BL-133: `core.api.SesameApiClient` が、通信の失敗（`IOException`。圏外・タイムアウト・
      名前解決失敗）、想定外の応答本文（`SerializationException`）、接続先URLが不正な場合
      （`IllegalArgumentException`）を `SesameApiException` へ正規化して送出するようにした
      （`asApiCall`）。従来これらは素通りし、呼び出し側（`SesameCommandHandler.execute` /
      `SesameDeviceCommandExecutor.fetchIsLocked`）が `SesameApiException` しか捕捉していない
      ため、ウィジェットのタップ（`WidgetCommandReceiver`）やウォッチからのメッセージ
      （`SesameMessageListenerService`）が起動したコルーチンから漏れ、プロセスが落ちる経路に
      なっていた。`SesameApiException` へ `cause` を追加し、例外メッセージには原因例外の型名だけを
      載せる（uuidを含むURLや応答内容を混ぜない）。あわせて既定の `OkHttpClient` へ接続10秒・
      読み書き10秒・呼び出し全体20秒のタイムアウトを設定し、プロセス内で共有する1インスタンスに
      した（BroadcastReceiverの実行時間制約内で必ず終わるようにするため）。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiClient.kt
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiException.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameApiClientTest.kt
      - docs/records/managed/BACKLOG.md
    関連ID:
      - BL-133
    検証コマンド: >-
      ./gradlew ktlintCheck detekt / ./gradlew lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。追加した3件の単体テストで、
      接続断・解析不能な応答本文・コマンド送信時の接続断のいずれでも `SesameApiException` だけが
      送出され、原因例外が保持されることを確認した。kotlinx.coroutinesが `withContext` をまたぐ例外を
      複製するため、テストは `cause` を1段見るのではなく最も内側の原因例外を検査している。

- date: 2026-09-16 12:12
  summary: 検証用にSesame APIの接続先を差し替えられるようにし、BL-126の残項目を実機で検証した
  details:
    変更内容: >-
      BL-132（新規）: mobile の debug ビルドへ `-PsesameApiBaseUrl` で Sesame API の接続先を
      差し替える注入口を追加した（`buildConfigField` の `SESAME_API_BASE_URL` を
      `SesameDeviceCommandExecutor.defaultApiClient` が `BuildConfig.DEBUG` かつ非空のときだけ使う）。
      未指定・リリースビルドでは空文字となり本番URLのままになる。あわせて debug 専用の
      AndroidManifest で平文HTTPを許可し、`scripts/mock-sesame-api.py`（標準ライブラリのみ、
      状態はメモリ保持、署名は検証しない）を追加した。実 Sesame デバイスと実資格情報が無いと
      検証できなかった「施錠/解錠の成功を起点とする状態同期」を、モックで再現できるようにする
      のが目的（rules/guardrails-unified.v1.md 12.5 が許容するモック限定の疎通確認）。
      BL-126: このモック構成で (3) 全デバイスの MIXED 表示と一括操作、(7) 端末再起動後の保持、
      (8) ウィジェットと Tile の相互追随を検証した（結果は DESIGN.md「実機検証（BL-126）」）。
    変更ファイル:
      - mobile/build.gradle.kts
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/debug/AndroidManifest.xml
      - scripts/mock-sesame-api.py
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証 /
      python scripts/mock-sesame-api.py と
      ./gradlew :mobile:installDebug -PsesameApiBaseUrl=http://192.168.137.1:8080/api/sesame2 /
      adb の input・screencap・logcat による実機確認
    検証結果: >-
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。実機では
      ウィジェットからの解錠でウォッチの Tile が解錠中へ追随し（wear のログで state=UNLOCKED）、
      ウォッチの Tile からの施錠でウィジェットが施錠中へ追随した。全デバイス選択時は
      一方だけ解錠した状態で「一部解錠／タップで全施錠」を表示し、タップで両uuidへ施錠が飛んで
      「全施錠中」になった。再起動後もウィジェットの割り当てと表示が保たれた。
      検証後はダミー資格情報を削除し（登録0台）、モックサーバーを停止した。
      Complication の表示・追随と、コンパニオン未導入スマホでの動作は未確認のまま BACKLOG へ残した。
    関連ID:
      - BL-132
      - BL-126

- date: 2026-09-16 11:36
  summary: デバッグ版をPlay版と併存インストールできるようにし、BL-126の人手検証をadb経由で実施した
  details:
    変更内容: >-
      BL-131（新規）: mobile / wear の debug ビルドへ applicationIdSuffix ".debug" と
      versionNameSuffix "-debug" を付け、Play ストア版（リリース署名）が入った実機へ
      デバッグ版を併存インストールできるようにした。署名が異なる Play 版へは上書き更新できず、
      入れ替えるには保存済みの資格情報を消すアンインストールが必要になるため。
      mobile / wear の双方へ同じサフィックスを付けることで、Data Layer API が要求する
      「同一 applicationId・同一署名」の条件をデバッグ版同士で満たす（実機で往復を確認済み）。
      あわせて debug ソースセットへ app_name / widget_label の上書き（"Sesami Wear (debug)"）を追加し、
      ランチャー・ウィジェット選択画面・ウォッチのアプリ一覧で Play 版と見分けられるようにした。
      BL-126: 上記の併存ビルドを Pixel 8 Pro / Pixel Watch 2 へ入れ、adb 経由のUI操作で人手検証を実施した
      （結果は DESIGN.md「実機検証（BL-126）」へ記載）。
    変更ファイル:
      - mobile/build.gradle.kts
      - wear/build.gradle.kts
      - mobile/src/debug/res/values/strings.xml
      - wear/src/debug/res/values/strings.xml
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証 /
      ./gradlew :mobile:installDebug および :wear:installDebug（ANDROID_SERIAL で端末を指定） /
      adb shell dumpsys package・appwidget・logcat と exec-out screencap による実機確認
    検証結果: >-
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。
      実機では com.sesamiwear.mobile（Play版 0.10.0）と com.sesamiwear.mobile.debug が
      両端末で共存し、デバッグ版の lastUpdateTime が今回のインストール時刻であることを確認した。
      BL-126 の (3) MIXED 表示・(8) 相互追随・(10) コンパニオン未導入スマホは未確認のまま
      BACKLOG へ残した。
    関連ID:
      - BL-131
      - BL-126

- date: 2026-09-16 04:30
  summary: ホーム画面ウィジェットに合わせてアプリ内ヘルプと利用者向けドキュメントを更新した
  details:
    変更内容: >-
      アプリ内ヘルプ（mobile.help.HelpContent）へ「ホーム画面ウィジェットの使い方」を4項目めとして追加し、
      「Sesameが無くてもデモで試す」をウォッチのタイルとウィジェットの両方の試し方、両者のデモが連動しないこと、
      登録後にデモのウィジェットが「タップして設定」へ戻ることを含む内容へ直した。ウィジェットの説明が
      実際の表示文言（変更・全デバイス・通信中...・タップして設定）を含むことをユニットテストで固定した。
      docs/USER_GUIDE.md に「ホーム画面ウィジェットで操作する」節とトラブルシュートを、docs/CLOSED_TEST.md に
      ウォッチ無しでも参加・試用できることを、README.md の主な機能と docs/RELEASE_NOTES.md の 0.11.0
      （未リリース、ストア掲載用の要約156文字を含む）を、docs/store/STORE_LISTING.md の短い説明（57文字）・
      詳細な説明（1465文字）・対象デバイスを更新した。テスターの手元は 0.10.0 のままのため、ユーザー確認のうえ
      公開ドキュメントには「0.11.0以降」と明記し、STORE_LISTING には Play Console への転記を BL-127 で行う旨を
      注記した。プライバシーポリシーとデータセーフティ申告は、ウィジェットが端末外へ新たな情報を送らず、
      端末内に保存するのはロック状態と割り当てのみのため変更不要と判断した（根拠は DESIGN.md に記載）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/help/HelpContent.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/help/HelpContentTest.kt
      - docs/USER_GUIDE.md
      - docs/CLOSED_TEST.md
      - docs/RELEASE_NOTES.md
      - docs/store/STORE_LISTING.md
      - README.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintFormat / ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証 / ストア掲載文言の文字数計測（Python）
    検証結果: >-
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。短い説明・詳細な説明・
      このリリースの新機能はいずれも Google Play の上限内。
    関連ID:
      - BL-124

- date: 2026-09-16 03:50
  summary: ホーム画面ウィジェットでもデモモードを操作できるようにした
  details:
    変更内容: >-
      登録済みデバイスが0台のとき、ウィジェットでデモ用デバイスを選んで施錠・解錠を体験できるようにした。
      SesameDeviceCommandExecutor はデモuuidのとき Sesame API を呼ばず常に成功として LockStateStore の
      デモ状態だけを書き換え（重複判定は実デバイスと同じ）、状態取得は保存値（無ければ初期状態の施錠中）を
      返すだけにした。通知先を LockStateNotifier（local＝ウィジェット再描画、watch＝DataItem 同期）へ分け、
      デモでは watch を呼ばないことで Data Layer へも送らない（ウォッチのデモ状態とは同期しない）。
      確認画面の有無・状態文言は実デバイスと同じ経路のまま。資格情報を1台でも保存すると、
      WidgetDeviceAssignmentStore.onRegisteredDevicesChanged でデモを割り当てていたウィジェットの割り当てを
      解除し「タップして設定」へ戻す。detekt の LongParameterList・ReturnCount に合わせて通知先の
      まとめ方と状態取得の分割を調整した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetDeviceAssignmentStore.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunnerTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetDeviceAssignmentStoreTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintFormat / ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。デモuuidでの操作で API リクエスト0件・DataItem 同期の呼び出し0件、
      1台登録後のデモ割り当て解除をユニットテストで確認した。途中のメモリ不足による Gradle ワーカーの
      異常終了はデーモンを停止して再実行した（コード起因ではない）。
    関連ID:
      - BL-123

- date: 2026-09-16 03:15
  summary: ホーム画面ウィジェットのタップで施錠・解錠・状態取得できるようにした
  details:
    変更内容: >-
      wear の Tile と同じ操作ルールでウィジェットを操作できるようにした。右側のタップは Android 非依存の
      WidgetTapAction（SesameTileActions と SesameCommandConfirmation に従い、施錠中→解錠確認、解錠中→即施錠、
      一部解錠→即全施錠、通信中・状態不明→なし）で決め、施錠は WidgetCommandReceiver（exported=false、goAsync）で
      即時実行、解錠は WidgetUnlockConfirmActivity（ダイアログテーマ、左キャンセル・右解錠）を挟む。
      デバイス名のタップは状態取得のみ。WidgetCommandRunner が対象uuidを SesameDeviceTargets で展開して
      実行口を並行に呼び、WidgetInProgressTracker（プロセス内メモリ）で通信中を表示してから、終了後に
      保存済み状態で再描画する（失敗時は操作前の状態に戻る）。解錠確認画面からも同じ経路で実行するため、
      Glance の ActionCallback ではなく同じ goAsync の仕組みを自前の Receiver で使った（WorkManager へは委譲せず、
      理由は DESIGN.md に記載）。SesameDeviceCommandExecutorFactory の通知先へウィジェットの再描画を加え、
      ウォッチ経由の成功でもウィジェットが、ウィジェット経由の成功でもウォッチの Tile が追随する。
      タップ時の動作・キャンセル時に送信しないこと・通信中の表示と解除・全デバイスへの送信・失敗時の状態維持・
      ウォッチ経由との重複の抑止をユニットテストで検証した（鍵は RFC 4493 の公開テストベクタ）。
    変更ファイル:
      - mobile/src/main/AndroidManifest.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetRepository.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandReceiver.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunner.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetInProgressTracker.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetTapAction.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetUnlockConfirmActivity.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetCommandRunnerTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetInProgressTrackerTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetTapActionTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintFormat / ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      ./gradlew :mobile:assembleRelease / npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートと mobile のリリースビルドが終了コード0。実機での施錠・解錠と、BroadcastReceiver の
      実行時間の制約に抵触しないかの確認は BL-126（人手検証）で行う。
    関連ID:
      - BL-122

- date: 2026-09-16 02:30
  summary: mobileにホーム画面ウィジェット（表示と対象デバイスの設定）を追加した
  details:
    変更内容: >-
      Jetpack Glance 1.2.0 で mobile.widget.SesameWidget / SesameWidgetReceiver を追加した。wear の Tile と
      同じく左列にデバイス名と「変更」、右側に状態アイコン・状態文言・操作文言を置き、状態色は右側のみに使う。
      表示内容は Android 非依存の SesameWidgetModelResolver が割り当て・登録済みデバイス・LockStateStore の
      保存値から決める（未設定・削除済み・デモの失効は「タップして設定」、単一は未取得なら状態不明、
      全デバイスは wear と同じ集約規則、スマホ未接続は存在しない）。appWidgetId ごとの割り当ては
      WidgetDeviceAssignmentStore（非暗号化 SharedPreferences）へ保存し、onDeleted で消す。
      追加時は android:configure の WidgetConfigurationActivity で SesameDeviceTargets.choices から選び、
      「変更」「タップして設定」からも開ける。Glance のセッション中は provideGlance が再実行されないため、
      SesameWidgetUpdater が状態へ更新トークンを書き込んで再描画させる方式にし、資格情報の保存・削除時
      （CredentialsSettingsScreen）と割り当て直後に呼ぶ。Glance が推移的に持ち込む work-runtime 2.7.1
      （room 2.2.5 等）は古いため、ユーザー確認のうえ明示依存で引き上げた。最新の 2.11.2 は kotlin-stdlib を
      2.1.20 へ上げるため、stdlib を変えない 2.10.5 を採った（room 2.6.1 / sqlite 2.4.0。既存依存の版の変化は
      compose-runtime 1.7.6→1.7.8 のみ）。施錠・解錠のタップ操作は BL-122 で実装する。
    変更ファイル:
      - gradle/libs.versions.toml
      - mobile/build.gradle.kts
      - mobile/src/main/AndroidManifest.xml
      - mobile/src/main/res/values/strings.xml
      - mobile/src/main/res/xml/sesame_widget_info.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidget.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModel.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetReceiver.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetRepository.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/SesameWidgetUpdater.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetConfigurationActivity.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/widget/WidgetDeviceAssignmentStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/SharedPreferencesKeyValueStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/SesameWidgetModelResolverTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/widget/WidgetDeviceAssignmentStoreTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew :mobile:dependencies（releaseRuntimeClasspath の導入前後の差分）/ ./gradlew ktlintFormat /
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug / ./gradlew :mobile:assembleRelease /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートと mobile のリリースビルド（minify有効）が終了コード0。lint の新規警告は既存コードと
      同種の UseKtx のみ。実機でのウィジェット追加・表示の確認は BL-126（人手検証）で行う。
    関連ID:
      - BL-121

- date: 2026-09-16 01:40
  summary: mobileの施錠・解錠・状態取得をData Layerから切り離した実行口として切り出した
  details:
    変更内容: >-
      Sesame API の呼び出しが SesameMessageListenerService の private メソッドに閉じていたため、
      ホーム画面ウィジェット（BL-122）からも呼べるよう Android 非依存の
      mobile.command.SesameDeviceCommandExecutor を追加した。execute は重複判定 → 資格情報の検索 →
      SesameCommandHandler → 成功時の状態保存と通知の順（移設前と同じ）で SUCCESS / FAILURE / DEBOUNCED を返し、
      refreshStatus は GET 結果を保存・通知する。CommandDebouncer は companion object の sharedDebouncer を
      共有し、ウォッチ経由とウィジェット経由の重複をまとめる。ロック状態を mobile 端末内に保存する
      mobile.state.LockStateStore（非暗号化 SharedPreferences、JsonObject を直接組み立てて単一キーへ保存）と、
      Android 側の配線 SesameDeviceCommandExecutorFactory・SharedPreferencesKeyValueStore を追加した。
      SesameCommandHandler にはコマンドを直接受ける execute を追加した（handle(path) は委譲）。
      SesameMessageListenerService はパスをコマンドへ変換して実行口を呼び、結果を返すだけにした。
      wear から見た挙動（結果返送・DataItem 同期・デバウンス）は変えていないが、未知のパスは実行口を呼ばない
      ため重複判定の対象から外れた（マニフェストの pathPrefix により実運用では届かない）。
      実行口（成功・APIエラー・資格情報なし・不正な鍵・経路をまたぐデバウンス・状態保存・通知・状態取得）と
      LockStateStore（保存・上書き・削除・壊れた値）のユニットテストを追加した。テストの鍵は RFC 4493 の
      公開テストベクタで、実資格情報は使っていない。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorFactory.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/LockStateStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/state/SharedPreferencesKeyValueStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameCommandHandler.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/command/SesameDeviceCommandExecutorTest.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/state/LockStateStoreTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintFormat / ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
      （--max-workers=1〜2）/ npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートが終了コード0。途中で detekt の ReturnCount と ktlint の行長違反を修正した。
      メモリ不足による Gradle ワーカーの異常終了はワーカー数を絞って再実行した（コード起因ではない）。
    関連ID:
      - BL-120

- date: 2026-09-16 01:05
  summary: Tileの文言・色・操作判定と対象デバイスの解決規則をwearからcoreへ移した
  details:
    変更内容: >-
      mobile のホーム画面ウィジェット（BL-121以降）と wear の Tile で表示・操作ルールを食い違わせない
      よう、wear にあった Android 非依存の判定ロジックを core.display パッケージへ移した。
      wear.tile.SesameTileActions と wear.tile.SesameTileContent は git mv でそのまま移し（パッケージ宣言と
      コメントのみ変更）、ユニットテストも core へ移した（期待値は変更していない）。
      wear.action.SesameActionTargetResolver の全デバイス展開、DeviceSelectionScreen の選択肢の組み立て
      （0台ならデモのみ、2台以上なら先頭に全デバイス、表示名が空欄ならuuid）、SesameTileStateResolver の
      表示名解決を core.display.SesameDeviceTargets（choices / displayName / targetUuids / isAllDevices）へ
      集め、wear 側は登録済み一覧を読んで渡すだけにした。固定文言の対象（デモ・全デバイス）では従来どおり
      DataItem を読まない。CLAUDE.md の処理フロー表・全デバイス説明・単一テスト実行例・テスト対象例の
      クラス名をユーザー承認のうえ新しい所在へ更新した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameTileActions.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameTileContent.kt
      - core/src/main/kotlin/com/sesamiwear/core/display/SesameDeviceTargets.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameTileActionsTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameTileContentTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/display/SesameDeviceTargetsTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionTargetResolver.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileStateResolver.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/ui/DeviceSelectionScreen.kt
      - CLAUDE.md
      - CHANGELOG.md
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew ktlintFormat / ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
      （--max-workers=2）/ npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証 /
      git diff -M --stat で移設したテストの差分がパッケージ宣言1行のみであることを確認
    検証結果: 成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）
    関連ID:
      - BL-119

- date: 2026-09-16 00:40
  summary: 推移的依存で古い版になっていたandroidx.fragmentを1.8.9へ引き上げた
  details:
    変更内容: >-
      Google Play Console で androidx.fragment:fragment 1.1.0 の更新（1.2.1 以降）を求められていた。
      gradle/libs.versions.toml へ androidx.fragment:fragment 1.8.9 を追加し、mobile と wear の
      build.gradle.kts で implementation に明示した。releaseRuntimeClasspath の dependencyInsight で
      両モジュールとも 1.8.9 に解決されることを確認した。最新安定版 1.9.0 でもビルドとリリースビルドは
      成功したが、推移的に kotlin-stdlib 2.0.21→2.1.20、androidx.tracing 1.2.0→2.0.0、annotation
      1.8.1→1.10.0、collection 1.4.4→1.6.0、profileinstaller 1.3.1→1.4.0 も引き上げることを
      dependencies の差分で確認したため、ユーザーに確認のうえ fragment 以外の解決結果が変わらない
      1.8.9 を採った。他に Play Console から更新を求められている依存は報告されていない
      （wear の appcompat 1.1.0 は古いが警告対象外のため変更しない）。
    変更ファイル:
      - gradle/libs.versions.toml
      - mobile/build.gradle.kts
      - wear/build.gradle.kts
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >-
      ./gradlew :mobile:dependencyInsight / :wear:dependencyInsight（releaseRuntimeClasspath、
      androidx.fragment:fragment）/ ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test
      assembleDebug / ./gradlew :mobile:assembleRelease :wear:assembleRelease /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証
    検証結果: >-
      成功 - 全品質ゲートとリリースビルド（minify有効）が終了コード0。初回実行時にマシンのメモリ不足で
      Gradleワーカー・デーモンが異常終了したため、--max-workers=2 で分割して再実行した（コード起因ではない）。
      Play Console の警告解消は BL-127 で確認する。
    関連ID:
      - BL-130

- date: 2026-09-16 00:20
  summary: mobileのWearable Data Layer呼び出しを失敗しても処理を続けるベストエフォート呼び出しにした
  details:
    変更内容: >-
      Wear OS のコンパニオンアプリが入っていない端末では Wearable API のタスクが ApiException で
      失敗しうるが、SesameDeviceListSyncer.sync と SesameStatusSyncer.syncLocked は例外処理なしで
      await しており、資格情報の保存・削除時に起動したコルーチンから例外が漏れてアプリが落ちる経路が
      あった。Android 非依存の mobile.messaging.DataLayerBestEffort を追加し、ApiException だけを
      捕捉してステータスコードを Log.w へ渡し（資格情報・uuid は出さない）、呼び出し元の処理を
      継続するようにした。コルーチンのキャンセルは捕捉しない。2つの Syncer と、
      SesameMessageListenerService の結果返送（MessageClient.sendMessage）をこの呼び出しで包んだ。
      成功・ApiException の握りつぶし・後続処理の継続・キャンセルの非捕捉をユニットテストで検証した。
      端末上での再現確認は BL-126（人手検証）で行う。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/DataLayerBestEffort.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameDeviceListSyncer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameStatusSyncer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/messaging/DataLayerBestEffortTest.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
      - docs/RELEASE_NOTES.md
    検証コマンド: >-
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 記録ファイルのYAML検証（yaml.safe_load）
    検証結果: 成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）
    関連ID:
      - BL-118

- date: 2026-09-13 02:10
  summary: BL-112〜BL-114を実機検証し、デモ用デバイスの表示名を短縮した
  details:
    変更内容: >
      Pixel 8 Pro / Pixel Watch 2 の実機で BL-112 / BL-113 / BL-114 を検証した。検証は既存の
      Play版アプリと登録済み資格情報へ影響を与えないよう、`applicationId`を
      `com.sesamiwear.mobile.demotest`へ変えた検証専用のデバッグビルドで実施し、終了後に両端末から
      アンインストールした（ビルド設定の一時変更はコミットしていない）。
      BL-112は、日本語IMEの変換候補から全角の「１２３－ＤＥＦ」を確定しても入力欄が半角の
      「123-DEF」になること、`!@#`が除去されること、secretKey欄が16進数以外を落として32文字で
      打ち切り「追加」ボタンが有効になることを確認した。BL-113は、ヘルプがメニューとして開き、
      3項目の本文・「戻る」・「閉じる」・SESAME Biz 開発者ページへの遷移が動作することを確認した。
      BL-114は、デバイス選択画面の見出しと説明文が円形画面へ収まることを確認した一方、
      Tileのデバイス名チップでは表示名「デモ（体験用）」7文字がチップの背景をはみ出しており、
      ユーザー報告の見切れはこちらであることが判明した。BL-102・BL-104と同じく文言側を短縮する
      方針で「デモ」へ変更し、チップ幅に収まることと、解錠操作後にTileが`解錠中`／`タップで施錠`
      へ遷移すること（`SesameTileService`のログで`state=UNLOCKED`）を再確認した。
      表示名の上限は`SesameDemoMode.MAX_DISPLAY_NAME_CHARS`としてユニットテストで固定した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameDemoMode.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameDemoModeTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/help/HelpContent.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/help/HelpContentTest.kt
      - docs/records/managed/DESIGN.md
      - docs/USER_GUIDE.md
      - docs/CLOSED_TEST.md
      - docs/SUPPORT.md
      - docs/RELEASE_NOTES.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md" / 実機（adb経由のUI操作とスクリーンショット）
    検証結果: >
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。実機検証も上記3件すべて
      確認済み。検証にはダミー値のみを使用し、実資格情報は入力していない。
    関連ID:
      - BL-115
      - BL-112
      - BL-113
      - BL-114

- date: 2026-09-13 01:38
  summary: mobileのヘルプをメニュー形式へ拡張しデモモードへの導線を追加した
  details:
    変更内容: >
      従来のヘルプは値の取得方法だけを説明する単一のAlertDialogで、資格情報が未登録でもwear側で
      デモ（BL-109）を操作できることに気づく導線がmobile側に無かった。文言をAndroid非依存の
      `mobile.help.HelpContent`（HelpTopic / HelpLink）へ切り出し、項目一覧（HelpMenuDialog）と
      本文（HelpTopicDialog、「戻る」で一覧へ復帰）の2段構成へ変更した。項目は「値の取得方法」
      （SESAME Biz 開発者ページへのリンクを維持し、入力欄が半角のみを受け付ける旨を追記）・
      「Sesameが無くてもデモで試す」（タイル追加からデモ選択・施錠解錠・コンプリケーション表示までの
      手順）・「登録後のウォッチでの使い方」の3件。項目数・順序・重複・リンクの有無・デモ項目の
      必須語をユニットテストで検証する。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/help/HelpContent.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/help/HelpContentTest.kt
      - docs/records/managed/DESIGN.md
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。実機でのダイアログ表示と
      リンク遷移の確認は人手検証（BL-115）として残す。
    関連ID:
      - BL-113

- date: 2026-09-13 01:33
  summary: 資格情報入力欄へ全角文字が入らないよう入力値を正規化した
  details:
    変更内容: >
      uuid / apikey / secretKeyの3欄は有効な値がASCII文字のみで構成されるのに対し、日本語IMEでは
      全角英数字が入力されやすく、見た目で半角と区別できないまま保存されると署名検証がAPI側で
      失敗する。`CredentialsInputSanitizer`（Android非依存）を追加し、全角ASCII（U+FF01〜U+FF5E）の
      半角化とダッシュ類の半角ハイフンへの統一を行ったうえで、uuidは英数字とハイフン、apikeyは
      空白を除くASCII印字可能文字、secretKeyは16進数32文字までへ絞り込む。3欄は`singleLine`と
      ASCIIキーボード（secretKeyはPassword）を既定にし、`onValueChange`で毎回正規化してから状態へ
      反映する。日本語を入力する表示名欄は対象外とした。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputSanitizer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputSanitizerTest.kt
      - docs/records/managed/DESIGN.md
      - docs/USER_GUIDE.md
      - docs/RELEASE_NOTES.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。実機のIMEでの入力確認は
      人手検証としてBACKLOGへ残す。
    関連ID:
      - BL-112

- date: 2026-09-13 01:29
  summary: デモモードの説明文が円形画面の端で見切れる問題を修正した
  details:
    変更内容: >
      wear側のデバイス選択画面は、デモモード（BL-109）時に32文字の1文をそのままTextへ渡し、
      折り返し位置を画面幅に委ねていたため、円形画面の左右の縁で行頭・行末の文字が見切れていた。
      文言をAndroid非依存の`wear.ui.DeviceSelectionContent`へ切り出し、見出し「デモモード」と
      11文字以内の2行（「スマホで登録すると」「実際の鍵を操作できます」）へあらかじめ分割した。
      表示側は左右12dpのパディングと中央揃えのみを与える。上限11文字は、最小構成の円形端末
      （幅192dp）からScalingLazyColumnの既定水平パディング（10dp）と本画面の水平パディング
      （12dp）を引いた148dpへ、caption2（12sp）の全角文字が12.3文字並ぶ計算に基づく。
      1行あたりの文字数上限はユニットテストで検証し、以後の文言追加でも同じ事故が起きないようにした。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/ui/DeviceSelectionContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/ui/DeviceSelectionScreen.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/ui/DeviceSelectionContentTest.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 "**/*.md"
    検証結果: >
      成功 - 全品質ゲートが終了コード0（markdownlintはSummary 0 issues）。実機での見え方の確認は
      人手検証としてBACKLOGへ残す。
    関連ID:
      - BL-114

- date: 2026-09-10 08:05
  summary: 資格情報が未登録でもTile・Complicationを操作できるデモモードを追加した
  details:
    変更内容: >
      Google Playのクローズドテスト（BL-106）では、Sesame 5実機・Hub 3・APIキーを持たない
      テスターが資格情報の登録画面から先へ進めず、製品版アクセス申請フォームのパート1で
      問われる「テスターのエンゲージメント」に実態を伴う回答ができない。対策として、
      mobile側に1台もデバイスが登録されていない場合に限り、ダミーの施錠状態を操作できる
      デモモードを提示するようにした。実装するかはユーザーの判断待ち（状態: 要確認）で
      あったため、2026-09-10に実装する旨の承認を得てから着手している。
      判定ロジックはAndroid非依存のcore.SesameDemoModeへ切り出し、単体テスト8件で検証した。
      デモ用デバイスのuuidは実デバイスのUUID形式ともALL_DEVICES_TARGET_UUIDとも衝突しない
      固定文字列「__demo_device__」とし、登録済みデバイスが1台でもある場合は選択肢に混ぜない
      （実際には施錠されていないのに施錠済みと誤認する事故を避けるため）。
      wear側は、デバイス選択画面（DeviceSelectionScreen）が0台時にデモ用デバイスのみを提示し、
      Tile/Complicationの状態解決（SesameTileStateResolver）がデモ用uuidならwear単体で保持する
      DemoLockStateStore（非暗号化SharedPreferences、機密情報を含まない）から状態を返す。
      施錠/解錠（SesameActionActivity）はデモ用uuidの場合にMessageClientへ一切送信せず、
      ローカル状態の書き換え・成功ハプティクス・再描画要求のみで完結する。状態更新
      （SesameStatusRefreshActivity）も同様にスマホへリクエストを送らない。
      デモ状態はスマホ接続状態に依存させず、ウォッチ単体で操作を体験できるようにしている。
      Tile/Complicationの再描画要求はSesameStatusListenerServiceと重複するため、
      display.SesameDisplayUpdateRequesterへ共通化した。
      既存の通信経路（SesameCommandSender / SesameApiClient）およびmobile側は変更していない。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameDemoMode.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameDemoModeTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/demo/DemoLockStateStore.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/display/SesameDisplayUpdateRequester.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameStatusRefreshActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameStatusListenerService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileStateResolver.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/ui/DeviceSelectionScreen.kt
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
      - docs/RELEASE_NOTES.md
      - docs/USER_GUIDE.md
      - scripts/version.properties
      - .markdownlint-cli2.yaml
      - CLAUDE.md
      - CHANGELOG.md
    検証コマンド: ./gradlew ktlintCheck / detekt / lintDebug / testDebugUnitTest test / assembleDebug
    検証結果: >
      成功 - 品質ゲート5コマンドがいずれもBUILD SUCCESSFUL。
      core.SesameDemoModeTestは8件すべて成功（failures=0 / errors=0）。
      Sesame APIへリクエストが飛ばないことは、デモ用uuidがMessageClient送信経路へ到達しない
      分岐をコード上で担保し、状態解決・コマンド実行・状態更新の3経路すべてに分岐を入れて
      確認した。実機（Pixel Watch 2）でのデモモードの表示・操作確認はBL-110として人手検証項目に
      登録済み。
      npx markdownlint-cli2 "**/*.md" は0 issues（MD024をsiblings_onlyへ緩和後）。
    関連ID:
      - BL-109

- date: 2026-09-06 17:15
  summary: タイルの状態ラベルの末尾省略を修正し、ストア用スクリーンショットを撮り直した
  details:
    変更内容: >
      ストア用スクリーンショットの撮り直し中に、複数デバイスの施錠状態が混在したタイルで
      状態ラベルが「施錠/解…」と末尾省略されることを実機で確認した。BL-102では操作ラベル
      （actionLabel）とデバイス変更チップのみを修正しており、状態ラベル（statusLabel）の
      見落としだった。MIXEDの「施錠/解錠混在」は7文字で、TYPOGRAPHY_TITLE2の表示幅
      （「全施錠中」の4文字は収まる）に対して長すぎる。
      文言を「一部解錠」（4文字）へ短縮した。1台でも解錠されているという利用者の行動に
      つながる事実を残す表現で、🔀アイコンと「タップで全施錠」が併記されるため意味は通る。
      あわせて状態ラベルのTextへsetMaxLines(2)・省略記号・中央揃えを指定し、今後文言を
      増やしたときに同じ末尾省略が起きない安全網を入れた。「通信中...」「スマホ未接続」は
      いずれも6文字で省略の可能性があるが実機で確認できていないため、文言は変更せず
      安全網による保護に留めた。
      ストア用スクリーンショットは全点を撮り直した。BL-102/BL-103/BL-101の修正が反映された
      状態で、Wear OS用にタイルの解錠状態と解錠確認画面の2枚を新規追加し5枚構成とした。
      解錠状態はユーザーの許可を得てSesame実機を実際に解錠して撮影し、撮影後に施錠へ戻している。
      スマートフォン用2枚は、システムバーのアイコンが暗色になり時刻・電池残量が判読できる
      ようになったことを確認できる。登録済みデバイスの表示名は従来どおり画像上でマスクした。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - docs/USER_GUIDE.md
      - docs/records/managed/DESIGN.md
      - docs/store/README.md
      - docs/store/images/screenshots/
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: >
      成功 - 品質ゲート184タスクがBUILD SUCCESSFUL、npx markdownlint-cli2も0 issues。
      実機（Pixel Watch 2）で、BL-102の修正により「タップで全解錠」と「変更」が省略・折り返し
      なしで表示されること、BL-103の修正によりスマートフォンのステータスバーが判読できること、
      BL-101の対応後にComplicationが「施錠」を表示しタップでデバイス変更できることを確認済み。
      本コミットのstatusLabel修正自体の表示確認は次回の実機検証で行う。
    関連ID:
      - BL-104

- date: 2026-09-06 16:55
  summary: Complicationが文字盤に載らない事象を切り分け、設定確定処理を防御的に修正した
  details:
    変更内容: >
      文字盤のComplicationへデバイスを割り当てても枠が空のままで、状態表示もタップも
      できないという報告を調査した。実機での切り分けにより、原因はアプリではなく
      Wear OSのシステムサービス側にあることを特定した。
      切り分けの過程で確認した事実は次のとおり。(1)SesameComplicationDataSourceServiceは
      enabled=true / exported=true で正しく登録され、cmd package query-servicesからも発見できる。
      (2)デバッグビルドで診断ログを入れたところ、ComplicationConfigurationActivityは
      instanceIdを正しく解決し、デバイス選択のコールバックも発火し、setResult(RESULT_OK)まで
      到達していた。(3)にもかかわらずonComplicationRequestは一度も呼ばれていなかった。
      (4)システム側のログに、文字盤編集の確定処理で例外が発生していた。
      com.google.wear.services.watchfaces.editing.WatchFaceEditingSessionController
      .commitFavoriteAndNotifyListener から AndroidXWatchFaceEngine.switchTo が呼ばれ、
      ImmutableMap.Builder.buildOrThrowで
      「Multiple entries with same key: 3=ComplicationData{mType=10} と 3=ComplicationData{mType=3}」
      （NO_DATAとSHORT_TEXTが同一スロットIDに重複）が投げられていた。この例外で確定処理が
      中断するため、枠がデータソースへ紐付かない。
      ウォッチを再起動したところ正常に動作するようになり、アンインストールと再インストールを
      繰り返した結果としてシステム側にスロットの不整合な状態が残っていたことが原因と判明した。
      アプリ側の実装は正常だったが、調査で見つかった脆さを2点修正した。
      ComplicationConfigurationActivityでsetResult(RESULT_OK)をrequestComplicationUpdateより
      前に呼ぶよう順序を入れ替え、更新要求をrunCatchingで囲んだ。更新要求は表示を即座に反映する
      ための最適化にすぎず失敗しても定期更新で回復するのに対し、setResult(RESULT_OK)は
      ユーザーの選択そのものを確定させる処理で、返さないと文字盤側がデータソースの選択を
      キャンセル扱いにする（BL-073）。従来は前者の失敗が後者を巻き添えにする構造だった。
      あわせて設定画面の各段階へLog.wで診断出力を追加した。リリースビルドでも
      -assumenosideeffectsで除去されないようLog.dではなくLog.wを用いる。出力内容は
      instanceIdとactionのみで、資格情報やデバイスuuidは含めない。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/ComplicationConfigurationActivity.kt
      - docs/records/managed/DESIGN.md
      - docs/USER_GUIDE.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: >
      成功 - 実機（Pixel Watch 2）で再起動後にComplicationが正常に表示され、タップでの
      デバイス変更と状態確認も動作することをユーザーが確認した。
    関連ID:
      - BL-101

- date: 2026-09-06 16:40
  summary: スマートフォン側のシステムバーのアイコンを暗色にして視認性を確保した
  details:
    変更内容: >
      targetSdk 36化の検証で撮影したスクリーンショットの比較中に、ステータスバーの時刻・
      アイコンが白のまま描画され、明るい背景に対してほとんど判読できない状態であることが
      判明した。targetSdk 35時点のスクリーンショットでも同一であり、targetSdkの引き上げによる
      回帰ではなく以前から存在した不具合である。
      targetSdk 35以降はエッジツーエッジ表示が必須で、アプリの背景がシステムバーの領域まで
      広がる。本アプリはMaterialThemeへcolorSchemeを渡しておらず既定のライトカラースキームを
      使うため、背景は常に明るい。MainActivityのonCreateで
      WindowCompat.getInsetsControllerからisAppearanceLightStatusBarsと
      isAppearanceLightNavigationBarsをtrueにし、システムバーのアイコンを暗色へ切り替えた。
      端末のダークモード設定にかかわらずアプリの配色は常にライトであるため条件分岐はせず、
      将来ダークテーマへ対応する場合はこの指定もテーマへ追随させる必要がある旨をKDocへ記した。
      ナビゲーションバーのアイコンも同様に白のままだったため、あわせて暗色にしている。
      実際の見え方の確認は実機が必要であり、デバッグビルドをインストールするとPlay配信版を
      署名不一致で置き換えてしまうため本対応では行わず、BL-097の人手検証へ集約する。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: 成功 - 品質ゲート184タスクがBUILD SUCCESSFUL。表示結果の確認は実機が必要なため未実施
    関連ID:
      - BL-103

- date: 2026-09-06 16:20
  summary: タイルの操作ラベルが省略される問題とデバイス変更チップの不自然な折り返しを修正した
  details:
    変更内容: >
      実機のスクリーンショットで、タイルの操作ラベルが「タップで全解…」と末尾で省略され、
      左下のチップが「デバイス変／更」と不自然な位置で2行に折り返していた。
      操作ラベルはSesameTileService側でsetMaxLinesを指定しておらず、ProtoLayoutのTextが
      既定で1行であるため、CAPTION1の7文字（「タップで全解錠」）が幅に収まらず省略されていた。
      状態ラベルより一段小さいCAPTION2へ変更したうえでsetMaxLines(2)と中央揃えを指定し、
      収まらない場合も末尾で切れずに折り返すようにした。文言自体は「タップで」という操作の
      示唆を残すため変更していない。
      デバイス変更チップはsetMaxLines(2)が指定済みだったが、CAPTION2の6文字がチップ幅に
      収まらず折り返していた。直上のデバイス名チップで「全デバイス」（5文字）が1行に収まって
      いることから、このチップに収まるのは5文字程度と判断し、「変更」へ短縮した。直上に対象
      デバイス名が表示されている文脈で意味が通るため、情報量の低下は許容できると判断した。
      文言変更に伴いdocs/USER_GUIDE.mdのタイル操作表の記載も追随させた。
      なおタイルの実際のレンダリング結果はビルドでは検証できず、確認にはデバッグビルドを
      ウォッチへインストールする必要がある。それはPlay配信版を署名不一致で置き換えてしまうため
      本対応では行わず、実機での見え方の確認はBL-097の人手検証へ集約する。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - docs/USER_GUIDE.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: >
      成功 - 品質ゲート184タスクがBUILD SUCCESSFUL。npx markdownlint-cli2も0 issues。
      表示結果そのものの確認は実機が必要なため未実施（BL-097へ集約）。
    関連ID:
      - BL-102

- date: 2026-09-06 15:45
  summary: 既存デバイスの資格情報を編集すると新規追加になる不具合を修正した
  details:
    変更内容: >
      Play Console経由でインストールしたアプリの実機確認で、スマートフォン側の設定画面から
      既存デバイスを編集して「更新」を押すと、更新ではなく新規追加になる不具合が報告された。
      原因はCredentialsSettingsScreenの保存処理が
      credentialsList.filterNot { it.uuid == formState.uuid } + formState.toCredentials()
      となっていた点にある。編集対象を特定するキーとして、編集を開始した時点のuuid
      （formState.editingUuid）ではなくフォームの現在値（formState.uuid）を使っていたため、
      編集中にuuidを変更すると元の項目が絞り込みに掛からず残り、新しい項目が追加されて
      重複していた。あわせて、filterNotの結果へ追記する実装のため編集した項目が常にリスト末尾へ
      移動し、一覧の並び順が保持されない問題もあった。
      保存時のリスト更新ロジックをCredentialsListEditorとして画面から切り出し、Android非依存の
      objectにしてユニットテストを追加した。挙動は次の3点。(1)新規追加は末尾へ追加するが、
      入力uuidが既存項目と一致する場合は上書きする（従来からの画面仕様を維持）。(2)既存項目の
      編集はeditingUuidで対象を特定し、リスト内の同じ位置で置き換える。(3)編集でuuidを他の
      既存項目と同じ値に変更した場合は、衝突した項目を取り除く（uuidをデバイスの一意キーとして
      扱うため重複を残さない）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsListEditor.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/credentials/CredentialsListEditorTest.kt
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: >
      成功 - 品質ゲート184タスクがBUILD SUCCESSFUL。新規追加したCredentialsListEditorTestは
      6件すべて通過（failures=0, errors=0）。テストのsecretKeyには実資格情報を用いず、
      AES-128鍵長を満たすダミー値を使用している。
    関連ID:
      - BL-100

- date: 2026-09-06 14:05
  summary: targetSdkを36へ引き上げ、AGP 8.13.0 / Gradle 8.13へ更新した
  details:
    変更内容: >
      Play Consoleへのアップロードで「現在、お客様のアプリはAPIレベル35を対象にしています。
      APIレベル36以上を対象にする必要があります」というエラーが出たため対応した。
      targetSdkはcompileSdkを超えられないため、両方を36へ引き上げた。
      AGP 8.7.3のままcompileSdk 36でもビルドは通るが「This Android Gradle plugin (8.7.3) was
      tested up to compileSdk = 35」の警告が出る。ストア配布物を未テストの組み合わせで
      ビルドすることになるため、ユーザー判断によりAGPを更新する方針とした。最新のAGP 9.4.0は
      Gradle 9.6を要求しメジャー跨ぎの破壊的変更を伴うため採用せず、compileSdk 36
      （最大API 36.1）に対応する8.x系列の最新であるAGP 8.13.0を選択した。AGP 8.13.0は
      Gradle 8.13を要求するため、gradlew wrapperタスクでラッパーも更新した。JDKは17のままで
      要件を満たす。あわせてAndroid SDK Platform 36とbuild-tools 36.0.0をローカルへ導入した。
      更新後、core:testの5件がIllegalAccessError（class SesameCredentialsStore tried to access
      private field SesameCredentials.Companion）で失敗した。原因はSesameCredentialsが
      @Serializableでありながらprivate companion objectを宣言していたことにある。
      kotlinx.serializationは@Serializableクラスのcompanion objectへserializer()を生成するため、
      companionをprivateにすると生成されたCompanionフィールドもprivateになり、他クラスからの
      シリアライズ時にアクセスできない。AGP 8.7.3では顕在化していなかった潜在不具合であり、
      アップグレードによって表面化したもの。定数AES_128_KEY_LENGTH_BYTESは同一ファイル内でのみ
      使用されていたため、companion objectを廃してファイルプライベートのトップレベル定数へ移し、
      なぜトップレベルへ置くのかをKDocへ明記した（将来companionへ戻されることを防ぐため）。
      @Serializableかつprivate companion objectを持つクラスが他に存在しないことも横断確認した。
    変更ファイル:
      - gradle/libs.versions.toml
      - gradle/wrapper/gradle-wrapper.properties
      - mobile/build.gradle.kts
      - wear/build.gradle.kts
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentials.kt
      - scripts/version.properties
      - README.md
      - docs/records/managed/DESIGN.md
      - .gitignore
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      scripts\release-build.bat -VersionCode 3 -WearVersionCode 1001 -VersionName 0.9.0
    検証結果: >
      成功 - 品質ゲート184タスクがBUILD SUCCESSFUL。core:testを含む全テストが通過し、
      compileSdkに関する警告も出力されないことを確認した。なお検証中、メモリ不足
      （物理メモリ7.7GB中の空きが1.4GB）によりGradleデーモンがクラッシュしたため、
      --no-daemon --max-workers=2 で実行している。
    関連ID:
      - BL-099

- date: 2026-09-06 10:27
  summary: リリースビルドスクリプトをmobile/wearの2成果物へ対応させた
  details:
    変更内容: >
      BL-090でwearを独立applicationモジュールへ分離したため、リリースビルドスクリプトを
      2つのAABを生成する構成へ更新した。scripts/version.propertiesへWEAR_VERSION_CODEを追加し、
      GoogleがversionCodeを全フォームファクタで一意であることを要求する点に対応して、mobileを
      1始まり、wearを1001始まりの独立した系列で管理する。versionNameは利用者から見たアプリの
      バージョンであり、ストア掲載ページも1つであるため両者で共通とした。
      release-build.ps1へ-WearVersionCodeパラメータを追加し、:mobile:bundleReleaseと
      :wear:bundleReleaseを1回の実行でビルドするようにした。両者のversionCodeが同一になった
      場合はビルド前に例外を投げるガードも追加した。完了メッセージには各AABの出力先と、
      どちらのトラックへアップロードすべきかを明示する。
      あわせて、version.propertiesの書き戻しをSet-ContentからSystem.IO.File.WriteAllTextへ
      変更した。Set-Contentは環境依存の改行コード（CRLF）を付与するため、値が変わっていなくても
      ビルドのたびに末尾改行の差分がgitに現れていた。改行をLFに固定しBOMなしUTF-8で書き出す
      ことで解消した。release-build.ps1自体はWindows PowerShell 5.1対策でUTF-8 BOM付き・CRLFの
      ため、編集後にその形式を復元している。
    変更ファイル:
      - scripts/release-build.ps1
      - scripts/release-build.bat
      - scripts/version.properties
    検証コマンド: >
      scripts\release-build.bat -VersionCode 1 -WearVersionCode 1001 -VersionName 0.9.0 /
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: >
      成功 - スクリプト実行でBUILD SUCCESSFULとなり、mobile-release.aab（versionCode=1）と
      wear-release.aab（versionCode=1001）が1回の実行で生成された。version.propertiesの
      git差分はWEAR_VERSION_CODE=1001の1行追加のみで、従来出ていた改行コードのみの差分が
      発生しないことを確認した。品質ゲート6タスクもBUILD SUCCESSFUL。
    関連ID:
      - BL-093

- date: 2026-09-06 10:22
  summary: wearをdynamic featureから独立applicationモジュールへ分離し、Wear OS専用トラックへ対応した
  details:
    変更内容: >
      Google Play Consoleへの初回アップロードで「Wear OSのバンドルを削除し、専用のWear OS
      トラックを作成してください」というエラーが発生した。調査の結果、wearモジュールの
      uses-feature android.hardware.type.watch がbase（mobile）のマージ済みマニフェストへ
      取り込まれ、required属性の既定値trueによってアプリ全体が腕時計必須と宣言されていた。
      この状態ではPlayがバンドル全体をWear OSアプリと分類し、専用トラックを要求すると同時に
      スマートフォンを配信対象から除外する。ローカルのadb installはPlayのデバイスフィルタを
      通らないため実機検証では表面化していなかった。
      Googleの公式ドキュメントは単一App BundleへWear OSをdynamic featureとして同梱する構成を
      非サポートと明記し（Wear OS APKs are separate from mobile APKs / You cannot use a single
      app bundle with a dynamic feature module for Wear OS）、required="false"を付ける回避策も
      非サポートとしている。そのためBL-036で採用した単一AAB構成を改め、applicationIdを
      com.sesamiwear.mobileで共通にしたまま2つの独立したAABを生成する構成へ変更した。
      wear/build.gradle.ktsをcom.android.applicationへ変更し、applicationId・targetSdk・署名設定・
      R8設定を自前で保持させた。versionCodeは全フォームファクタで一意である必要があるため
      appWearVersionCodeプロパティで1001始まりの独立系列とした。:mobile依存はAGPの
      dynamic feature制約によるもので、wearのソースはmobileのクラスを一切参照していなかったため
      削除できた。baseのproguard設定を継承しなくなるためwear/proguard-rules.proを追加した。
      wearのマニフェストからdist:moduleブロックを削除し、uses-feature watchは宣言を維持した。
      ウォッチ用アイコン（ic_launcher_wear系4ファイル）はmobile側にあったためwearへgit mvで
      移設し、application要素へandroid:icon/roundIconを指定した。BL-066でwear.MainActivityから
      LAUNCHER intent-filterを除去していたのは、baseモジュールが常にウォッチへもインストール
      されアイコンが2つ表示されるという単一AAB構成固有の問題への対処だったが、分離後は
      ウォッチ側にmobileが存在せずアイコンが1つも表示されなくなるため復活させた。
      mobile側はdynamicFeatures指定とwear_module_title文字列リソース、wearのために追加していた
      guava依存を削除し、MainActivityからPackageManager.FEATURE_WATCH判定による
      wear.MainActivityへの委譲処理も削除した。guavaはwear側でcompileOnlyからimplementationへ
      変更し自前で解決させた。
    変更ファイル:
      - wear/build.gradle.kts
      - wear/proguard-rules.pro
      - wear/src/main/AndroidManifest.xml
      - wear/src/main/res/drawable/ic_launcher_wear_background.xml
      - wear/src/main/res/drawable/ic_launcher_wear_foreground.xml
      - wear/src/main/res/mipmap-anydpi-v26/ic_launcher_wear.xml
      - wear/src/main/res/mipmap-anydpi-v26/ic_launcher_wear_round.xml
      - mobile/build.gradle.kts
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - mobile/src/main/res/values/strings.xml
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      ./gradlew :mobile:bundleRelease :wear:bundleRelease
    検証結果: >
      成功 - 品質ゲート6タスクがBUILD SUCCESSFUL（176タスク）。リリースAABは
      mobile-release.aab（3.69MB、baseのみでwearを含まない）とwear-release.aab（3.82MB）が
      独立して生成され、いずれも同一のKeystore（META-INF/SESAMI-W.RSA）で署名済み。
      根本原因の解消をマージ済みマニフェストで確認: mobile側にandroid.hardware.type.watchが
      含まれず、wear側にのみ存在する。wear側のpackageはcom.sesamiwear.mobile（mobileと共通）、
      versionCodeは1001（mobileの1と非衝突）、LAUNCHER intent-filterが1件存在する。
    関連ID:
      - BL-090
      - BL-091
      - BL-092

- date: 2026-09-05 23:02
  summary: アプリのversionNameを0.1.0から0.9.0へ更新した
  details:
    変更内容: >
      Google Play公開前の公開準備段階に合わせ、ユーザー指示によりversionNameを0.1.0から
      0.9.0へ引き上げた。versionNameは3箇所（scripts/version.propertiesの永続値、
      mobile/build.gradle.ktsの既定値、scripts/release-build.ps1のversion.properties読み込み
      失敗時フォールバック）に分散しているため、すべてを0.9.0へ揃えた。release-build.ps1は
      Windows PowerShell 5.1対策でUTF-8 BOM付きのため、編集後にBOMが保持されていることを
      確認済み（BL-035）。あわせて表記の一貫性のため、docs/RELEASE_NOTES.mdのバージョン見出しと
      .github/ISSUE_TEMPLATE/bug_report.ymlのバージョン入力欄プレースホルダ、BACKLOG.md
      BL-085の初回リリースタグ名（v0.9.0）も更新した。versionCodeはリリースビルド時に
      scripts/release-build.batが自動採番するため、現行値（1）のまま変更していない。
      機能面の変更はないためdocs/RELEASE_NOTES.mdの記載内容（新機能・既知の問題）は据え置き、
      見出しのバージョン番号のみを変更した。
    変更ファイル:
      - scripts/version.properties
      - mobile/build.gradle.kts
      - scripts/release-build.ps1
      - docs/RELEASE_NOTES.md
      - .github/ISSUE_TEMPLATE/bug_report.yml
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      npx markdownlint-cli2 / bug_report.ymlのYAML構文チェック
    検証結果: 成功 - 段階Bの品質ゲートすべてBUILD SUCCESSFUL、markdownlint 0 issues、YAML構文OK
    関連ID:
      - BL-090

- date: 2026-09-05 22:41
  summary: リリースビルドから切り分け用デバッグログ（Log.d / Log.v）を除去するR8ルールを追加した
  details:
    変更内容: >
      BL-072/BL-073の切り分け用に残置しているLog.d呼び出し（mobile.messaging
      .SesameMessageListenerService、wear.messaging.SesameResultListenerService、
      wear.tile.SesameTileService、wear.complication.SesameComplicationDataSourceService）が、
      isMinifyEnabled=trueのリリースビルドにもそのまま残っていた。出力内容はパス・成否・
      状態の真偽値のみで資格情報は含まないが、Public公開・ストア配布を控え、配布物へ内部状態を
      残さないためmobile/proguard-rules.proへ-assumenosideeffectsを追加し、
      android.util.LogのdとvをR8の最適化で除去するようにした。障害調査に必要なLog.w/Log.eは
      残している。wearはdynamic featureでminifyEnabled/proguardFilesを持たず、base（mobile）側の
      設定がアプリ全体へ適用されるため、追加はmobile側のみで足りる（wear/proguard-rules.proは
      BL-036の統合時に廃止済み）。あわせてDESIGN.mdのProGuard/R8の記述が「mobile/wear双方の
      proguard-rules.pro」という統合前の内容のままだったため、現在の構成へ更新した。
      デバッグビルドの挙動は変更していない。
    変更ファイル:
      - mobile/proguard-rules.pro
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug /
      ./gradlew :mobile:assembleRelease / npx markdownlint-cli2
    検証結果: >
      成功 - 段階Bの品質ゲートすべてBUILD SUCCESSFUL、R8適用の:mobile:assembleReleaseも
      BUILD SUCCESSFUL（ルール追加によるビルド破壊がないことを確認）、markdownlint 0 issues
    関連ID:
      - BL-083

- date: 2026-09-05 03:10
  summary: BL-055（単一Tileのデバイス切り替え）の実機確認完了を記録へ反映し、コード中の
    人手検証待ち注記を確認済みへ更新した
  details:
    変更内容: >
      ユーザーへのヒアリングにより、実機（Pixel Watch + Sesame 5実機2台以上）で
      単一Tileのデバイス切り替えを検証した結果を確認した。デバイス切り替え後の
      デバイス名・状態表示（🔒/🔓）が選択デバイスの実態と一致すること、施錠/解錠が
      選択中のデバイスのみに作用すること、成否をハプティクスで判別できること、
      「全デバイス」選択時の一括操作と🔀混在表示が正しく動作することの4点すべてで
      問題がないと報告されたため、BL-055を完了としてBACKLOG.mdから削除した。
      あわせて、削除済みidへの参照が残らないよう、KDocの「BL-055で人手検証」という
      注記を「BL-055の人手検証で2026-09-05に確認済み」へ更新した（コメントのみの変更で
      挙動への影響はない）。DESIGN.mdは、Tileの多重インスタンス追加が実機で行えない
      未確認事項の記述を残したまま、単一Tileでのデバイス切り替えが実機確認済みである旨を
      追記した。Google Play公開まわりの人手検証3件（BL-033/BL-034/BL-038）は
      ユーザー判断により保留のまま据え置いた。
    変更ファイル:
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
      - CHANGELOG.md
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameDeviceListReader.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameStatusSnapshotReader.kt
    検証コマンド: >
      npx markdownlint-cli2 "CHANGELOG.md" "docs/records/managed/*.md" /
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
    検証結果: 成功 - markdownlint 0 issues、Gradle品質ゲート BUILD SUCCESSFUL
    関連ID:
      - BL-055

- date: 2026-09-05 02:05
  summary: Complicationのデバイス設定導線をWear OS標準の設定導線へ対応させ、設定済み枠の
    デバイス変更を可能にした（BL-073）
  details:
    変更内容: >
      BL-072の修正で状態文言が表示されるようになった結果、デバイス割り当ての導線が
      「未設定枠に出る『タップして設定』を押す」1経路しかないことが実機確認で顕在化した。
      設定済みComplicationにはtapActionを付けていなかったため、一度割り当てると変更できず、
      複数の枠へ別々のデバイスを割り当てることも実質できなかった。次の2点で解消した。
      (1) Wear OS標準の設定導線への対応: wear/AndroidManifest.xmlの
      SesameComplicationDataSourceServiceへ、ComplicationDataSourceServiceの
      METADATA_KEY_DATA_SOURCE_CONFIG_ACTION（android.support.wearable.complications.
      PROVIDER_CONFIG_ACTION）のmeta-dataを追加し、ComplicationConfigurationActivityへ同じactionと
      CATEGORY_DATA_SOURCE_CONFIG（android.support.wearable.complications.category.PROVIDER_CONFIG）
      を持つintent-filterを追加した。actionは他アプリと混同しないようアプリ固有の名前
      （com.sesamiwear.wear.complication.CONFIG_COMPLICATION）とした。Activity側は、システムが渡す
      EXTRA_CONFIG_COMPLICATION_ID（Int）を優先し、無ければ従来のtapAction経由のextra（文字列）を
      使う形で対象instanceIdを解決する。選択完了時にsetResult(RESULT_OK)を返すようにした
      （RESULT_OKを返さないと文字盤側がデータソース選択自体をキャンセル扱いにするため）。
      これにより、文字盤のピッカーで枠ごとにデータソースを選んだ直後にデバイス選択画面が開く。
      (2) 設定済み枠のデバイス変更: SesameComplicationDataSourceServiceのtapAction生成を
      configurationTapAction()へ切り出し、未設定枠・設定済み枠・状態解決失敗時のフォールバック表示の
      すべてへ付与した。タップで開くのは設定画面のみで、施錠/解錠のコマンド送信は行わない
      （操作導線はTile側に限定する既存方針は維持）。
      いずれもAndroid Complications API依存でユニットテスト対象外のため、テストの追加はない。
      実機での確認はBL-073として人手検証項目に登録した。
      あわせて、実機で状態文言の表示を確認できたBL-072をBACKLOGから削除し、BL-055の完了条件から
      複数Complicationのデバイス別表示（BL-073へ移管）を外して依存を解消した。
    変更ファイル:
      - wear/src/main/AndroidManifest.xml
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/ComplicationConfigurationActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest / ./gradlew assembleDebug / npx markdownlint-cli2 "**/*.md"
    検証結果: >
      成功 - 品質ゲート5コマンドすべてBUILD SUCCESSFUL。markdownlintは0 issues。
      複数枠への割り当て・設定済み枠の変更は実機確認が必要なためBL-073として人手検証に残す。
    関連ID:
      - BL-073
      - BL-072
      - BL-055

- date: 2026-09-05 01:37
  summary: Complicationの状態文言が空欄になる不具合（BL-072）へ、想定3要因すべてに対する
    防御的修正と切り分け用ログを実装した
  details:
    変更内容: >
      文字盤のComplication枠にデバイスを割り当てても状態文言が表示されない事象（BL-072）について、
      実機ログを採取できない状態では想定した3要因を切り分けられないため、いずれの要因であっても
      表示が復帰するよう防御的な修正をまとめて実装した。
      (a) 要求ComplicationTypeへの対応: SUPPORTED_TYPESへLONG_TEXTを追加し、
      onComplicationRequestがrequest.complicationTypeを見てShortTextComplicationData／
      LongTextComplicationDataを作り分けるようにした。SHORT_TEXTしか返せない実装では、LONG_TEXT枠へ
      配置された際に型不一致でデータが破棄され空欄になるため。getPreviewDataも両型へ対応させた
      （従来はSHORT_TEXT以外でnullを返していた）。LONG_TEXTは表示領域に余裕があるため、
      SesameComplicationContent.longText()でデバイス名と状態文言を併記する。
      (b) データ未返却の排除: 状態解決（Wearable APIの往復）をwithTimeout(10秒)で打ち切り、
      runCatchingで例外・タイムアウトを捕捉して「不明」表示のデータへフォールバックするようにした。
      従来はCoroutineScope(Dispatchers.IO).launch内で例外が発生するとlistener.onComplicationDataが
      呼ばれず、Complicationが空欄のままになる経路があった。対応外の型を除き、必ずデータを返す。
      (c) 更新契機の追加: マニフェストのUPDATE_PERIOD_SECONDSを0（定期更新なし）から600へ変更し、
      ComplicationDataSourceUpdateRequesterからの更新要求が届かない場合でも定期更新で表示が
      回復するようにした（Wear OSの実効最小間隔は300秒のため、消費電力を考慮して600秒とした）。
      あわせて切り分け用にLog（TAG=SesameComplication）で、要求されたComplicationType・
      データ返却有無・解決した状態・例外内容を出力する。実機で再現する場合は
      adb logcat -s SesameComplication で3要因のどれに該当するかを判別できる。
      Complication表示はAndroid Complications API依存でユニットテスト対象外のため、テストは
      Android非依存のSesameComplicationContent.longText()に対して追加した。
      本修正の実機確認はBL-072として人手検証項目に残している。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationContent.kt
      - wear/src/main/AndroidManifest.xml
      - wear/src/test/kotlin/com/sesamiwear/wear/complication/SesameComplicationContentTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck / ./gradlew detekt / ./gradlew lintDebug /
      ./gradlew testDebugUnitTest / ./gradlew assembleDebug / npx markdownlint-cli2 "**/*.md"
    検証結果: >
      成功 - 品質ゲート5コマンドすべてBUILD SUCCESSFUL（detektのReturnCount違反は
      resolveComplicationDataのearly returnを1箇所へ集約して解消）。markdownlintは0 issues。
      実機での表示確認は自動実行対象外のためBL-072として人手検証に残す。
    関連ID:
      - BL-072

- date: 2026-08-30 17:59
  summary: ランチャーアイコンにコンプリケーション風リングを追加し、人手検証完了項目
    （BL-063/064/066/067/071）をBACKLOGから削除した
  details:
    変更内容: >
      人手検証タスクのヒアリングをユーザーと実施し、実機確認の結果を反映した。
      BL-063（Tileデザイン）/BL-064（コマンド成功後のTile自動更新）/BL-066（アプリアイコン1つへの
      統一と設定画面起動）/BL-067（デバイス切替時の無関係画面の非表示）/BL-071（全デバイス一括
      施錠/解錠）はいずれも実機で期待どおり動作することが確認できたため、BACKLOGから削除した。
      BL-042（ランチャーアイコンのリング）は「リングが全く表示されない」との報告を受けて調査した
      ところ、検証NGではなく前提の失効であることが判明した。mobile/wear統合（BL-036）と1アイコン
      統一（BL-066）により、ランチャーへ表示されるアイコンはmobileのandroid:icon="@mipmap/ic_launcher"
      （実体はic_launcher_foreground.xml）のみとなったが、コンプリケーション風リングのpathは
      Tile/Complicationピッカー用のic_launcher_wear_foreground.xml（wear側マニフェストの
      TileService/ComplicationDataSourceServiceのandroid:iconから参照）にしか存在せず、
      ランチャーアイコンには元から描かれていなかった。ユーザー判断により「ランチャーにもリングを
      追加して意匠を統一する」方針を採り、ic_launcher_foreground.xmlへリングのpathを追加した。
      wear側は Tileピッカーでの見え方に合わせて意匠全体を50%縮小している（BL-068）が、
      ランチャーアイコンは等倍で描画する。リングは半径30・ストローク幅4で外周が中心(54,54)から
      32dpとなり、Adaptive Iconのセーフゾーン（108dp viewport中心から半径33dp）内に収まるため
      円形マスクでも欠けない。実機での再確認はBL-042として残している。
      BL-055（複数Tile/Complicationのデバイス別対応）は、実機（Wear OS 7のPixel Watch）で同一Tileを
      2つ以上追加できないことが判明した。Watch上のタイル編集の「＋」でもスマホのPixel Watchアプリの
      タイル管理画面でも、追加済みのSesami Wearはチェック済み扱いで再選択できない。コード側は
      tileIdごとの多重インスタンス対応済みでマニフェスト定義にも制約はないため、Wear OS側の
      タイル（ウィジェット）管理UIの制約と推測されるが未確認であり、DESIGN.mdへ未確認事項として
      追記した。ユーザー判断により、BL-055の完了条件を「単一Tileでのデバイス切り替え」と
      「複数Complication枠でのデバイス別表示」の2点へ縮小した。あわせて、Complication枠へ
      デバイスを割り当てても状態文言が表示されず空欄になる不具合を新たに検出し、切り分け観点を
      添えてBL-072として登録した（Tile側は同一のSesameTileStateResolverで正常表示できているため
      Complication固有の要因を疑う）。Complicationが設定済み状態でtapActionを持たない読み取り専用
      表示であることは設計どおりであり、ユーザー確認のうえ現行仕様を維持する。
    変更ファイル:
      - mobile/src/main/res/drawable/ic_launcher_foreground.xml
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/EXECUTE.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（5タスクすべて成功）
    関連ID:
      - BL-042
      - BL-055
      - BL-063
      - BL-064
      - BL-066
      - BL-067
      - BL-071
      - BL-072

- date: 2026-08-23 17:18
  summary: 管理ドキュメントの最新化・最適化を実施し、DESIGN.mdを履歴蓄積型からFORMAT.md準拠の
    最新版統合設計書へ全面圧縮した
  details:
    変更内容: >
      ユーザーから「管理している各種ドキュメントの最新化・最適化を現コードベースで実施したい」との
      依頼を受け、DESIGN.md/BACKLOG.md/EXECUTE.md/README.md/CONTRIBUTING.md/
      .github/copilot-instructions.md/docs/store配下/docs/INSTALL.md/CHANGELOG.mdを現在の
      ソースコード・BACKLOG進捗と突き合わせて監査した。
      BACKLOG.mdの進行中項目（BL-063/064/066/067/071等）はいずれもコード実装済みで実機
      インストールまで完了しているが、ユーザーによる実機での最終確認待ちの状態が正しく反映されて
      おり、コードとの齟齬は見つからなかった（BL-052〜054も実装済みと確認、TileConfigurationActivity/
      ComplicationConfigurationActivity/TileDeviceAssignmentStore/ComplicationDeviceAssignmentStore/
      SesameDeviceListSyncer/SesameDeviceListReaderの各ソースで裏付け確認済み）。README.md/
      CONTRIBUTING.md/.github/copilot-instructions.mdもCLAUDE.mdと整合しており変更不要だった。
      2点の実質的な問題を検出し対応した。
      (1) docs/store/STORE_LISTING.mdが、BL-036（wearのdynamic feature化、単一AAB・単一
      applicationId・単一Play Store掲載ページへの統合）以前の制約（BL-031、mobile/wearを別アプリ
      として登録する必要がある）をそのまま参照しており、実際のPlay Console登録時に誤った手順を
      招くおそれがあったため、単一アプリとして登録する旨へ修正した。
      (2) DESIGN.md（801行）が、docs/records/spec/FORMAT.mdの規定
      （「文書は履歴を積み上げず、最新版を維持する」）に反し、Tileデザインの4回にわたる試行錯誤
      など個々のイテレーションのやり取りをそのまま累積した「経過日記」になっていた。ユーザーへ
      AskUserQuestionで扱いを確認し「現状（最終形）だけへ全面圧縮」を選択された。実装済み機能を
      REQ番号の時系列ではなく機能領域別（暗号・署名/APIクライアント/資格情報管理/プロトコル定義/
      mobile側処理/wear側送受信/Tile/Complication/操作画面/ハプティクス/エントリポイント/
      アイコン/リリースビルド/Google Play配布方式/GitHub公開対応/ストア掲載情報）に再構成し、
      各機能の最終仕様・未確認事項・技術制約（AGPのdynamic feature制約、secretKeyがhex形式である件、
      detektのLongMethod/TooManyFunctions実測上限、PowerShell BOM問題等）はすべて保持したまま、
      同一機能に対する複数世代の指摘・修正の応酬（「1回目→指摘→2回目→再指摘→…」の記述）を
      圧縮した。BL-064（コマンド成功後にTileが自動更新されない未解決問題）等の既知の未解決事項も
      引き続き明記した。結果、801行から446行（約44%減）になった。REQ-XXXの通し番号はEXECUTE.md
      （履歴ログ、変更対象外）のみが参照しており他ドキュメントからの参照がないことをgrepで
      確認した上で除去した。
    変更ファイル:
      - docs/records/managed/DESIGN.md
      - docs/store/STORE_LISTING.md
    検証コマンド: npx markdownlint-cli2 "**/*.md"
    検証結果: 成功 - 0 issues in 0 files（23ファイル、DESIGN.md書き換え直後にMD004
      （リスト記号の不統一、折り返し行頭の"+"がリストマーカーと誤認識された）が1件検出されたため
      文言を調整し再実行して解消を確認）
    関連ID: []

- date: 2026-08-23 21:30
  summary: GitHub公開に向けたレビューを実施し、LICENSE追加・非公式アプリの明記・CODEOWNERS修正を行った
  details:
    変更内容: >
      ユーザーから「GitHub登録・公開にあたって不備や対応したほうがいい点がないかレビューして、
      必要に応じて対応して」「ライセンス侵害などの観点も含めて」との依頼を受けた。
      現在のツリー・全git履歴を対象に秘密情報のハードコード・local.properties/keystore等の
      誤コミット・個人情報の混入を確認したが問題は見つからなかった（.gitignoreも適切）。
      core.api.SesameCommand/SesameCommandSigner/SesameStatusがpysesame3
      （https://github.com/mochipon/pysesame3）を参照している件についてはWebFetchで
      同リポジトリのライセンスを確認しMIT Licenseであることを確認した。ソースコードの
      逐語的コピーではなくプロトコル仕様（コマンドコード・署名アルゴリズム・レスポンス構造）を
      参照した独自実装であり、コメントで出典も明記済みのため侵害リスクはないと判断した。
      一方、本リポジトリ自体にLICENSEファイルが存在しないことが判明した（LICENSEがない場合、
      法的にはデフォルトで著作権者に権利が留保され、公開しても他者による使用・改変・再配布が
      許可されない状態になる）。ユーザーへAskUserQuestionでライセンス種別を確認した結果、
      MIT Licenseを選択（Android/Wear OSアプリ・pysesame3参考実装ともに一般的な選択であり、
      CANDY HOUSE社への影響はライセンス種別ではなく非公式アプリの明記で対応する方針とした）。
      LICENSEファイル（MIT、Copyright 2026 filderschoice。git logの最古コミットが2026年のため
      単年表記とした）を新規作成し、READMEへライセンスセクション（pysesame3への謝辞含む）を
      追加した。あわせてREADME.md/docs/store/STORE_LISTING.mdへ「本アプリは個人による非公式
      アプリであり、CANDY HOUSE株式会社とは提携・協力関係にない」旨の明記を追加し、
      .github/CODEOWNERSのプレースホルダー（@your-org/ai-platform等、テンプレート由来の
      実在しないチーム名）を実際のGitHubユーザー名（@filderschoice）へ修正した。
    変更ファイル:
      - LICENSE（新規）
      - README.md
      - docs/store/STORE_LISTING.md
      - .github/CODEOWNERS
    検証コマンド: npx markdownlint-cli2 "**/*.md"
    検証結果: 成功 - 0 issues in 0 files（23ファイル）。LICENSE/CODEOWNERSはMarkdown対象外の
      ためlint対象に含まれないが、内容は目視確認済み
    関連ID: []

- date: 2026-08-23 20:40
  summary: Tileから登録済み全デバイスへ一括で施錠/解錠を行える機能を追加した（BL-071）
  details:
    変更内容: >
      ユーザーから「個別デバイスの操作はできたので登録済みすべての施錠/解錠を行うことも
      できるようにしたい。Tileだとデバイス選択で全デバイスを選択可能とし、登録済みの
      デバイスすべてに対して操作を行えるように。ステータス表示はデバイスの状態に依存すると
      思うが、そこはうまく表現して現状態を表示できるように」との要望を受けた。
      core.SesameWearProtocolへ「全デバイス」を表す特別な値ALL_DEVICES_TARGET_UUID
      （"__all_devices__"、実際のSesame uuidと衝突しない固定文字列）を追加。
      core.TileDisplayStateへMIXED（施錠/解錠が混在）を追加しisActionableをtrueにした。
      core.TileDisplayStateResolverへresolveAggregate（複数デバイスのロック状態リストから
      集約状態を決定。1台でも未取得(null)があれば安全側でUNKNOWN、全台施錠でLOCKED、
      全台解錠でUNLOCKED、それ以外はMIXED）を追加。
      wear.ui.DeviceSelectionScreenは登録済みデバイスが2台以上の場合のみ先頭に「全デバイス」の
      選択肢を表示するようにした（1台のみの場合は個別選択と等価で冗長なため出さない）。
      新規wear.tile.SesameTileStateResolverへTile/Complication共通の表示名・状態解決ロジックを
      集約し、SesameTileService/SesameComplicationDataSourceServiceが重複させていた
      DataItem読み取り・staleness判定・requestStatus送信のロジックをこちらへ統合した
      （対象uuidがALL_DEVICES_TARGET_UUIDなら登録済み全デバイスの状態をresolveAggregateで
      集約、それ以外は単一デバイスの状態を解決）。新規wear.action.SesameActionTargetResolverで
      コマンド送信・状態更新の対象uuid一覧を解決（全デバイス時は登録済み全uuidのリスト）し、
      SesameActionActivity/SesameStatusRefreshActivityがループで各デバイスへ個別に
      lock/unlock/status-requestメッセージを送信するようにした（mobile側の
      SesameMessageListenerServiceは既存の単一デバイス処理をそのままN回受けるだけで対応でき、
      mobile側の変更は不要だった）。
      wear.tile.SesameTileActionsはMIXED状態でタップ時に「全施錠」を提示する（迷ったら安全側の
      方針）。wear.tile.SesameTileContent/wear.complication.SesameComplicationContentへ
      MIXED用のアイコン（🔀）・ラベル・背景色（紫）を追加し、statusLabel/actionLabelへ
      isAllDevicesパラメータ（デフォルトfalse）を追加して全デバイス時は「全施錠中」等の文言に
      切り替えた。SesameActionActivityの施錠/解錠確認画面（BL-070）のボタンラベルも全デバイス
      時は「全施錠」「全解錠」に切り替えた。Complicationは設定済み状態ではtapActionを持たない
      読み取り専用表示のため、全デバイス選択時も集約状態の表示のみでコマンド送信は行わない
      （Tile側のみが操作対象）。
      実装にあたり、detektのTooManyFunctions/LongMethod制約（過去のイテレーションで判明した
      クラス内関数数10以下・関数60行未満の実測上限）を踏まえ、状態解決ロジックを
      SesameTileService/SesameComplicationDataSourceServiceのクラス内に増やすのではなく、
      別ファイルの新規object（SesameTileStateResolver/SesameActionTargetResolver）へ切り出す
      設計にした（既存クラスの関数数を増やさずに機能追加できた）。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/main/kotlin/com/sesamiwear/core/TileDisplayState.kt
      - core/src/main/kotlin/com/sesamiwear/core/TileDisplayStateResolver.kt
      - core/src/test/kotlin/com/sesamiwear/core/TileDisplayStateResolverTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameWearProtocolTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileStateResolver.kt（新規）
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileActions.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/tile/SesameTileActionsTest.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/tile/SesameTileContentTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/complication/SesameComplicationContentTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/ui/DeviceSelectionScreen.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionTargetResolver.kt（新規）
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameStatusRefreshActivity.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（1回で成功、コンパイルエラー・detekt指摘なし。
      TileDisplayStateにMIXEDを追加したことで既存のexhaustiveなwhen式が軒並みコンパイル
      エラーになる想定だったが、事前に全箇所を洗い出して同時に修正したため1回で通った）。
      Pixel 8 Pro実機・Pixel Watch 2実機の両方へ./gradlew :mobile:installDebugでインストール
      済み。実機での「全デバイス」選択・集約状態表示・一括施錠/解錠動作の確認はユーザー実施予定
      （Sesame実機2台以上が必要）
    関連ID:
      - BL-071

- date: 2026-08-23 19:10
  summary: 施錠/解錠確認画面のボタンを角丸チップの左右分割デザインへ再設計した（BL-070）
  details:
    変更内容: >
      ユーザーから「タップして施錠・解錠を押した後に遷移するtileのボタンが小さくて
      テキストが見切れている。左右分割で左がキャンセル、右が施錠・解錠のボタンにしたい。
      デザインはtopと同じ角丸の四角で」との指摘を受けた。原因は
      wear.action.SesameActionActivityの解錠確認画面が
      androidx.wear.compose.material.Button（既定で円形・小サイズ）に「タップして解錠」という
      長いテキストを詰め込んでいたことだった。SesameActionScreenを、awaitingConfirmation時は
      新設のSesameConfirmationButtons（Row+weight(1f)で左右均等分割）を表示するよう変更し、
      各ボタンは新設のSesameActionChip（Modifier.clip(RoundedCornerShape(12.dp))
      .background().clickable()の自作コンポーネント、Wear Compose Materialの円形Buttonは
      不使用）で実装した。左＝「キャンセル」（タップでonFinishedを呼びコマンド送信せず終了）、
      右＝「施錠」または「解錠」（コマンドに応じて切り替え、タップで送信フローへ進む）。
      デザインをTile側（SesameTileService）と統一するため、中立色定数
      SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB（新規public）へSesameTileServiceが
      個別に持っていた同名のprivate定数を集約し重複を解消した上でキャンセルボタンへ適用、
      施錠/解錠ボタンにはSesameTileContent.backgroundColorArgb/statusTextColorArgb
      （操作後に遷移する状態＝LOCKED/UNLOCKEDに対応する色）を適用し、角丸半径も
      Tileの角丸チップと同じ12dpに揃えた。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（1回で成功）。Pixel 8 Pro実機・Pixel Watch 2実機の
      両方へ./gradlew :mobile:installDebugでインストール済み（両実機のワイヤレスデバッグ
      接続が複数回切断・IP:ポート変更されたため都度再接続した。最終的にはmDNS自動検出接続
      （adb-38101RTJWW48WP-lx9j39._adb-tls-connect._tcp）で反映を確認）。
      ユーザーが実機で確認し「イメージどおりにできてた」と確認済み
    関連ID:
      - BL-070

- date: 2026-08-23 18:20
  summary: Tile追加ピッカーとTile表示アイコンの分離を試みたがAndroid/Wear OSの仕様上不可能と
    判明し、縮小をクリーン再インストールで再検証する方針へ切り替えた（BL-068）
  details:
    変更内容: >
      BL-068（Tile追加ピッカーのアイコン50%縮小）の実機確認で、ユーザーから「Tile表示時の
      アイコンは前のサイズでよかった、Tile追加登録時のアプリ選択画面のアイコンだけ変更したい」
      との指摘を受けた。両者を分離するため、wear/AndroidManifest.xmlの<application>へ
      専用の縮小版アイコン（新規ic_launcher_wear_picker、ic_launcher_wear_picker_foreground）を
      作成し設定したが、ビルド時にmobile側の<application>icon（ic_launcher）と競合し
      マニフェストマージが失敗した（Attribute application@icon ... is also present at
      [:wear] ...）。調査の結果、dynamic feature構成ではbase（mobile）とfeature（wear）が
      最終的に1つの<application>タグへマージされるため、Tile追加時の「アプリ選択」画面と
      Tile表示時のアイコンは同一のSesameTileService.iconリソースしか持てず、
      Android/Wear OSの仕様上、表示先ごとに別サイズへ分離する手段がないと判明した。
      ic_launcher_wear_picker関連の新規ファイルは削除し、wear/AndroidManifest.xmlの
      <application>icon設定もロールバックした。
      この制約をユーザーへ説明し、AskUserQuestionで「縮小を再適用してクリーン再インストール」
      「両方とも元サイズへ戻す」の2択を提示した結果、前者が選択された（前回の確認は
      update-in-place installであり、Wear OSのTileピッカーの表示キャッシュ遅延で
      「アプリ選択」画面だけ古いサイズに見えていた可能性を切り分けるため）。
      ic_launcher_wear_foreground.xmlへ50%縮小（<group>によるscale変換）を再適用し、
      両実機でuninstall後に./gradlew :mobile:installDebugで再インストールした。
    変更ファイル:
      - mobile/src/main/res/drawable/ic_launcher_wear_foreground.xml
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（1回で成功。ic_launcher_wear_picker追加時は
      マニフェストマージエラーで失敗したが、ロールバック後の再試行で成功）。
      Pixel 8 Pro実機・Pixel Watch 2実機の両方でuninstall後にクリーン再インストール済み。
      Tile追加ピッカー・Tile表示・Complicationピッカーそれぞれのアイコンサイズの
      実機確認はユーザー実施予定
    関連ID:
      - BL-068

- date: 2026-08-23 17:30
  summary: Tile追加ピッカーのアイコンをwear専用デザインへ切り替え、50%縮小した（BL-068）
  details:
    変更内容: >
      ユーザーから「Tile追加時のアイコンのサイズがデカいので50%ぐらい小さくても問題なさそう」
      との指摘を受けた。原因は、wear.tile.SesameTileServiceにandroid:icon指定がなく、
      <application>のicon（mobileのic_launcher、リング装飾なしの通常のスマホ向けアイコン）へ
      フォールバックしていたこと。既にSesameComplicationDataSourceService用に用意されていた
      wear専用アイコン（ic_launcher_wear、コンプリケーション風のリング装飾付き）がTile追加
      ピッカーでは使われていなかった。対応として、(1) SesameTileServiceへ
      android:icon="@mipmap/ic_launcher_wear"を明示指定しComplicationピッカーと意匠を統一、
      (2) ic_launcher_wear_foreground.xmlの全パスを<group android:scaleX="0.5"
      android:scaleY="0.5" android:pivotX="54" android:pivotY="54">で包み、中心基準で
      50%縮小した（個々のpath座標自体は変更せず、グループ変換のみで対応）。ic_launcher_wearは
      SesameComplicationDataSourceServiceでも使用しているため、Complicationピッカー側の
      見た目にも同様に反映される（スマホ側のic_launcher・ic_launcher_roundは変更対象外の
      ため影響なし）。
    変更ファイル:
      - wear/src/main/AndroidManifest.xml
      - mobile/src/main/res/drawable/ic_launcher_wear_foreground.xml
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（1回で成功）。Pixel 8 Pro実機・Pixel Watch 2実機の両方へ
      ./gradlew :mobile:installDebugでインストール済み。実機でのTile追加ピッカー・
      Complicationピッカーのアイコンサイズ確認はユーザー実施予定
    関連ID:
      - BL-068

- date: 2026-08-23 16:45
  summary: Tileの各種Activityへタスク分離設定を追加し、デバイス変更後に無関係な画面が表示される
    不具合を修正した（BL-067）
  details:
    変更内容: >
      BL-066（ランチャーアイコン重複解消）の実機確認中、ユーザーから「Tileのデバイス変更を
      行うと『Sesami Wear』という文字が表示される。再度Tileを見ると切り替わっているが、
      施錠/解錠ボタン押下時の状態がおかしい」との報告を受けた。原因調査の結果、
      wear.MainActivity/SesameActionActivity/SesameStatusRefreshActivity/
      TileConfigurationActivity/ComplicationConfigurationActivityのいずれも
      android:taskAffinityを明示指定しておらずデフォルト（アプリ共通）のタスク親和性を
      共有していたことが判明した。BL-066でmobile.MainActivityがウォッチ実行時に
      wear.MainActivityへstartActivity（FLAG_ACTIVITY_NEW_TASKなし）していたため、
      そのタスクがwear.MainActivityをルートとして残留し、後続のTile LaunchAction
      （TileConfigurationActivity等、システムがFLAG_ACTIVITY_NEW_TASKで起動）がタスク
      親和性の一致により同一タスクへ積み重なっていた。TileConfigurationActivityが
      finish()すると背後に残っていたwear.MainActivityが露出して「Sesami Wear」表示となり、
      古いActivityインスタンスが再利用されうる状態（新しいIntent Extraが反映されない
      可能性）が「ボタン押下時の状態がおかしい」の原因と推測される。
      対応として、上記5つのActivity（wear/AndroidManifest.xml）すべてへ
      android:noHistory="true"（フォアグラウンドを外れた時点で即座に破棄しタスクに
      残留させない）とandroid:excludeFromRecents="true"を追加し、mobile.MainActivityの
      wear.MainActivityへのstartActivityへIntent.FLAG_ACTIVITY_NEW_TASKを明示付与した。
    変更ファイル:
      - wear/src/main/AndroidManifest.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（1回で成功）。Pixel 8 Pro実機・Pixel Watch 2実機の両方で
      uninstall後、./gradlew :mobile:installDebugで再インストール済み。実機でのTile
      デバイス変更・施錠/解錠ボタンの状態確認はユーザー実施予定
    関連ID:
      - BL-067
      - BL-066

- date: 2026-08-23 15:20
  summary: mobile/wear双方のランチャーアイコン重複を解消し（BL-066）、Tile外周パディングを
    16fへ再拡大した
  details:
    変更内容: >
      ユーザーから「mobile/wearに2つアプリアイコンが出ている、1つにできないか」「mobileの設定が
      開けない」の2点の報告を受けた。原因調査の結果、mobile（baseモジュール）・wear（feature、
      android.hardware.type.watch限定配信）の両方のMainActivityがそれぞれ独自のLAUNCHER
      intent-filterを持っていたことが判明した。baseモジュールはdist:conditionsの対象外で常に
      ウォッチ側にも同梱されるため、ウォッチ側では常にmobile.MainActivity（タップすると
      FEATURE_WATCH判定で即finish()するガード付き、事実上機能しない）とwear.MainActivity
      （「Sesami Wear」のプレースホルダー表示）の2アイコンが共存していた。またローカルの
      installDebugではdist:conditionsが評価されないため、スマホ側にもwear.MainActivityの
      アイコンが重複表示されていた（BL-044として記録していたローカル制約の懸念が実は恒常的な
      バグだったと判明したため、BL-044は本修正で解消するものとして削除した）。「設定が開けない」
      報告は、スマホ側で誤ってwear.MainActivity（設定機能を持たない）をタップしていたことが
      原因と推測される。対応として、wear/AndroidManifest.xmlのMainActivityからLAUNCHER
      intent-filterを除去（android:exported="false"へ変更、android:icon/roundIcon指定も除去。
      Tile/Complicationが主要導線のため独立起動は不要）、mobile.MainActivityのウォッチ実行時
      ガードをfinish()のみから、explicit Intent（Intent().setClassName(packageName,
      "com.sesamiwear.wear.MainActivity")。mobileはwearへコンパイル時依存できないためクラス名
      文字列を使用）でwear.MainActivityへ委譲する形へ変更した（ActivityNotFoundException時は
      フォールバックでfinish()のみ行う防御コード付き）。
      あわせて、Tileの角丸チップの見切れがまだ残るとの追加指摘を受け、CONTAINER_PADDING_DPを
      13f→16fへさらに拡大した（BL-063継続対応）。
      検証中、Watch側のワイヤレスデバッグのIP:ポートが2回変わり接続が切断されたため、
      ユーザーに都度確認して再接続した。ランチャーアイコン変更の確実な反映のため、
      両実機でuninstallしてから再インストールした。
    変更ファイル:
      - wear/src/main/AndroidManifest.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（1回で成功）。Pixel 8 Pro実機・Pixel Watch 2実機の両方で
      uninstall後、./gradlew :mobile:installDebugで再インストール済み。実機でのアイコン数・
      設定画面の到達性・Tileの見切れ具合の確認はユーザー実施予定
    関連ID:
      - BL-066
      - BL-063

- date: 2026-08-23 13:40
  summary: Tileの外周パディング拡大・テキスト色設定・デバイス名タップでの状態更新機能を追加した
    （BL-063継続対応、4回目）
  details:
    変更内容: >
      直前の左右2分割・角丸チップ表現を実機確認したユーザーから3点の指摘を受けた。
      (1) 角丸チップの一部がまだラウンドベゼルで見切れている→CONTAINER_PADDING_DPを
      12f→13fへ約1割増やした。
      (2) 左側チップ（デバイス名・デバイス変更）のテキスト色が既定の黒のままで、暗い中立背景
      （0xFF424242）に対して視認できない→全Text.Builder呼び出しへ明示的にsetColorを設定した。
      左側2チップは新設の白系定数CHIP_NEUTRAL_TEXT_COLOR_ARGB（0xFFFFFFFF）、右側の状態チップは
      core側ではなくwear.tile.SesameTileContentへ新設したstatusTextColorArgb(state)関数
      （状態色の明度に応じてコントラストを確保：通信中の明るいアンバー背景のみ濃色0xFF212121、
      施錠中・解錠中・未接続・不明の各背景は白0xFFFFFFFF）で個別に設定した。
      (3) デバイス名タップで状態更新をユーザー契機でも実施したい→新規
      wear.action.SesameStatusRefreshActivity（施錠/解錠は行わずPATH_STATUS_REQUESTを
      Fire-and-forgetで送信するのみの軽量Activity、既存のSesameActionActivityとは別クラスとして
      分離。理由: core.api.SesameCommandはSesame API送信コマンドコード（LOCK=82/UNLOCK=83）と
      1対1対応する値であり、状態取得（GET）という異なる概念を追加するとこの enum の意味が
      混乱するため）を追加し、AndroidManifest.xmlへexported="true"で登録（BL-060で判明した
      Wear TilesのLaunchAction制約を踏まえた登録漏れ防止）。SesameTileService.buildLeftColumnの
      デバイス名チップへClickable（LaunchAction）を追加してこのActivityを起動するようにした。
      あわせてSesameTileContentTestへstatusTextColorArgbの単体テスト2件を追加した。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameStatusRefreshActivity.kt（新規）
      - wear/src/main/AndroidManifest.xml
      - wear/src/test/kotlin/com/sesamiwear/wear/tile/SesameTileContentTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（1回で成功、コンパイルエラー・detekt指摘なし）。
      ./gradlew :mobile:installDebugでPixel 8 Pro実機・Pixel Watch 2実機の両方へインストール済み。
      実機での見た目・状態更新動作の確認はユーザー実施予定
    関連ID:
      - BL-063

- date: 2026-08-23 11:15
  summary: Tileレイアウトを左右2分割・角丸チップ表現へ再設計し、状態色を右チップのみへ限定した
    （BL-063継続対応、3回目）
  details:
    変更内容: >
      直前のコミットで実施した「上部/中央/下部の3段構成」を実機確認したユーザーから
      「左レイアウトの文字が画面に収まっていない、ステータス色が全画面に出てレイアウトの
      区切りがわからない、ステータス色は右側だけでいい、各領域を角丸の四角ボタンで表現したい」
      との指摘を受けた。原因は円形画面のセーフエリア（内接正方形、約136dp四方）を考慮せず
      タイルのliteralな端に要素を配置していたためラウンドベゼルでテキストが欠けていたこと、
      および状態色をルートBox全面に敷いていたため左右の領域区切りが視覚的に分からなくなって
      いたことの2点。SesameTileService.buildConfiguredTireを、タイル端からCONTAINER_PADDING_DP
      （12dp）内側へ寄せたRow（左列＋右チップ）構成へ変更。左列（buildLeftColumn、幅76dp固定）
      はデバイス名チップ・デバイス変更チップをDimensionBuilders.weight(1f)で高さ均等分割し、
      間に6dpのSpacerを挟む。右チップ（buildStatusBox）はexpand()で残り全域を占有。各チップは
      共通ヘルパーbuildChipModifiersで角丸背景（ModifiersBuilders.Corner、半径12dp）・内側
      パディング（6dp）を持つ「角丸の四角ボタン」として表現。状態色は右チップの背景にのみ適用し、
      左側2チップは中立色（0xFF424242）で統一した。
      実装中、detektのLongMethod（buildConfiguredTire/buildStatusBoxが60行超過）と
      TooManyFunctions（クラス内関数数が閾値11に到達し失敗、10以下が必須と実測で確認）の
      両方に複数回抵触した。最終的に(a)デバイス名チップ構築をbuildLeftColumnへ統合し
      buildDeviceNameBoxを独立関数として持たない形にする、(b)buildConfigurationLaunchAction
      （2箇所の呼び出し元へインライン化し関数自体を削除）、(c)buildCommandClickableは
      buildStatusBoxと分離したまま維持、の組み合わせで関数数10・各関数60行未満に収めた。
      あわせてTEXT_OVERFLOW_ELLIPSIZE_END（deprecated）をTEXT_OVERFLOW_ELLIPSIZEへ置き換えた。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（試行錯誤の過程でLongMethod/TooManyFunctions/
      型不一致（setModifiersにBuilderを渡していた）のコンパイルエラーが複数回発生したが、
      いずれも修正して最終的に成功）。./gradlew :mobile:installDebugでPixel 8 Pro実機・
      Pixel Watch 2実機の両方へインストール済み。実機での見た目確認はユーザー実施予定
    関連ID:
      - BL-063

- date: 2026-08-23 09:30
  summary: Tileレイアウトを上部/中央/下部の3領域へ再構成（BL-063継続対応）、自動更新問題の
    調査用ログをmobile側にも追加（BL-064継続対応）
  details:
    変更内容: >
      ユーザーから「Tileは中心にテキストが集まっている、左右や上下でレイアウトに意味を
      持たせてほしい」と指摘があった（BL-063の1回目対応後）。原因は、状態表示を担う
      SesameTileService.buildStatusBoxの返すBoxに幅・高さの明示指定がなく、内容サイズにしか
      広がらないままタイル中央に小さくまとまって表示されていたこと。buildConfiguredTileを
      Box（タイル全面、DimensionBuilders.expand()、背景色もここへ移動）→Column（同じく
      expand）→(1)buildDeviceNameBox（上部・中央寄せ、デバイス名）、
      (2)buildStatusBox（expand()で残り全域を占有、中央寄せ、状態アイコン・状態文言・
      操作ラベル、施錠/解錠のクリック領域）、(3)buildChangeDeviceBox（下部・右寄せ、
      「デバイスを変更 ›」）の3段構成へ再設計した。detektのLongMethod（buildConfiguredTireが
      71行で60行制限に抵触）を解消するためbuildDeviceNameBoxを独立関数へ切り出し、代わりに
      1行の呼び出しのみだったbuildUnconfiguredTileをonTileRequestの呼び出し元へインライン化し
      関数数を維持してTooManyFunctionsの再超過を回避した。
      あわせてBL-064（コマンド成功後にTileが自動更新されない問題）の調査用に、
      mobile.messaging.SesameMessageListenerServiceのhandleCommandRequest/
      handleStatusRequestへLog.d呼び出しを追加した（コマンド受信・デバウンス判定・
      API実行結果・DataItem同期・結果送信の各段階、TAG="SesameMessageListener"）。
      wear側のSesameResultListenerService/SesameTileServiceへのログ追加は前回セッションで
      実施済みのため今回は対象外。ユーザーが当面Watch実機を操作できないとのことのため、
      レイアウト改善を優先して品質ゲート実行・実機インストールまで完了させ、ログの確認は
      ユーザーの都合がつき次第行う
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（初回はSesameTileService.buildConfiguredTireの
      LongMethod違反で失敗、関数分割で解消して再実行し成功。mobile側もTAG未定義でコンパイル
      エラーとなり修正後成功）。./gradlew :mobile:installDebugでPixel 8 Pro実機・
      Pixel Watch 2実機の両方へインストール済み。レイアウト・自動更新とも実機での見た目/
      挙動確認はユーザー実施予定
    関連ID:
      - BL-063
      - BL-064

- date: 2026-08-22 23:42
  summary: Tile連打による施錠/解錠コマンド重複送信を防ぐデバウンス処理を追加した（BL-062）
  details:
    変更内容: >
      ユーザー報告（Tileタップ後にwearのハプティクスが連続して鳴り続ける）を受け実機ログを
      調査した結果、SesameActionActivityが同一時間帯に5つの別々のタスクとして起動されている
      ことを確認した（ユーザーの連打が原因と判明）。PLAN.mdのUX要件「通信中は明確な処理中表示
      ＋ボタン無効化で二重送信防止」に対応する仕組み（TileDisplayState.IN_PROGRESS）は
      BL-007時点で用意されていたが、実際に「送信中」を検知してこの状態にする実装が
      入っておらず（isCommandInProgressが常にfalse固定）、連打を防げていなかった。
      mobile.messaging.CommandDebouncer（時刻取得を注入可能にしたAndroid非依存クラス）を
      新規実装し、SesameMessageListenerService.handleCommandRequestで同一デバイスuuidへの
      2秒以内の重複コマンドを無視するようにした（companion objectでインスタンスを保持し
      Serviceの再生成をまたいで機能する）。単体テスト4件（初回は常に処理、window内は拒否、
      window後は再度処理、キーごとに独立）を追加した。
      Tile側のisCommandInProgressを実際に機能させる根本対応（送信中状態の管理、次回のTile
      表示時に反映される非同期な性質を踏まえたUI設計）は実装複雑度と緊急性のバランスから
      今回は見送り、mobile側でのデバウンスのみで実害（重複コマンド実行・重複振動）を防ぐ
      対症療法とした。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/CommandDebouncer.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/messaging/CommandDebouncerTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
        （BL-061コミットに含めて実装済み、本タスクでは新規ファイルのみ追加）
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（CommandDebouncerの単体テスト4件を含め全成功）。
      実機での連打時の重複防止確認はユーザー実施予定。
    関連ID:
      - BL-062

- date: 2026-08-22 23:40
  summary: Tile/Complication表示時にSesame API状態を自動取得する機能を実装した（BL-061）
  details:
    変更内容: >
      Tile/Complicationの初期状態が常に「状態不明」（UNKNOWN）になり、UNKNOWN状態では
      タップ不可のためコマンドを一切送信できないデッドロックが判明したことを受け
      （ユーザー合意）、Tile/Complication表示時にSesame APIの状態を自動取得する機能を実装した。
      core.SesameWearProtocolへPATH_STATUS_REQUEST（状態取得リクエスト用の新メッセージパス、
      Fire-and-forget）を追加し、wear.messaging.SesameCommandSenderにrequestStatusを追加した。
      SesameTileService.onTileRequest/SesameComplicationDataSourceService.onComplicationRequest
      は、Wear Tiles/Complications APIのレスポンスタイムアウト制約を避けるため既存の
      DataItemスナップショットで即座に応答しつつ、mobile側へ状態取得リクエストを送信するよう
      変更した。mobile側のSesameMessageListenerServiceはPATH_STATUS_REQUESTを受信すると
      SesameApiClient.getStatus()でSesame APIのGETを呼び、成功時にSesameStatusSyncerで
      DataItemへ同期する（結果はwear側へ返送しないFire-and-forget）。wear側に新規
      SesameStatusListenerService（WearableListenerService.onDataChanged）を追加し、
      STATUS_DATA_ITEM_PATH配下のDataItem変更を検知したらTileService.getUpdater()と
      ComplicationDataSourceUpdateRequester.requestUpdateAll()でTile/Complicationの再描画を
      リクエストする仕組みを追加した。これによりBL-015の既知の制約（状態同期がコマンド送信
      成功時のみ）も解消した。
      Pixel Watch 2実機で、Tile Configuration完了後にTileから施錠/解錠操作ができることを
      確認した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameWearProtocolTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameCommandSender.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/messaging/SesameCommandSenderTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameStatusListenerService.kt
      - wear/src/main/AndroidManifest.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:bundleDebug && ./gradlew :mobile:installDebug（実機Pixel Watch 2での
      施錠/解錠操作確認込み）
    検証結果: 成功 - すべてのコマンドが成功。実機でTileから施錠/解錠操作ができることを確認した。
    関連ID:
      - BL-061

- date: 2026-08-22 23:11
  summary: Tile/Complication関連Activityのexported属性不足を修正した重大バグ修正（BL-060）
  details:
    変更内容: >
      ユーザー報告（Tileの「タップして設定」表示をタップしても何も起きない）を受け実機ログ
      （adb logcat）を確認した結果、ProtoTilesPlTileViewInstance:
      "Activity constraints not met. Not launching LaunchAction Activity"という警告を
      発見した。Wear Tiles APIのLaunchActionで起動するActivityはandroid:exported="true"が
      必須という制約があることを公式情報で確認し、wear/AndroidManifest.xmlの
      SesameActionActivity（BL-014で実装した施錠/解錠実行画面、実機未検証のまま長期間
      exported="false"だった）・TileConfigurationActivity（BL-052）・
      ComplicationConfigurationActivity（BL-054）の3つすべてがexported="false"のままだった
      ことが原因と判明した。3つのActivityをexported="true"へ修正した。
      これによりTile Configuration機能だけでなく、基本の施錠/解錠操作自体も
      実機では動作していなかった可能性が高い（BL-011の人手検証が未実施だったため
      これまで発覚していなかった）。
      Pixel Watch 2実機で修正後、Tileの「タップして設定」からTileConfigurationActivityが
      正しく起動し、mobile側で登録済みのデバイス一覧が表示され選択・割り当てできることを
      確認した。
    変更ファイル:
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:installDebug（実機Pixel Watch 2でのTile Configuration動作確認込み）
    検証結果: 成功 - すべてのコマンドが成功。実機でTile Configuration Activityの起動と
      デバイス選択を確認した。
    関連ID:
      - BL-060

- date: 2026-08-22 22:55
  summary: 資格情報設定画面の案内文言をbiz.candyhouse.co前提に統一し情報量を削減した（BL-059）
  details:
    変更内容: >
      ユーザーからuuid・apikeyもbiz.candyhouse.coから取得する値であるとの報告を受け
      （Sesameアプリの「鍵をシェア」QRコードは使わない運用）、CredentialsSettingsScreenの
      案内文言を修正した。あわせて、旧SetupInstructions（3行の手順テキスト）と各入力欄の
      supportingTextにより初期表示の情報量が多いという指摘を受け、詳細説明をヘルプボタン
      （TextButton）タップで開くAlertDialog（HelpDialog、「uuid・apikey・secretKeyは
      biz.candyhouse.coで確認できます。secretKeyは16進数32文字です。」の1つにまとめた
      文言）へ集約し、初期表示は入力欄（表示名/uuid/apikey/secretKey）をラベルのみの
      シンプルな見た目にした。material-icons系ライブラリの依存がプロジェクトになかったため
      アイコンボタンではなくテキストボタンで実装した。README.md/
      docs/store/STORE_LISTING.md/docs/store/PRIVACY_POLICY.mdの取得元説明も
      biz.candyhouse.coに統一した。
      追加のユーザー依頼により、HelpDialogへSESAME Biz開発者ページ
      （https://biz.candyhouse.co/biz/developer）へIntent.ACTION_VIEWで遷移するTextButtonを
      追加した。さらに「追加」ボタンがsecretKey入力欄と近すぎる」という指摘を受け、
      CredentialsFormをColumn(verticalArrangement = Arrangement.spacedBy(8.dp))で統一し、
      ボタン群の前に追加スペーサーを挟み、ボタンをModifier.fillMaxWidth()で横幅いっぱいの
      目立つ形状に変更した。
      Pixel 8 Pro実機でシンプル化後の画面表示、ヘルプダイアログの表示、リンクボタンタップ時の
      ブラウザ遷移（biz.candyhouse.co/biz/developerへの正常な遷移を確認）、レイアウト調整後の
      余白をすべて確認した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - README.md
      - docs/store/STORE_LISTING.md
      - docs/store/PRIVACY_POLICY.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      npx markdownlint-cli2 "**/*.md" &&
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:installDebug（実機Pixel 8 Proでの画面・ダイアログ・リンク遷移・
      レイアウトの目視確認込み）
    検証結果: 成功 - すべてのコマンドが成功。実機スクリーンショットで初期表示の簡素化、
      ヘルプダイアログの表示、開発者ページへのブラウザ遷移、レイアウト調整をすべて確認した。
    関連ID:
      - BL-059

- date: 2026-08-22 22:30
  summary: secretKeyのデコード方式をBase64からhexへ修正した重大バグ修正（BL-058）
  details:
    変更内容: >
      ユーザー報告（secretKeyに32文字の値を入力しても「追加」ボタンが無効のまま）を受けて
      調査した結果、core.SesameCredentialsのsecretKeyデコード処理がBase64を前提として
      いたことが根本原因と判明した。CANDY HOUSE公式ドキュメント
      （API_document/SesameOS3/webapi.mdのコード例）ではsecretKeyは16進数文字列
      （32文字=16バイト）であり、Base64ではない。secretKeyBase64フィールドを
      secretKeyHexへリネームし、デコードをjava.util.HexFormat.of().parseHex()へ変更した
      （core.crypto.AesCmac/core.api.SesameCommandSigner自体は鍵の16バイト長のみを
      要求するロジックでエンコーディング形式に依存しないため変更不要だった）。
      mobile.CredentialsInputValidator、mobile.CredentialsSettingsScreen（UI文言・
      supportingText・SetupInstructionsの手順説明）、関連する単体テスト3件
      （SesameCredentialsTest/SesameCredentialsStoreTest/CredentialsInputValidatorTest）
      をすべてhex形式に合わせて修正した。SetupInstructionsは、ユーザーの実際の運用
      （secretKeyはQRコードではなくbiz.candyhouse.coのデバイス情報から生成）に合わせて
      「①QRコードでuuid確認 ②biz.candyhouse.coでapikey・secretKey発行」という手順に
      更新した。core.api.SesameApiClientのBase64使用箇所（historyタグのエンコード）は
      secretKeyとは無関係のため変更していない。
      未確認事項として、apikey/secretKeyの正確な取得画面（biz.candyhouse.co内の
      具体的な遷移）は実機での施錠/解錠疎通確認（BL-010）で最終検証する旨をDESIGN.mdに
      記録した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentials.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCredentialsTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCredentialsStoreTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputValidator.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputValidatorTest.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（大文字/小文字hex、不正hex、鍵長不一致の単体テストを
      含め全成功）。実機での16進数secretKey入力確認はユーザー実施予定。
    関連ID:
      - BL-058

- date: 2026-08-22 21:59
  summary: apikey発行先URLの記載をpartners.candyhouse.coからbiz.candyhouse.coへ修正した（BL-057）
  details:
    変更内容: >
      QRコード自動入力機能の実現可能性を調査する過程で、CANDY HOUSE公式ドキュメント
      （github.com/CANDY-HOUSE/API_document/blob/master/SesameOS3/webapi.md）に
      「APIキーはbiz.candyhouse.co（SESAME Biz 開発者ページ）で取得する」と明記されている
      ことを確認した。これはREADME.md/docs/store/STORE_LISTING.md/
      docs/store/PRIVACY_POLICY.md/mobile側UI（CredentialsSettingsScreen）に記載していた
      partners.candyhouse.coと異なっていたため、該当箇所をbiz.candyhouse.coへ修正した。
      PLAN.mdは原初依頼内容のため変更せず、DESIGN.md（REQ-032）へ正しい情報と
      未確認事項（biz.candyhouse.co自体のページ内容は動的サイトのためWebFetchで確認できて
      いない）を記録した。
      あわせて、前回（BL-056、SetupInstructions追加）の記録漏れだったDESIGN.mdへの反映
      （REQ-031）も本イテレーションで追記した。
      QRコード読み取りによる自動入力機能自体は、実機でSesame 5のQRコードをスキャンし
      160バイトのデータを確認したが、SESAME 3/4向けの既知構造（sesame-qr-reader、99バイト）
      とは一致せず、Sesame 5固有のバイナリ構造は非公開で不明なため見送った
      （非公式フォーマットへの依存リスクが高いとユーザーと合意）。
    変更ファイル:
      - README.md
      - docs/store/STORE_LISTING.md
      - docs/store/PRIVACY_POLICY.md
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      npx markdownlint-cli2 "**/*.md" &&
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - すべてのコマンドが成功
    関連ID:
      - BL-057

- date: 2026-08-22 20:17
  summary: mobile資格情報設定画面に取得手順の説明を追加した（BL-056）
  details:
    変更内容: >
      ユーザー報告により、CredentialsSettingsScreenの入力欄（uuid/apikey/secretKey）に
      説明が一切なく、初めて使うユーザーがどこから値を取得すればよいか分からない問題を
      改善した。画面冒頭にSetupInstructions（PLAN.mdのAPI仕様記載に基づく3ステップの
      手順説明: ①Sesameアプリの「鍵をシェア」でuuid/secretKey確認 ②partners.candyhouse.co
      でapikey発行 ③フォーム入力）を追加し、各OutlinedTextFieldにsupportingTextで
      個別の取得元説明を追加した。あわせてDeviceListが0件の場合に
      「まだSesameが登録されていません。下のフォームから追加してください。」という
      ガイダンスを表示するよう変更した。Pixel 8 Pro実機でinstallDebug後にスクリーンショットで
      表示を確認した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:installDebug（実機Pixel 8 Proでのスクリーンショット目視確認込み）
    検証結果: 成功 - 全品質ゲートおよびinstallDebugがBUILD SUCCESSFUL。実機スクリーンショットで
      手順説明・各入力欄のヘルパーテキスト・未登録時ガイダンスの表示を確認した。
    関連ID:
      - BL-056

- date: 2026-08-22 17:44
  summary: wear側Complicationの複数デバイス対応を実装した（BL-054）
  details:
    変更内容: >
      SesameComplicationDataSourceServiceをcomplicationInstanceId（ComplicationRequestから
      取得できるインスタンス固有のInt ID）ごとに対象デバイスを参照する「複数Complicationインスタンス
      方式」へ変更した（BL-053のSesameTileServiceと同型のパターン）。対象デバイスは新規追加した
      ComplicationDeviceAssignmentStore（SharedPreferencesベース、instanceIdをキーとしたuuid永続化）
      で管理する。未設定のcomplicationInstanceIdの場合は「タップして設定」を表示し、
      tapAction（PendingIntent）でComplicationConfigurationActivityを起動する。選択後は
      ComplicationDataSourceUpdateRequesterで対象Complicationの再描画を要求する。
      デバイス選択UI（BL-052でTile用に実装したScalingLazyColumn+Chipのリスト）を
      wear.ui.DeviceSelectionScreenへ切り出し、TileConfigurationActivity/
      ComplicationConfigurationActivityの双方から共通利用する形にリファクタリングした。
      これによりBL-053で暫定対応（デバイス一覧の先頭のみ表示）していたComplicationの制約を解消した。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/ComplicationDeviceAssignmentStore.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/ComplicationConfigurationActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/ui/DeviceSelectionScreen.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/TileConfigurationActivity.kt
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:bundleDebug
    検証結果: 成功 - BUILD SUCCESSFUL。これでBL-046〜054（複数Sesameデバイス対応）の
      自動実行可能なタスクがすべて完了した。実機での最終確認はBL-055（人手検証）。
    関連ID:
      - BL-054

- date: 2026-08-22 17:40
  summary: wear側Tile/コマンド送信をtileId・deviceUuid対応へ変更した（BL-053）
  details:
    変更内容: >
      SesameTileService.onTileRequestでrequestParams.tileIdを取得し、
      TileDeviceAssignmentStore（BL-052）でtileIdに割り当てられたデバイスuuidを参照するよう
      変更した。未割り当てのtileIdの場合は「タップして設定」の誘導表示（タップで
      TileConfigurationActivityを起動、tileIdを文字列Extraとして渡す）を持つTileを返し、
      割り当て済みの場合はSesameStatusSnapshotReader.readLatest(context, uuid)（BL-050で
      追加したデバイス別パス対応）で状態を取得し、クリックアクションのIntent extraへ
      deviceUuidも含めるよう変更した。SesameCommandSender.requestLock/requestUnlockへ
      deviceUuidパラメータを追加し、SesameWearProtocol.encodeDeviceUuidでペイロード化して
      送信するよう変更した（BL-050時点で常にFAILUREになっていた一時的不整合を解消）。
      SesameActionActivityはIntentからdeviceUuidも受け取り送信時に渡すよう変更した。
      SesameActionCommandParserへEXTRA_DEVICE_UUID定数を追加した。
      **副作用の対応**: SesameStatusSnapshotReaderのシグネチャ変更によりビルド不能になった
      SesameComplicationDataSourceServiceは、BL-054（Complicationの複数デバイス対応）までの
      暫定措置として、SesameDeviceListReaderで同期されたデバイス一覧の先頭のみを表示する形に
      最小限追従させた。
      既存テスト（SesameCommandSenderTest）を新シグネチャに合わせて更新した。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/TileConfigurationActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameCommandSender.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameStatusSnapshotReader.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionCommandParser.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/messaging/SesameCommandSenderTest.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:bundleDebug
    検証結果: 成功 - BUILD SUCCESSFUL
    関連ID:
      - BL-053

- date: 2026-08-22 17:35
  summary: wear側Tile Configuration Activityとデバイス一覧同期の仕組みを実装した（BL-052）
  details:
    変更内容: >
      BL-051の技術調査結果（「Tile自体がタップで設定画面へ誘導する」パターン）に基づき、
      wear.tile.TileConfigurationActivity（Intent extraでtileIdを受け取り、選択したデバイスの
      uuidをTileDeviceAssignmentStoreへ永続化して終了する画面）を実装した。
      実装着手時に、wear側は資格情報を持たない設計方針のため、Configuration Activityが
      選択肢として表示するデバイス一覧をmobile側から同期する仕組みが前提として必要だと判明し、
      あわせて実装した: core.SesameDeviceSummary（uuid/displayNameのみの機密情報を含まないDTO）、
      core.SesameWearProtocol.DEVICE_LIST_DATA_ITEM_PATH/KEY_DEVICE_LIST_JSON、
      mobile.SesameDeviceListSyncer（DataClient経由でデバイス一覧を同期、CredentialsSettingsScreen
      の保存・削除時に呼び出す）、wear.messaging.SesameDeviceListReader（DataClientから
      デバイス一覧を読み取る、既存のSesameStatusSnapshotReaderと同型のパターン）。
      wear.tile.TileDeviceAssignmentStoreはtileId（Int）をキーとしたuuidの永続化を、
      機密情報を扱わないため通常のSharedPreferencesで行う。TileConfigurationActivityは
      androidx.wear.compose.foundation.lazy.ScalingLazyColumn/androidx.wear.compose.material.Chip
      でデバイス一覧を表示し、選択時にTileService.getUpdater().requestUpdate()で対象Tileの
      再描画を要求する。mobile/wear双方のbuild.gradle.ktsにkotlinx-serialization-json依存が
      不足していたため追加した（coreモジュールの依存はimplementationのため推移的に伝播しない）。
      実際にこのActivityをtileId付きで起動する導線（未設定Tileからのタップ誘導）はBL-053で実装する。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameDeviceSummary.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - mobile/build.gradle.kts
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameDeviceListSyncer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - wear/build.gradle.kts
      - wear/src/main/AndroidManifest.xml
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameDeviceListReader.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/TileDeviceAssignmentStore.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/TileConfigurationActivity.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:bundleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（Wear Compose API/Tiles APIの解決も含め成功、
      AABパッケージングも成功）
    関連ID:
      - BL-052

- date: 2026-08-22 17:26
  summary: mobile側メッセージ受信・コマンド実行・状態同期をdeviceId対応へ変更した（BL-050）
  details:
    変更内容: >
      core.SesameWearProtocolへstatusDataItemPath(uuid)を追加し、STATUS_DATA_ITEM_PATHを
      プレフィックスとしてデバイスごとに一意なDataItemパスを生成できるようにした
      （複数デバイスの状態が同一DataItemで上書き衝突しないため）。
      mobile.SesameStatusSyncer.syncLockedへuuidパラメータを追加しstatusDataItemPath(uuid)
      を使うよう変更した。mobile.SesameMessageListenerService.onMessageReceivedで、
      messageEvent.dataをSesameWearProtocol.decodeDeviceUuidでデコードして対象デバイスの
      uuidを取得し、credentialsStore.loadAll().find { it.uuid == deviceUuid }で該当する
      資格情報を選択してSesameCommandHandlerを構築するよう変更した（BL-047時点の暫定実装
      firstOrNull()を置き換えた）。syncLockedStateFromPathにもdeviceUuidを渡すよう変更した。
      **未確認事項/既知の一時的不整合**: wear側のSesameCommandSender（BL-053で対応予定）は
      現状ByteArray(0)を送信し続けているため、この時点ではmessageEvent.dataは常に空となり
      decodeDeviceUuidは空文字列を返す。結果、credentialsStore内にuuid=""のデバイスが
      存在しない限りコマンドは常にFAILUREになる。BL-053完了までの間、機能的な結合動作は
      一時的に崩れるが、ビルド・単体テストは成功する状態を維持している（未リリースのアプリの
      ため許容と判断）。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameWearProtocolTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameStatusSyncer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL
    関連ID:
      - BL-050

- date: 2026-08-22 17:24
  summary: mobile資格情報設定画面を複数デバイス管理UIへ変更した（BL-049）
  details:
    変更内容: >
      CredentialsSettingsScreenを単一フォームから、複数デバイスの一覧（DeviceList、
      表示名・uuid表示＋編集/削除ボタン）と追加/編集フォーム（CredentialsForm、
      表示名/uuid/apikey/secretKey入力）を組み合わせたUIへ全面的に書き換えた。
      uuidをキーとしたupsert（同一uuidなら上書き、新規uuidなら追加）で
      credentialsStore.saveAll()へ反映する。編集状態はCredentialsFormState
      （remember管理のプライベートクラス）へ切り出した。detektのLongMethod
      （閾値60）に抵触したため、DeviceList/CredentialsFormをサブComposableへ
      分割して解消した。CredentialsInputValidator自体はロジック変更なし
      （既存テストに影響なし）。実機（Pixel Watch 2）へのinstallDebugは成功したが、
      Pixel 8 Pro側はadbデーモンが不安定になり接続確認できず、実機目視確認は
      未実施（コードレベルの品質ゲートは全て成功）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL
    関連ID:
      - BL-049

- date: 2026-08-22 17:19
  summary: SesameWearProtocolのペイロードに対象デバイスuuidを載せるエンコード/デコードを追加した（BL-048）
  details:
    変更内容: >
      core.SesameWearProtocolへencodeDeviceUuid(uuid)/decodeDeviceUuid(payload)を追加した。
      施錠/解錠コマンドのメッセージペイロードへ対象デバイスのuuid（SesameCredentials.uuidと
      対応）を載せるための単純なUTF-8バイト列エンコード/デコードで、JSON等の複雑な形式は
      使わない。core.SesameMessageSender.sendは元々payload: ByteArrayを受け取る設計だった
      ため、インターフェース自体の変更は不要だった。wear側のSesameCommandSender（現状
      ByteArray(0)を送信）・mobile側の受信処理でこのエンコード/デコードを実際に使う変更は
      BL-050/BL-053のスコープとし、本タスクではcore層のロジック追加とテストのみに留めた。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameWearProtocolTest.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（round-trip・空ペイロードの単体テストを含め全成功）
    関連ID:
      - BL-048

- date: 2026-08-22 17:16
  summary: SesameCredentialsStoreを複数デバイスのリスト保存対応へ変更した（BL-047）
  details:
    変更内容: >
      core.SesameCredentialsStoreの単一資格情報の保存・読み出し（save/load）を、
      List<SesameCredentials>全体をkotlinx.serializationでJSON化し単一キー
      （credentials_list）で保存するsaveAll/loadAllへ置き換えた。loadAllはJSONとして
      不正な値が保存されていた場合に例外を投げずemptyList()を返す（BL-026の防御的
      プログラミング方針に合わせた）。特定デバイスの削除用にremove(uuid)も追加した。
      未リリースのアプリのため、旧フォーマット（uuid/api_key/secret_key_base64/display_nameの
      個別キー保存）からのマイグレーションは行わない方針とした（実運用データが存在しないため）。
      呼び出し元（mobile.SesameMessageListenerService.createHandler、
      mobile.CredentialsSettingsScreen）は、複数デバイス対応の本実装（BL-049/BL-050）までの
      暫定措置として、loadAll().firstOrNull()で先頭の1件のみを扱う形に最小限追従させ、
      ビルド可能な状態を維持した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentialsStore.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCredentialsStoreTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（複数件保存・不正JSON・remove等の単体テストを含め全成功）
    関連ID:
      - BL-047

- date: 2026-08-22 17:13
  summary: SesameCredentialsを複数デバイス対応データモデルへ変更した（BL-046）
  details:
    変更内容: >
      複数台（3〜5台想定）のSesame 5を操作したいというユーザー要件（DESIGN.md「複数Sesameデバイス
      対応方針」参照）を受け、core.SesameCredentialsへdisplayName（表示名、デフォルト空文字）を
      追加した。識別子は別途deviceIdを持たせず、Sesame API上で既に一意なuuidをそのまま複数
      デバイス管理のキーとして用いる設計とした（BACKLOG登録時の想定から簡素化、概念の重複を
      避けるため）。displayNameは既存フィールド（uuid/apiKey/secretKeyBase64）の後にデフォルト値
      付きで追加したため、既存の呼び出し箇所（SesameCredentialsStore/CredentialsSettingsScreen/
      CredentialsInputValidator/既存テスト、いずれも名前付き引数で呼んでいた）は変更不要だった。
      後続タスク（BL-047）でのkotlinx.serializationによるリスト保存に備え@Serializableを付与した。
      SesameCredentialsStore.save/loadもdisplayNameのput/get（未設定時は空文字）に対応させた
      （複数デバイスのリスト保存自体はBL-047で行う、現時点では単一保存のまま）。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentials.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentialsStore.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCredentialsTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCredentialsStoreTest.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: 成功 - BUILD SUCCESSFUL（新規追加した単体テスト3件を含め全テスト成功）
    関連ID:
      - BL-046

- date: 2026-08-22 17:02
  summary: mobile側CredentialsSettingsScreenがステータスバーと重なる表示崩れを修正した（BL-045）
  details:
    変更内容: >
      ユーザー報告により、mobile側のCredentialsSettingsScreen（資格情報設定画面）が画面トップの
      ステータスバーと重なって表示される問題を確認した。原因は
      CredentialsSettingsScreen.ktのColumn(modifier = Modifier.padding(16.dp))に
      WindowInsets対応が設定されておらず、Edge-to-edge表示によりコンテンツがステータスバーの
      下（同じ座標）に描画されていたため。ColumnのmodifierへsafeDrawingPadding()を追加し
      （.safeDrawingPadding().padding(16.dp)の順で適用、ステータスバー・ナビゲーションバー・
      ディスプレイカットアウトを含む安全領域分のpaddingを内側の16dpパディングより外側に確保）、
      重なりを解消した。Pixel 8 Pro実機でinstallDebug後にMainActivityを起動し、
      スクリーンショットで時刻・通知アイコン等のステータスバーとコンテンツ
      （「Sesame API設定」テキスト・uuid入力欄）が重ならず表示されることを目視確認した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:installDebug（実機Pixel 8 Proでのスクリーンショット目視確認込み）
    検証結果: 成功 - 全品質ゲートおよびinstallDebugがBUILD SUCCESSFUL。実機スクリーンショットで
      ステータスバーとの重なり解消を確認した。
    関連ID:
      - BL-045

- date: 2026-08-22 17:01
  summary: dist:titleの重複リソース参照によるAABパッケージング失敗を修正した（BL-043）
  details:
    変更内容: >
      ユーザー依頼でmobile/wearアプリの実機再インストール（アンインストール→
      ./gradlew :mobile:installDebug）を行ったところ、:mobile:packageDebugBundleが
      「Title for module 'wear' is missing in the base resource table」で失敗することが判明した。
      原因はwear/AndroidManifest.xmlのdist:module dist:title="@string/app_name"が、
      mobile（base）側とwear（feature）側の双方に同名で存在するapp_name文字列リソースを
      参照しており、bundletoolがbaseリソーステーブル内でwearモジュール固有のタイトルを
      一意に解決できなかったため（BL-036実施時点のEXECUTE.md記録では:mobile:bundleDebug/
      bundleReleaseの成功を確認済みだったが、その後のいずれかの変更で顕在化したリグレッション。
      発生源のコミットは特定していない、未確認）。切り分けのため
      (1)wear側に一意な別名リソースを追加、(2)mobile側にも同名で追加、
      (3)mobile側にのみ別名リソース（wear_module_title）を追加、の3パターンを試し、
      (3)でのみ解決することを確認した。wear固有のwear_module_titleをmobile側の
      strings.xmlにのみ追加し、dist:titleの参照先をこちらへ変更した。wear側のapp_name
      （ランチャーラベル等の表示名）はdist:title用途と分離し、従来どおり維持した。
      検証中に副次的な問題として、Pixel 8 Pro（スマホ実機）へのinstallDebugでも
      com.sesamiwear.wear.MainActivityがインストールされる現象を確認した。BL-039の
      dist:device-feature条件はGoogle Play正式配信でのみ評価されローカルのinstallDebugでは
      評価されない制約による可能性があるが未確認のため、BL-044として人手検証へ切り出した。
    変更ファイル:
      - mobile/src/main/res/values/strings.xml
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/BACKLOG.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug &&
      ./gradlew :mobile:bundleDebug :mobile:bundleRelease && ./gradlew :mobile:installDebug
    検証結果: 成功 - 全品質ゲートおよびbundleDebug/bundleRelease/installDebugがBUILD SUCCESSFUL。
      installDebugで接続中のPixel 8 Pro実機・Pixel Watch 2実機（2経路）へのインストールも確認した。
    関連ID:
      - BL-043

- date: 2026-08-22 16:24
  summary: wearランチャーアイコンのリングをAdaptive Iconセーフゾーン内に収まる寸法へ縮小した（BL-041）
  details:
    変更内容: >
      ic_launcher_wear_foreground.xmlのコンプリケーション風リング（中心(54,54)、半径40、
      ストローク幅5）は外周が中心から42.5dpに達しており、Adaptive Iconのセーフゾーン
      （108dp viewport中心から半径33dp・直径66dp、Google公式ガイドライン）を大きく超えていたため、
      丸型等のランチャーマスクでリングが欠けて表示される状態だった。半径を40から30、
      ストローク幅を5から4へ縮小し（外周が中心から32dpとなりセーフゾーン内に収まる）、
      pathDataの始点・終点座標（M94,54/A40,40→M84,54/A30,30 等）もあわせて修正した。
      trimPathStart/trimPathEnd/trimPathOffsetはパス長に対する相対値のため変更不要。
    変更ファイル:
      - mobile/src/main/res/drawable/ic_launcher_wear_foreground.xml
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: >-
      成功 - BUILD SUCCESSFUL（132 actionable tasks: 13 executed, 119 up-to-date）
    関連ID:
      - BL-041

- date: 2026-08-22 16:12
  summary: mobile/MainActivityへWear OS実機検出時のfinish()ガードを追加した（BL-040）
  details:
    変更内容: >
      BL-039の対応後もmobileはbaseモジュールであるため常時ウォッチ側にもインストールされ、
      mobile/.MainActivity（スマホ向け資格情報設定画面）のランチャーアイコンがウォッチにも
      表示され続ける制約が残っていた。mobile/MainActivity.ktのonCreate()冒頭で
      packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)を判定し、
      該当する場合はsetContent()を呼ばずfinish()して即座に終了するガードを追加した。
      ウォッチ実機でアイコンをタップしても資格情報設定画面（フォーム等、丸型小画面向けに
      最適化されていないCompose UI）が表示されなくなる。既存のSesameCommandHandler等
      同様の理由でActivity層自体の単体テストは他のActivity（wear.MainActivity/
      SesameActionActivity等）にも存在しないため、本変更も単体テストは追加していない
      （Android非依存ロジックのみユニットテスト対象という既存方針に合わせた）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: >-
      成功 - BUILD SUCCESSFUL（132 actionable tasks: 25 executed, 107 up-to-date）
    関連ID:
      - BL-040

- date: 2026-08-22 15:56
  summary: wearモジュールのdist:deliveryへdevice-feature条件を追加しスマホへの配信を止めた（BL-039）
  details:
    変更内容: >
      実機検証（スマホ+Pixel Watch）で:mobile:installDebugを実行したところ、両デバイスに
      com.sesamiwear.mobile（mobile/.MainActivityとwear側com.sesamiwear.wear.MainActivityの
      両方のランチャーアイコン）が入る現象を確認した。原因はwear/src/main/AndroidManifest.xmlの
      dist:deliveryがdist:install-timeのみでデバイス種別を絞る条件を持たず、wearモジュールが
      常に全デバイスへ配信される設定になっていたため。dist:install-time配下へ
      dist:conditions/dist:device-feature(dist:name="android.hardware.type.watch")を追加し、
      Play Feature Delivery公式ドキュメントの条件付きinstall-time配信の記法に合わせ、
      wearモジュールがwatchハードウェア機能を持つデバイスにのみ配信されるようにした。
      dist:fusing include=trueは維持（minSdk26のため実質未使用の古い端末向けuniversal APK
      フォールバック専用設定であり、今回の問題とは無関係なため変更不要と判断）。
    変更ファイル:
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/BACKLOG.md
    検証コマンド: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug
    検証結果: >-
      成功 - BUILD SUCCESSFUL（132 actionable tasks: 22 executed, 110 up-to-date）
    関連ID:
      - BL-039

- date: 2026-08-22 12:40
  summary: wearをdynamic featureへ変更しmobileとapplicationIdを統合（BL-036）
  details:
    変更内容: >
      mobile/wearが別々のapplicationIdを持つ独立2アプリ構成（BL-031で記録した制約）を見直し、
      wearをcom.android.applicationからcom.android.dynamic-featureへ変更してmobileへ統合した。
      wear/build.gradle.ktsからapplicationId/signingConfigs/versionCode・versionName/
      minifyEnabled・proguardFilesを削除（すべてbaseモジュールから継承される）、
      wearのdefaultConfigにtargetSdkを指定できない制約がありminSdkのみ26へ変更（mobileと一致、
      元は30。未確認: wearCompose/wearTiles等のライブラリがminSdk26で実機動作するかは未検証）、
      dependenciesにimplementation(project(":mobile"))を追加（AGPの制約でdynamic-feature
      モジュールはbaseモジュールへの依存宣言が必須、欠けるとprocessDebugMainManifestが
      "Collection is empty"で失敗する）。mobile/build.gradle.ktsのandroidブロックへ
      dynamicFeatures += setOf(":wear")を追加。ルートbuild.gradle.ktsとgradle/libs.versions.toml
      にcom.android.dynamic-featureプラグインを追加登録（未登録だとプラグイン解決エラーになる）。
      wear/AndroidManifest.xmlにxmlns:dist名前空間とdist:module（instant=false、
      install-time delivery、fusing include=true）を追加。mobile/wear双方の<application>要素の
      android:theme属性がマニフェストマージ時に衝突したため、各モジュールのMainActivityへ
      個別のandroid:theme指定へ移行し<application>側から削除。mobile/wearが同名のランチャー
      アイコンリソース（ic_launcher/ic_launcher_round、drawable/mipmap）を別内容で持っていたため
      AABパッケージング時に衝突（"contain entry ... with different content"）、wear固有の
      アイコンをic_launcher_wear系にリネームした上でmobile側res配下へ配置（AGPの制約:
      マニフェストで参照するリソースはbaseモジュールに存在する必要があり、featureモジュール側に
      置くとAAPTのリンク時に解決できない）。wear/proguard-rules.proはmobileと同一内容
      （com.sesamiwear.**を保護）で不要になったため削除。scripts/release-build.ps1は
      :wear:bundleReleaseという独立タスクがfeatureモジュール単体では実行できなくなったため
      :mobile:bundleReleaseのみに変更（wear分は統合されたAAB1本に含まれる）。
      リリースビルド（:mobile:bundleRelease、R8 minify有効）検証時、wearが直接implementation
      していたguavaとmobile側がplay-services-wearable経由で持つguavaが重複しR8が
      「ListenableFutureが2重定義」エラーで失敗したため、wearのguava依存をcompileOnlyへ変更。
      その結果wearが実行時に使うFutures/SettableFuture等の実装クラスが欠落するR8エラーへ
      変わったため、mobile側にimplementation(libs.guava)を追加しbaseモジュールが
      ランタイムクラスパスへguava実装を提供する構成にした。
    変更ファイル:
      - build.gradle.kts
      - gradle/libs.versions.toml
      - mobile/build.gradle.kts
      - mobile/src/main/AndroidManifest.xml
      - mobile/src/main/res/drawable/ic_launcher_wear_background.xml
      - mobile/src/main/res/drawable/ic_launcher_wear_foreground.xml
      - mobile/src/main/res/mipmap-anydpi-v26/ic_launcher_wear.xml
      - mobile/src/main/res/mipmap-anydpi-v26/ic_launcher_wear_round.xml
      - wear/build.gradle.kts
      - wear/src/main/AndroidManifest.xml
      - wear/proguard-rules.pro（削除）
      - scripts/release-build.ps1
    検証コマンド: >
      ./gradlew ktlintCheck && ./gradlew detekt && ./gradlew lintDebug &&
      ./gradlew testDebugUnitTest && ./gradlew assembleDebug && ./gradlew :mobile:bundleDebug &&
      ./gradlew :mobile:bundleRelease
    検証結果: 成功 - 全コマンドがBUILD SUCCESSFULで完了。:mobile:bundleDebug/:mobile:bundleRelease
      （署名なし、R8 minify込み）双方でwearをfeatureとして含む単一AABの生成を確認した。
      実機でのインストール・自動プッシュ配信の動作確認、署名済みリリースビルドでの検証は
      未実施（BL-038、人手検証）。
    関連ID:
      - BL-036

- date: 2026-08-21 01:05
  summary: ストア掲載情報・プライバシーポリシーのドラフトを作成
  details:
    変更内容: >
      Google Play Console提出に必要なストア掲載情報とプライバシーポリシーのドラフトを
      docs/store/配下に新規作成した。STORE_LISTING.mdにはアプリ名・短い説明・詳細な説明・
      カテゴリ案・対象デバイスを記載し、mobile/wearが別applicationIdの独立アプリである
      制約（BL-031）に言及した。PRIVACY_POLICY.mdには収集する情報（uuid/apikey/secretKey、
      利用者本人が入力しサーバー側では収集しない）、保存方法（mobile側の
      EncryptedSharedPreferencesのみ、wear側は非保持）、送信先（CANDY HOUSE Sesame APIのみ、
      広告/分析SDK不使用）、削除方法を記載した。お問い合わせ先メールアドレスは本人確認なしに
      個人情報をドラフトへ記入することを避け、プレースホルダーのまま「未確認」と明記した。
      実際の公開URLでのホスティングとPlay ConsoleのData safety申告への反映はBL-033
      （人手検証）で行う。
    変更ファイル:
      - docs/store/STORE_LISTING.md
      - docs/store/PRIVACY_POLICY.md
      - docs/records/managed/DESIGN.md
    検証コマンド: npx markdownlint-cli2 "**/*.md"
    検証結果: 成功 - docs/store配下の新規ファイルは0件、リポジトリ全体でも新規issueなし
      （既知のBL-021・BACKLOG.md MD041のみ残存、対象外）
    関連ID:
      - BL-030
```

```yaml
- date: 2026-08-21 01:00
  summary: リリースビルド用バッチファイルとバージョン管理を実装
  details:
    変更内容: >
      ユーザー依頼により、リリースビルドを簡易実施するscripts/release-build.batを作成した。
      scripts/version.propertiesに現在のversionCode/versionNameを永続化し、引数なし実行時は
      versionCodeを1インクリメント、-VersionCode/-VersionName指定時はその値を固定使用する
      scripts/release-build.ps1（PowerShell）を実装した。mobile/wearのbuild.gradle.ktsへ
      findProperty("appVersionCode"/"appVersionName")によるGradleプロパティ上書き対応を追加し
      （未指定時は既定値のまま）、release-build.ps1から-PappVersionCode/-PappVersionNameとして
      gradlewへ渡す設計にした。
      実装中、Windows PowerShell 5.1（powershell.exe）がBOM無しUTF-8スクリプト内の日本語コメントを
      正しく解釈できず「} finally {」付近で構文エラーになる問題が発生し、スクリプトへUTF-8 BOMを
      付与し、かつ.batからpwsh（PowerShell 7）を優先呼び出しするよう変更して解消した。
      PowerShellツールが本セッションで機能しなかった（単純なWrite-Hostでもexit code 1）ため、
      Bashツール経由でのpowershell.exe/pwsh直接呼び出しとGit Bashからの.bat直接実行で
      代替検証した。インクリメント・固定値指定の両パターンでversion.propertiesの更新と
      AABビルド（bundleRelease）の成功、versionCode/versionNameがoutput-metadata.jsonへ
      正しく反映されることを確認した。README.mdに使い方を追記した。
    変更ファイル:
      - scripts/release-build.bat
      - scripts/release-build.ps1
      - scripts/version.properties
      - mobile/build.gradle.kts
      - wear/build.gradle.kts
      - README.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew :mobile:assembleDebug --no-daemon -PappVersionCode=42 -PappVersionName=1.2.3;
      pwsh -NoProfile -File scripts/release-build.ps1 -VersionCode 999 -VersionName 9.9.9;
      powershell.exe -NoProfile -File scripts/release-build.ps1 -VersionCode 5 -VersionName 0.2.0;
      ./scripts/release-build.bat（引数なし、インクリメント確認）;
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - すべてのビルド・実行確認がBUILD SUCCESSFULまたは正常終了。
      全モジュールのテストも成功
    関連ID:
      - BL-035
```

```yaml
- date: 2026-08-21 00:37
  summary: リリースビルドのProGuard/R8設定を整備しisMinifyEnabledを有効化
  details:
    変更内容: >
      mobile/proguard-rules.pro・wear/proguard-rules.pro（build.gradle.ktsから参照されていたが
      実体が存在しなかったため新規作成）へ、kotlinx.serializationの@Serializableクラス保護の
      keepルールを追加した。mobile/wear双方のbuildTypes.releaseでisMinifyEnabledをfalseから
      trueへ変更し、難読化・縮小を有効化した状態でassembleReleaseが成功することを確認した。
    変更ファイル:
      - mobile/proguard-rules.pro
      - wear/proguard-rules.pro
      - mobile/build.gradle.kts
      - wear/build.gradle.kts
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew :mobile:assembleRelease :wear:assembleRelease --no-daemon;
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。難読化有効なリリースビルドと全モジュールのテストが成功
    関連ID:
      - BL-029
```

```yaml
- date: 2026-08-21 00:28
  summary: リリースビルド用の署名設定を追加
  details:
    変更内容: >
      mobile/wearのbuild.gradle.ktsへリリース署名設定を追加した。local.properties
      （.gitignore対象）からRELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/
      RELEASE_KEY_PASSWORDを読み込み、存在する場合のみsigningConfigsのreleaseを構築して
      buildTypesへ適用する設計にした。local.properties未設定時はassembleDebug/assembleRelease
      いずれもunsignedのまま成功することをビルドで確認した。README.mdへKeystore生成コマンド
      （keytool -genkeypair）と設定手順、bundleReleaseコマンドを追記し、mobile/wearが
      別applicationIdであるためGoogle Playには2つの別アプリとして登録することになる旨も明記した。
    変更ファイル:
      - mobile/build.gradle.kts
      - wear/build.gradle.kts
      - README.md
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon;
      ./gradlew :mobile:assembleRelease :wear:assembleRelease --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。unsigned releaseビルドを含め全モジュールが成功
    関連ID:
      - BL-028
```

```yaml
- date: 2026-08-21 00:20
  summary: mobile/wearの実アイコン(Adaptive Icon)を作成し反映
  details:
    変更内容: >
      Google Play限定公開に向けて、南京錠をモチーフにしたVectorDrawableベースのAdaptive Iconを
      mobile/wear両モジュールへ追加した。drawable/ic_launcher_background.xml（背景、単色
      #1E3A5F）・ic_launcher_foreground.xml（前景、白い南京錠のシルエット、シャックル+本体+鍵穴）、
      mipmap-anydpi-v26/ic_launcher.xml・ic_launcher_round.xml（Adaptive Icon定義）を作成し、
      AndroidManifestのandroid:icon/android:roundIcon（Wear側Complicationサービスの
      android:iconも含む）を@android:drawable/sym_def_app_iconから差し替えた。
      図案はXMLパスの手書きによる簡易的なものであり、視覚的な洗練度はデザイナーによる調整を
      前提としていない。Play Console提出用の高解像度アイコン画像(512x512 PNG)はXMLベースでの
      生成が技術的に困難なため未対応（BL-034、人手検証）。
    変更ファイル:
      - mobile/src/main/res/drawable/ic_launcher_background.xml
      - mobile/src/main/res/drawable/ic_launcher_foreground.xml
      - mobile/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
      - mobile/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
      - mobile/src/main/AndroidManifest.xml
      - wear/src/main/res/drawable/ic_launcher_background.xml
      - wear/src/main/res/drawable/ic_launcher_foreground.xml
      - wear/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
      - wear/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。全モジュールのテストが成功
    関連ID:
      - BL-027
```

```yaml
- date: 2026-08-20 23:40
  summary: secretKey検証不足によるクラッシュリスクを修正
  details:
    変更内容: >
      コードレビューで発見したsecretKeyの検証不足を修正した。core.SesameCredentialsへ
      secretKeyBytesOrNull（Base64デコード失敗時・デコード後16バイトでない場合にnullを返す、
      例外を投げない安全なアクセサ）を追加し単体テスト4件で検証した。
      mobile.credentials.CredentialsInputValidatorをこのsecretKeyBytesOrNullを使う実装へ変更し、
      不正な鍵は設定画面で保存できないようにした（既存テストのダミー値"secret"は新しい検証で
      不正と判定されるため、有効な16バイトBase64値へ差し替え、不正なBase64・不正な鍵長のテスト
      ケースを追加）。mobile.messaging.SesameMessageListenerService.createHandlerも同じ
      secretKeyBytesOrNullを使うよう修正し、過去に保存された不正な鍵が万一残っていても
      例外でクラッシュせずFAILUREへフォールバックするようにした。
      detektのReturnCountルールがcreateHandlerの早期return3連続に反応したため、
      null合成条件式1つのreturnへ書き換えた。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentials.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCredentialsTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputValidator.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputValidatorTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameCredentialsTest 4件、CredentialsInputValidatorTest 7件
      （新規3件含む）を含む全モジュールのテストが成功
    関連ID:
      - BL-026
```

```yaml
- date: 2026-08-20 00:47
  summary: Tileにアクセシビリティ用のcontentDescriptionを追加
  details:
    変更内容: >
      BL-019のUI/UXレビューで発見したTileのアクセシビリティ不足を修正した。
      SesameTileService.buildStatusBoxのModifiersへModifiersBuilders.Semanticsを追加し、
      SesameTileContent.statusLabel(state)をcontentDescriptionとして設定した。
      これによりBL-019のレビュー観点（視認性・誤操作防止・セキュリティ/プライバシー・
      フィードバックの分かりやすさ・アクセシビリティ）で挙げた指摘事項（BL-022〜BL-025）が
      すべて対応済みとなった。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。全モジュールのテストが成功
    関連ID:
      - BL-025
```

```yaml
- date: 2026-08-20 00:42
  summary: 設定画面へ保存フィードバックと入力バリデーションを追加
  details:
    変更内容: >
      BL-019のUI/UXレビューで発見した設定画面のフィードバック・バリデーション不足を修正した。
      mobile.credentials.CredentialsInputValidator（uuid/apikey/secretKeyBase64のいずれかが
      空欄なら無効と判定、Android非依存）を実装し単体テスト5件で検証した。
      CredentialsSettingsScreenの保存ボタンへenabled=isInputValidとして組み込み、
      空欄のまま保存できないようにした。保存成功時は「保存しました」というテキストを
      LaunchedEffect+delayで2秒間表示するフィードバックを追加した。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputValidator.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/credentials/CredentialsInputValidatorTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。CredentialsInputValidatorTest 5件を含む
      全モジュールのテストが成功
    関連ID:
      - BL-024
```

```yaml
- date: 2026-08-20 00:38
  summary: secretKey入力欄をマスキング表示に変更
  details:
    変更内容: >
      BL-019のUI/UXレビューで発見したセキュリティ/プライバシー上の指摘を修正した。
      CredentialsSettingsScreenのsecretKey入力フィールドへPasswordVisualTransformationを設定し、
      平文表示による肩越しの盗み見リスクを解消した。表示/非表示切り替えトグルは今回のスコープ外とした。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。全モジュールのテストが成功
    関連ID:
      - BL-023
```

```yaml
- date: 2026-08-20 00:32
  summary: 解錠確認ボタンのラベルを実際の操作方法に一致させて修正
  details:
    変更内容: >
      BL-019のUI/UXレビューで発見した表記不整合を修正した。SesameActionActivity.ktの
      解錠確認ボタンのラベルを「ホールドで解錠」（実装はワンタップ確認のため実態と不一致だった）
      から「タップして解錠」へ変更した。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。全モジュールのテストが成功
    関連ID:
      - BL-022
```

```yaml
- date: 2026-08-20 00:26
  summary: UI/UXレビューを実施しBACKLOGへ改善タスクを分解登録
  details:
    変更内容: >
      実装済みUI/UX（SesameTileService/SesameTileContent、SesameActionActivity、
      CredentialsSettingsScreen、SesameComplicationDataSourceService）をPLAN.mdのUX要件と
      突き合わせてコードベース上でレビューした。視認性・誤操作防止・セキュリティ/プライバシー・
      フィードバックの分かりやすさ・アクセシビリティの観点で所見をまとめDESIGN.mdへ記録した。
      特に重要な指摘として、SesameActionScreenの解錠確認ボタンのラベル「ホールドで解錠」が
      実際のワンタップ動作と食い違っている点（ユーザーの長押し試行による誤操作リスク）と、
      CredentialsSettingsScreenのsecretKey入力欄が平文表示のままである点（盗み見リスク）を
      発見し、それぞれBL-022・BL-023として要修正登録した。設定画面の保存フィードバック欠如・
      入力バリデーション欠如をBL-024、Tileのアクセシビリティ（contentDescription未設定）を
      BL-025として登録した。器のみだったBL-020はこの分解をもって完了とした。
    変更ファイル:
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: 検証対象のコード変更なし（レビュー作業のみ）
    検証結果: 該当なし
    関連ID:
      - BL-019
      - BL-020
```

```yaml
- date: 2026-08-20 00:23
  summary: README.mdを新規作成し、DESIGN.mdのMarkdownlint違反を修正
  details:
    変更内容: >
      アーキテクチャ概要（core/mobile/wearの役割分担）、前提環境、資格情報の取得・設定手順、
      品質ゲート5コマンド、プロジェクト構成、既知の未確認事項・制約をまとめたREADME.mdを新規作成した。
      npx markdownlint-cli2 "**/*.md" 実行時に既存ファイルのDESIGN.md（bare URL、MD034）と
      BACKLOG.md（先頭見出し欠落、MD041）の違反を発見した。DESIGN.mdの違反はマーカー内の変更で
      修正可能だったためbare URLをMarkdownリンク形式へ修正した。BACKLOG.mdの違反修正には
      マーカー外（COPILOT_RECORDS:BEGINより前）への追記が必要でCLAUDE.mdの規約に抵触する可能性が
      あるため、修正はせずBL-021として要確認登録した。
    変更ファイル:
      - README.md
      - docs/records/managed/DESIGN.md
      - docs/records/managed/BACKLOG.md
    検証コマンド: >
      npx markdownlint-cli2 "**/*.md"
    検証結果: 成功 - README.md/DESIGN.mdの違反解消を確認（BACKLOG.mdの1件はBL-021として要確認のまま残存）
    関連ID:
      - BL-018
```

```yaml
- date: 2026-08-20 00:08
  summary: Tileの背景色による状態表示を実装
  details:
    変更内容: >
      PLAN.mdのUX要件「Tileはロック状態をアイコン・色でひと目表示」のうち、BL-007時点で
      テキスト表示のみに留まっていた色分けを追加した。wear.tile.SesameTileContentへ
      backgroundColorArgb(state)（施錠中=緑/解錠中=赤/通信中=黄/未接続・不明=グレー）を追加し
      単体テスト2件で検証した（3状態が異なる色であること、未接続/不明が同色であること）。
      SesameTileServiceのBoxにModifiersBuilders.Backgroundとして組み込み、既存のクリック
      アクション用Modifiersと統合した（Boxが持てるModifiersは1つのため、背景色とクリック設定を
      同一のModifiers.Builderにまとめる形に既存コードをリファクタリングした）。
      アイコン自体は未実装のままで、テキスト+背景色による表現に留まる。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileContent.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/tile/SesameTileContentTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameTileContentTest 5件（新規2件含む）を含む
      全モジュールのテストが成功
    関連ID:
      - BL-017

- date: 2026-08-20 00:03
  summary: Wear側の結果受信とハプティクス再生の橋渡しを実装
  details:
    変更内容: >
      wear.messaging.SesameResultHandler（path/payloadからHapticPatternを決定、
      PATH_COMMAND_RESULT以外はnull、Android非依存）を実装し単体テスト3件で検証した。
      wear.messaging.SesameResultListenerService（WearableListenerService実装、判定結果を
      SesameHapticPlayerへ渡す薄いアダプタ）を実装し、AndroidManifestへMESSAGE_RECEIVED
      アクション・pathPrefix="/sesami-wear/result"のintent-filter付きで登録した。
      これによりBL-008で実装済みだったハプティクス再生ロジックの呼び出し元が揃い、
      施錠/解錠のData Layer APIメッセージングとハプティクスフィードバックの結線が完了した。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameResultHandler.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/messaging/SesameResultHandlerTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameResultListenerService.kt
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameResultHandlerTest 3件を含む全モジュールのテストが成功
    関連ID:
      - BL-016

- date: 2026-08-19 23:57
  summary: TileとComplicationへの実データ結線(DataClient同期)を実装
  details:
    変更内容: >
      DataClient（DataItem）でMobile→Wear間にロック状態を同期する方式を採用した。
      core.SesameWearProtocolへSTATUS_DATA_ITEM_PATH/KEY_IS_LOCKED/KEY_UPDATED_AT_EPOCH_MILLIS
      を追加し、core.SesameStatusSnapshot（スナップショット型）とcore.SesameStatusSnapshotFactory
      （DataMapの生値からスナップショットを構築するAndroid非依存ロジック、単体テスト2件）を実装した。
      mobile.messaging.SesameStatusSyncer（DataClient.putDataItemラッパー）を追加し、
      SesameMessageListenerServiceでコマンド送信成功時に意図した状態（LOCK成功→施錠、
      UNLOCK成功→解錠）を同期する簡略化ロジックとした。wear.messaging.SesameStatusSnapshotReader
      （DataClient経由の読み取り）を追加し、SesameTileServiceとSesameComplicationDataSourceService
      の両方をSesameConnectedNodeProviderと組み合わせた実データ表示に置き換えた。
      TileService.onTileRequestはコールバック形式でListenableFutureを返す必要があるため、
      Guavaの新規依存を避けSettableFutureで手動ブリッジした。
      実際のCHSesame2Statusとの整合、アプリ起動時や他経路での状態変化の反映漏れは
      未確認事項としてDESIGN.mdに明記した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusSnapshot.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameStatusSnapshotFactory.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameStatusSnapshotFactoryTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameStatusSyncer.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameStatusSnapshotReader.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameStatusSnapshotFactoryTest 2件を含む
      全モジュールのテストが成功
    関連ID:
      - BL-015

- date: 2026-08-19 20:58
  summary: Complicationでロック状態を文字盤表示する機能を実装
  details:
    変更内容: >
      wear.complication.SesameComplicationContent（TileDisplayState→短い表示文言「施錠」
      「解錠」「通信中」「未接続」「不明」、Android非依存）を実装し単体テスト2件で検証した。
      wear.complication.SesameComplicationDataSourceService（ComplicationDataSourceService実装、
      ShortTextComplicationDataを返す、getPreviewDataも実装）を追加し、AndroidManifestへ
      BIND_COMPLICATION_PROVIDER権限・ACTION_COMPLICATION_UPDATE_REQUEST intent-filter・
      SUPPORTED_TYPES=SHORT_TEXTのmeta-data付きで登録した。SesameTileServiceと同様、実データ
      （スマホ接続状態・ロック状態）との結線は未実装で常にUNKNOWN状態を表示するプレースホルダーの
      ままであり、この点はBL-015へ統合される。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationContent.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/complication/SesameComplicationContentTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/complication/SesameComplicationDataSourceService.kt
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameComplicationContentTest 2件を含む
      全モジュールのテストが成功
    関連ID:
      - BL-009

- date: 2026-08-19 18:48
  summary: ハプティクスフィードバック(成功/失敗の振動パターン)を実装
  details:
    変更内容: >
      wear.haptics.HapticPattern（SUCCESS/FAILURE）とwear.haptics.SesameHapticPatternResolver
      （SesameCommandResultからHapticPatternを決定、Android非依存）を実装し単体テスト2件で検証した。
      wear.haptics.SesameHapticPlayer（Vibrator/VibratorManagerベースの振動再生、API31分岐を含む
      薄いアダプタ）を実装し、android.permission.VIBRATEをAndroidManifestへ追加した
      （lintDebugがMissingPermissionエラーで検出、追加により解消）。
      SesameHapticPlayerを呼び出す箇所（Wear側でのPATH_COMMAND_RESULT受信リスナー）はまだ存在せず、
      BL-014でコマンド送信をFire-and-forgetにしたため結果受信の仕組み自体が未実装であることに起因する。
      受信リスナーとハプティクス再生の橋渡しを新規タスクBL-016へ切り出しBACKLOGへ登録した
      （BL-011の依存にも追加）。
    変更ファイル:
      - wear/src/main/kotlin/com/sesamiwear/wear/haptics/HapticPattern.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/haptics/SesameHapticPatternResolver.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/haptics/SesameHapticPatternResolverTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/haptics/SesameHapticPlayer.kt
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameHapticPatternResolverTest 2件を含む
      全モジュールのテストが成功
    関連ID:
      - BL-008

- date: 2026-08-19 13:04
  summary: Tileのクリックアクション(施錠ワンタップ/解錠確認)を実装
  details:
    変更内容: >
      core.api.SesameCommandConfirmation（UNLOCKのみ確認要求）、wear.tile.SesameTileActions
      （Tile状態→提示コマンド決定）、wear.action.SesameActionCommandParser（Intent Extra文字列→
      SesameCommand）をAndroid非依存で実装し単体テスト計8件で検証した。
      wear.action.SesameActionActivity（LOCKはワンタップ即送信、UNLOCKは確認ボタン後に送信する
      Fire-and-forget方式のTrampoline的Activity）、wear.messaging.SesameConnectedNodeProvider
      （NodeClient.connectedNodesから接続先ノードID取得）を実装し、AndroidManifestへ
      SesameActionActivity（exported=false）を登録した。SesameTileServiceにも
      ActionBuilders.LaunchActionによるクリック設定を組み込んだ。
      現状Tileは常にUNKNOWN状態を返すプレースホルダーのため実際にはまだタップできず、
      Tileへの実データ結線を新規タスクBL-015へ切り出しBACKLOGへ登録した（BL-011の依存にも追加）。
      コマンド送信はFire-and-forgetのため、成功/失敗のリアルタイム反映とハプティクスはBL-008に委ねる。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameCommandConfirmation.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameCommandConfirmationTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileActions.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/tile/SesameTileActionsTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionCommandParser.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/action/SesameActionCommandParserTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/action/SesameActionActivity.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameConnectedNodeProvider.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/main/AndroidManifest.xml
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。core 2件、wear 6件（計8件）の新規テストを含む
      全モジュールのテストが成功
    関連ID:
      - BL-014

- date: 2026-08-19 09:09
  summary: WearableListenerServiceの実サービス化と資格情報結線を実装
  details:
    変更内容: >
      mobile.messaging.SesameMessageListenerService（WearableListenerService実装）を追加。
      onMessageReceivedでSesameCredentialsStore経由の資格情報からSesameCommandHandlerを構築し
      （未設定時はFAILURE）、実行結果をWearable.getMessageClient経由でWear側へ返す。
      AndroidManifestへMESSAGE_RECEIVEDアクション・pathPrefix="/sesami-wear/"のintent-filter付きで
      登録した。wear.messaging.SesameCommandSenderProvider（Wearable.getMessageClient(context)から
      SesameCommandSenderを構築するファクトリ）も実装した。
      実装中、core.SesameApiClientのコンストラクタがOkHttpClient型をデフォルト引数として公開APIに
      含んでいたため、core/build.gradle.ktsのokhttp依存をimplementationからapiへ変更する必要があった
      （implementationのままだとmobileモジュールでCannot access class 'okhttp3.OkHttpClient'
      コンパイルエラーになった）。
    変更ファイル:
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameMessageListenerService.kt
      - mobile/src/main/AndroidManifest.xml
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameCommandSenderProvider.kt
      - core/build.gradle.kts
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL（本イテレーションは薄いアダプタ実装のため新規ユニットテストなし。
      既存の全モジュールテストが引き続き成功）
    関連ID:
      - BL-013

- date: 2026-08-19 07:48
  summary: 資格情報(uuid/apikey/secretKey)の保存機能と設定画面を実装
  details:
    変更内容: >
      core.SesameCredentials（secretKeyはBase64文字列保持、secretKeyBytesで復号）、
      core.SesameKeyValueStore（永続化抽象インターフェース）、core.SesameCredentialsStore
      （save/load/clearロジック本体、Android非依存）を実装し単体テスト4件で検証した。
      mobile.credentials.EncryptedSharedPreferencesKeyValueStore（EncryptedSharedPreferences+
      MasterKeyベースの実装アダプタ）とmobile.credentials.CredentialsSettingsScreen
      （uuid/apikey/secretKey入力・保存のCompose画面）を実装し、MainActivityから呼び出す構成に
      置き換えた。detektのReturnCountルールがSesameCredentialsStore.load()の早期return4連続に
      反応したため、null合成条件式1つのreturnへ書き換えた。ログ出力は一切実装しておらず、
      平文資格情報がログへ出力される経路がないことをコードレビューで確認した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentials.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameKeyValueStore.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameCredentialsStore.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCredentialsStoreTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/EncryptedSharedPreferencesKeyValueStore.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/credentials/CredentialsSettingsScreen.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameCredentialsStoreTest 4件を含む全モジュールのテストが成功
    関連ID:
      - BL-005

- date: 2026-08-19 07:36
  summary: Wear OS Tile UI(状態表示)を実装(クリックアクションはBL-014へ分離)
  details:
    変更内容: >
      core.TileDisplayState（LOCKED/UNLOCKED/IN_PROGRESS/DISCONNECTED/UNKNOWN）と
      core.TileDisplayStateResolver（スマホ接続状態・コマンド実行中フラグ・ロック状態から
      表示状態を決定）、wear.tile.SesameTileContent（状態→表示文言マッピング）を実装し、
      単体テスト9件で検証した。wear.tile.SesameTileService（TileService実装、ステータステキストの
      みを表示する最小構成）を実装し、AndroidManifestへBIND_TILE_PROVIDERのintent-filter付きで登録した。
      実装中、androidx.wear.tiles:tiles:1.4.1が内部でandroidx.wear.protolayoutパッケージへ移行済み
      であることが判明し、LayoutElementBuilders/TimelineBuilders/ResourceBuilders/material.Textの
      importをprotolayoutパッケージへ修正し、protolayout-material依存を追加した。また
      Futures.immediateFuture()の解決にはcom.google.guava:guavaの明示的な追加が必要だった
      （推移的に入るlistenablefuture:1.0はFuturesヘルパーを含まないため）。
      Tileタップ時のクリックアクション（施錠ワンタップ、解錠ホールド確認）と実データとの結線は、
      BL-013（資格情報結線）が前提となるため新規タスクBL-014へ切り出しBACKLOGへ登録した
      （BL-011の依存にも追加）。Android Studioでのプレビュー確認は本環境で自動実行できないため
      完了条件から除外し、実機確認はBL-011（人手検証）に委ねることをDESIGN.mdへ明記した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/TileDisplayState.kt
      - core/src/main/kotlin/com/sesamiwear/core/TileDisplayStateResolver.kt
      - core/src/test/kotlin/com/sesamiwear/core/TileDisplayStateResolverTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileContent.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/tile/SesameTileService.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/tile/SesameTileContentTest.kt
      - wear/src/main/AndroidManifest.xml
      - gradle/libs.versions.toml
      - wear/build.gradle.kts
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。core 6件、wear 3件（計9件）の新規テストを含む
      全モジュールのテストが成功
    関連ID:
      - BL-007

- date: 2026-08-19 01:10
  summary: Data Layer APIメッセージングのコアロジックを実装(WearableListenerService結線はBL-013へ分離)
  details:
    変更内容: >
      core.SesameMessageSender（送信抽象化インターフェース）とcore.SesameCommandResult
      （成功/失敗の1バイトペイロード変換）を追加。wear.messaging.SesameCommandSender
      （lock/unlock意図の送信ロジック、Android非依存）、mobile.messaging.SesameCommandHandler
      （受信パス判定→SesameApiClient実行→結果返却、Android非依存）を実装し、それぞれフェイク実装
      /MockWebServerで単体テストした。wear.messaging.MessageClientSesameMessageSender
      （MessageClient.sendMessage().await()の薄いアダプタ）も実装したが、Android Google Play
      Services依存のためユニットテスト対象外とした。
      Mobile側のWearableListenerService実サービスクラスとAndroidManifest登録、
      apikey/secretKey/uuidの取得元との結線は、BL-005（シークレット保存）が未完了で
      資格情報取得方式が確定していないため、新規タスクBL-013へ切り出しBACKLOGへ登録した
      （BL-011の依存にもBL-013を追加）。あわせてBL-010のタスク内容からBL-012で削除済みの
      isBatteryCriticalへの言及を除去し、施錠/解錠仕様の確認事項を追記した。
      detektのSwallowedExceptionルールがSesameCommandHandlerの例外握りつぶしに反応したため、
      仕様上意図的である旨のコメントと@Suppressを付与した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/SesameMessageSender.kt
      - core/src/main/kotlin/com/sesamiwear/core/SesameCommandResult.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameCommandResultTest.kt
      - mobile/src/main/kotlin/com/sesamiwear/mobile/messaging/SesameCommandHandler.kt
      - mobile/src/test/kotlin/com/sesamiwear/mobile/messaging/SesameCommandHandlerTest.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/SesameCommandSender.kt
      - wear/src/main/kotlin/com/sesamiwear/wear/messaging/MessageClientSesameMessageSender.kt
      - wear/src/test/kotlin/com/sesamiwear/wear/messaging/SesameCommandSenderTest.kt
      - gradle/libs.versions.toml
      - mobile/build.gradle.kts
      - wear/build.gradle.kts
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。core 3件、mobile 4件、wear 2件（計9件）の新規テストを含む
      全モジュールのテストが成功
    関連ID:
      - BL-006

- date: 2026-08-19 00:54
  summary: Sesame API施錠/解錠(POST, AES-CMAC署名付き)クライアントを実装
  details:
    変更内容: >
      SesameCommand（LOCK=82/UNLOCK=83、pysesame3のCHSesame2CMDを参照）、SesameCommandSigner
      （現在時刻を4バイト・リトルエンディアン化した[1:4]をAesCmac.computeで署名し16バイト全体を
      hex化する、internal実装）、SesameApiClient.sendCommand()（POST {baseUrl}/{uuid}/cmd、
      cmd/history/signのJSONボディ、x-api-keyヘッダー）を実装した。
      署名生成の正確性を独立検証するため、pip経由でpycryptodomeを導入し、
      CMAC.new(key, ciphermod=AES)による計算結果とKotlin実装の出力が固定タイムスタンプで
      一致することをテストで確認した（鍵はRFC 4493のダミー鍵）。
      MockWebServerでPOSTリクエストのメソッド・パス・ヘッダー・ボディ（cmd値、sign長）を検証し、
      HTTP非成功時にSesameApiExceptionを送出することも確認した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameCommand.kt
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameCommandSigner.kt
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiClient.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameCommandSignerTest.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameApiClientTest.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameCommandSignerTest 2件、SesameApiClientTest 6件を含む
      全モジュールのテストが成功
    関連ID:
      - BL-004

- date: 2026-08-19 00:45
  summary: pysesame3実装を参照しSesameStatusのフィールド構成を実仕様に合わせて修正
  details:
    変更内容: >
      BL-004（施錠/解錠API実装）着手前に、参考実装pysesame3のソースコード
      （pysesame3/helper.py, cloud.py, const.py, auth.py）をGitHub API経由で確認した。
      Web APIのGETレスポンスは実際にはbatteryVoltage/position/CHSesame2Statusの3フィールドのみで、
      BL-003で実装したisBatteryCritical/isInLockRange/isInUnlockRangeを直接デコードする設計は
      誤りだったため、isInLockRange/isInUnlockRangeをCHSesame2Statusからの計算プロパティへ変更し、
      Web APIレスポンスに存在しないisBatteryCriticalフィールドを削除した。
      あわせてBL-004で使う施錠/解錠APIの正確な仕様（POSTエンドポイントが`/cmd`サフィックス付きである
      こと、cmd/history/signのペイロード構造、署名対象バイト列がUnixタイムスタンプ4バイト
      リトルエンディアンの[1:4]でありCMAC出力16バイト全体をhex化して使うこと）をDESIGN.mdへ記録した。
      テストはロック中/未ロック中のステータス導出、未知フィールドの無視、異常系の計4件に拡充。
      修正中、@Serializableアノテーションが要求するcompanion objectをprivateにしたことで
      SesameStatus.serializer()の呼び出しがコンパイルエラーになったため、companion object自体は
      公開のまま内部定数のみprivateにする形に修正した。detektのForbiddenCommentルールが
      引用コメント中の"TODO"文字列に反応したため文言も修正した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameStatus.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameApiClientTest.kt
      - docs/records/managed/DESIGN.md
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameApiClientTestの4件を含む全モジュールのテストが成功
    関連ID:
      - BL-012

- date: 2026-08-19 00:37
  summary: Sesame API状態取得（GET）クライアントをcoreモジュールへ実装
  details:
    変更内容: >
      com.sesamiwear.core.api.SesameApiClientとして状態取得（GET）処理を実装した。
      OkHttpでx-api-keyヘッダー付きリクエストを送信し、kotlinx.serialization.jsonで
      SesameStatus（batteryVoltage/isBatteryCritical/position/CHSesame2Status/
      isInLockRange/isInUnlockRange）へデコードする。HTTP非成功時はSesameApiExceptionを送出する。
      SesameStatusのフィールド構成はCANDY HOUSE公式APIドキュメント未参照のため一般的な
      Sesame API実装からの推測であり、未確認事項としてDESIGN.mdとBL-010（人手検証）に明記した
      （安全性に関わらない実装詳細のため、既定値を採用し確認質問を挟まず続行）。
      テストはMockWebServerを用い、正常系のレスポンスパースとリクエストヘッダー検証、
      異常系（HTTP 401）でのSesameApiException送出を確認した。
      実装中にkotlin.test.assertFailsWithが未解決（kotlin-test依存が未追加）となったため、
      追加依存を避けJUnit標準のtry-catchへ書き換えた。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameStatus.kt
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiException.kt
      - core/src/main/kotlin/com/sesamiwear/core/api/SesameApiClient.kt
      - core/src/test/kotlin/com/sesamiwear/core/api/SesameApiClientTest.kt
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。SesameApiClientTestの2件（正常系/異常系）を含む
      全モジュールのテストが成功
    関連ID:
      - BL-003

- date: 2026-08-19 00:27
  summary: AES-CMAC（RFC 4493）署名処理をcoreモジュールへ実装
  details:
    変更内容: >
      RFC 4493準拠のAES-CMACをcom.sesamiwear.core.crypto.AesCmacとして実装した。
      javax.crypto.Cipher（AES/ECB/NoPadding）でAES-128の単一ブロック暗号化を行い、
      サブキー生成（K1/K2）・メッセージのブロック分割・最終ブロックのパディング/XOR処理を
      RFC 4493 Section 2.3〜2.4のアルゴリズムに忠実に実装した。
      Sesame APIコマンド固有のバイト列組み立て（cmd種別・タイムスタンプ等の直列化）は
      BL-004（施錠/解錠API実装）側で本関数を呼び出す形で実装する（本タスクの範囲外）。
      単体テストはRFC 4493 Appendix Aの公開テストベクタ4件（空メッセージ/16/40/64バイト）と、
      鍵長不正時にIllegalArgumentExceptionを送出することを検証する異常系1件の計5件。
      実装中にBOM/Kotlin標準ライブラリの誤認識（Byte型にxor中置関数が存在しない）による
      コンパイルエラーが発生したためInt経由のXOR処理に修正し、ktlintFormatでの
      フォーマット違反（複数行式の改行位置）も解消した。
    変更ファイル:
      - core/src/main/kotlin/com/sesamiwear/core/crypto/AesCmac.kt
      - core/src/test/kotlin/com/sesamiwear/core/crypto/AesCmacTest.kt
    検証コマンド: >
      ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug --no-daemon
    検証結果: 成功 - BUILD SUCCESSFUL。AesCmacTestの5件（RFC 4493テストベクタ4件＋異常系1件）を含む
      全モジュールのテストが成功
    関連ID:
      - BL-002

- date: 2026-08-18 07:23
  summary: Android/Wear OSマルチモジュールプロジェクトの雛形を作成（段階A→段階B移行）
  details:
    変更内容: >
      ルートGradleプロジェクトを新設し、core（純Kotlin/JVMライブラリ、CMAC実装等の配置予定地）、
      mobile（Android application、compileSdk/targetSdk 35、minSdk 26）、
      wear（Android application for Wear OS、minSdk 30）の3モジュール構成とした。
      Version Catalog（gradle/libs.versions.toml）でAGP 8.7.3・Kotlin 2.0.21・
      Compose BOM 2024.12.01・Wear Compose 1.4.1等を管理。
      ルートbuild.gradle.ktsでktlint-gradleとdetektを全サブプロジェクトへ一括適用し、
      config/detekt/detekt.ymlでComposable関数の命名規則除外（naming.FunctionNaming.ignoreAnnotated）
      と.editorconfigのktlint_function_naming_ignore_when_annotated_with設定を追加。
      Gradle Wrapperはローカルにキャッシュ済みのGradle 8.10.2から生成。
      各モジュールにMainActivity（Compose/Wear Composeの最小Hello World画面）を配置し、
      coreにはBL-006で使うData Layerメッセージパス定数（SesameWearProtocol）と単体テストを追加。
      根拠: パッケージ名はcom.sesamiwearを採用（リポジトリ名由来、安全性に関わらない既定値選択）。
      アイコンはmipmapリソース未作成のため暫定的に@android:drawable/sym_def_app_iconを参照しており、
      配布前に専用アイコンへの差し替えが必要（未対応事項としてBACKLOGへ計上せず、DESIGN.mdの制約に明記）。
    変更ファイル:
      - settings.gradle.kts
      - build.gradle.kts
      - gradle.properties
      - gradle/libs.versions.toml
      - gradle/wrapper/gradle-wrapper.properties
      - gradle/wrapper/gradle-wrapper.jar
      - gradlew
      - gradlew.bat
      - .editorconfig
      - config/detekt/detekt.yml
      - core/build.gradle.kts
      - core/src/main/kotlin/com/sesamiwear/core/SesameWearProtocol.kt
      - core/src/test/kotlin/com/sesamiwear/core/SesameWearProtocolTest.kt
      - mobile/build.gradle.kts
      - mobile/src/main/AndroidManifest.xml
      - mobile/src/main/res/values/strings.xml
      - mobile/src/main/kotlin/com/sesamiwear/mobile/MainActivity.kt
      - wear/build.gradle.kts
      - wear/src/main/AndroidManifest.xml
      - wear/src/main/res/values/strings.xml
      - wear/src/main/kotlin/com/sesamiwear/wear/MainActivity.kt
    検証コマンド: >
      ./gradlew tasks --no-daemon;
      ./gradlew ktlintCheck --no-daemon;
      ./gradlew detekt --no-daemon;
      ./gradlew lintDebug --no-daemon;
      ./gradlew testDebugUnitTest test --no-daemon;
      ./gradlew assembleDebug --no-daemon
    検証結果: 成功 - 全6コマンドがBUILD SUCCESSFULで完了（coreモジュールの単体テスト1件含む）
    関連ID:
      - BL-001
```
<!-- COPILOT_RECORDS:END -->

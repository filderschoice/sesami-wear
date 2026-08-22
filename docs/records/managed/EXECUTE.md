<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用実施記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.execute.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
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
    検証結果: 成功 - BUILD SUCCESSFUL（132 actionable tasks: 13 executed, 119 up-to-date）
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
    検証結果: 成功 - BUILD SUCCESSFUL（132 actionable tasks: 25 executed, 107 up-to-date）
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
    検証結果: 成功 - BUILD SUCCESSFUL（132 actionable tasks: 22 executed, 110 up-to-date）
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

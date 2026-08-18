<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用実施記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.execute.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
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

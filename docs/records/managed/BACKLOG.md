<!-- markdownlint-disable-file MD041 -->
<!-- Copilot専用未対応事項記録ファイル（ユーザ編集禁止） -->
<!-- このファイルはCopilotがプロンプト指示の処理実行時のみ自動更新します。 -->
<!-- schema: records.backlog.v1 -->

<!-- COPILOT_RECORDS:BEGIN -->
```yaml
- id: BL-071
  区分: 機能追加
  タスク内容: ユーザーから「登録済みすべての施錠/解錠を行えるようにしたい。Tileのデバイス選択で
    全デバイスを選択可能とし、登録済みのデバイスすべてに対して操作を行えるように。ステータス表示は
    デバイスの状態に依存するのでうまく表現して現状態を表示できるように」との要望があった。
    対応として以下を実装した。
    (1) core: SesameWearProtocolへ「全デバイス」を表す特別な値ALL_DEVICES_TARGET_UUID
    （"__all_devices__"、実際のSesame uuidと衝突しない固定文字列）を追加。
    TileDisplayStateへMIXED（施錠/解錠が混在）を追加し、isActionableをtrueにした。
    TileDisplayStateResolverへresolveAggregate（複数デバイスのロック状態リストから集約状態を
    決定。1台でも未取得(null)があれば安全側でUNKNOWN、全台施錠でLOCKED、全台解錠でUNLOCKED、
    それ以外はMIXED）を追加。
    (2) wear.ui.DeviceSelectionScreen: 登録済みデバイスが2台以上の場合のみ先頭に
    「全デバイス」の選択肢を表示するようにした（Tile/Complication共通の選択画面）。
    (3) wear.tile.SesameTileStateResolver（新規）: Tile/Complication共通の表示名・状態解決
    ロジックを集約。対象uuidがALL_DEVICES_TARGET_UUIDの場合は登録済み全デバイスの状態を
    resolveAggregateで集約し、それ以外は単一デバイスの状態を解決する（DataItemが古い場合の
    自動状態取得リクエストも両ケースで実施）。SesameTileService/SesameComplicationDataSource
    Serviceの双方がこれを利用するようリファクタリングした（重複ロジックの排除）。
    (4) wear.action.SesameActionTargetResolver（新規）: コマンド送信・状態更新の対象uuid一覧を
    解決（全デバイス時は登録済み全uuidのリスト、それ以外は単一uuid）。SesameActionActivity/
    SesameStatusRefreshActivityが利用し、全デバイス選択時はループで各デバイスへ個別に
    lock/unlock/status-requestメッセージを送信する（mobile側は既存の単一デバイス処理をそのまま
    N回受けるだけで対応可能なため、mobile側の変更は不要）。
    (5) wear.tile.SesameTileActions: MIXED状態はタップで「全施錠」（迷ったら安全側の方針、
    UNLOCKのみ確認画面を挟む既存UXと組み合わせて安全側は確認不要のまま維持）。
    (6) wear.tile.SesameTileContent/wear.complication.SesameComplicationContent: MIXED用の
    アイコン（🔀）・ラベル（施錠/解錠混在）・背景色（紫）を追加。statusLabel/actionLabelへ
    isAllDevicesパラメータ（デフォルトfalse）を追加し、全デバイス時は「全施錠中」等の文言に
    切り替える。
    (7) wear.action.SesameActionActivity: 施錠/解錠確認画面（BL-070）のボタンラベルも
    全デバイス時は「全施錠」「全解錠」に切り替える。
    Complicationは設定済み状態ではtapActionを持たない読み取り専用表示のため、全デバイス選択時も
    集約状態の表示のみでコマンド送信は行わない（Tile側のみが操作対象）
  優先度: P1
  状態: 進行中
  担当: Claude Code
  完了条件: 実機（Sesame実機2台以上）でTileのデバイス選択に「全デバイス」が表示され、選択すると
    登録済み全デバイスの集約状態（全施錠中/全解錠中/施錠解錠混在/状態不明等）が表示され、
    タップで登録済み全デバイスへ施錠/解錠コマンドが送信されることを確認でき、
    ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebugが成功する
  依存: []

- id: BL-067
  区分: バグ修正
  タスク内容: BL-066（ランチャーアイコン重複解消）の実機確認中、ユーザーから「Tileの
    デバイス切り替えを行うと『Sesami Wear』という文字が表示される。再度Tileを見ると
    切り替わっているが、施錠/解錠ボタン押下時の状態がおかしい」との報告があった。原因は、
    wear.MainActivity/SesameActionActivity/SesameStatusRefreshActivity/
    TileConfigurationActivity/ComplicationConfigurationActivityのいずれもandroid:taskAffinity
    を明示指定しておらず、デフォルト（アプリ共通）のタスク親和性を共有していたこと。
    BL-066でmobile.MainActivityがウォッチ実行時にwear.MainActivityへstartActivity（NEW_TASK
    フラグなし）していたため、そのタスクがwear.MainActivityをルートとして残留し、後続の
    Tile LaunchAction（TileConfigurationActivity等、NEW_TASKで起動される）がタスク親和性の
    一致により同一タスクへ積み重なっていたと判明した。TileConfigurationActivityが
    finish()すると、背後に残っていたwear.MainActivityが露出して「Sesami Wear」表示となり、
    さらに古いActivityインスタンスが再利用されうる状態（新しいIntent Extraが反映されない
    可能性）が「ボタン押下時の状態がおかしい」の原因と推測される。対応として、上記5つの
    Activityすべてへandroid:noHistory="true"（フォアグラウンドを外れた時点で即座に破棄し
    タスクに残留させない）とandroid:excludeFromRecents="true"を追加し、
    mobile.MainActivityのwear.MainActivityへのstartActivityへIntent.FLAG_ACTIVITY_NEW_TASK
    を明示付与した
  優先度: P1
  状態: 進行中
  担当: Claude Code
  完了条件: Tileのデバイス切り替え後に無関係な画面（「Sesami Wear」等）が表示されず、
    施錠/解錠ボタンの状態・動作が常に正しく反映されることを実機で確認でき、
    ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebugが成功する
  依存:
    - BL-066

- id: BL-066
  区分: バグ修正
  タスク内容: ユーザー報告により、mobile/wear双方の実機で「アプリアイコンが2つ表示される」
    「mobileの設定画面が開けない」問題が判明した。原因は、mobile（baseモジュール）・
    wear（feature、android.hardware.type.watch限定配信）の両方のMainActivityがそれぞれ独自の
    LAUNCHER intent-filterを持っていたこと。baseモジュールは常にウォッチ側にも同梱される
    仕様（dist:conditionsはPlay Store配信時のfeature選択のみを制御し、baseは常に含まれる）
    のため、ウォッチ側では常にmobile.MainActivity（タップするとFEATURE_WATCH判定で即finish()
    するガード付き、事実上機能しない）とwear.MainActivity（「Sesami Wear」のプレースホルダー
    表示）の2アイコンが共存していた。またローカルのinstallDebug（bundletool経由のAPK Set
    生成）ではdist:conditionsが評価されないため、スマホ側にもwear.MainActivityのアイコンが
    重複表示されていた（既知の制約、BL-044参照）。「設定が開けない」報告は、スマホ側で
    誤ってwear.MainActivityのアイコン（設定機能を持たない）をタップしていたことが原因と推測
    される。対応として、wear.MainActivityからLAUNCHER intent-filterを除去し
    （android:exported="false"へ変更、本Activityを独立起動する必要はなくTile/Complicationが
    主要導線のため）、mobile.MainActivityのウォッチ実行時ガードをfinish()のみから、
    explicit Intent（クラス名文字列。mobileはwearへコンパイル時依存できないため）で
    wear.MainActivityへ委譲する形へ変更した（ActivityNotFoundException時はフォールバックで
    finish()のみ行う防御コード付き）。これによりmobile/wear双方の実機でアイコンが1つに統一され、
    スマホでは常にmobile.MainActivity（設定画面）、ウォッチでは常にwear.MainActivityが開く
  優先度: P1
  状態: 進行中
  担当: Claude Code
  完了条件: mobile/wear双方の実機でアプリアイコンが1つのみ表示され、スマホでは資格情報設定画面、
    ウォッチではwear.MainActivityの画面が正しく開くことを確認でき、
    ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebugが成功する
  依存: []

- id: BL-064
  区分: バグ修正
  タスク内容: ユーザー報告により、施錠/解錠コマンド成功後にTileの状態表示が更新されず、
    もう一度タップしないと最新状態が反映されない問題が判明した。原因は2点:
    (1) mobile側がコマンド成功時にDataItemを更新してもwear側のSesameResultListenerServiceは
    ハプティクス再生のみでTile/Complicationの再描画をトリガーしていなかった、
    (2) BL-061のTile表示時自動状態取得（requestStatus）が、コマンド実行直後の再描画時にも
    発動し、Sesame実機のモーター動作が完了する前のSesame API GET結果でDataItemの正しい状態を
    上書き（巻き戻し）してしまう可能性があった。対応として、SesameResultListenerServiceで
    コマンド結果受信時に即座にTile/Complicationの再描画をリクエストするようにし、
    SesameTileService/SesameComplicationDataSourceServiceのrequestStatus送信をDataItemが
    一定時間（30秒）以上古い場合のみに制限した。実機再検証でユーザーから
    「自動的に最新状態に切り替わってはいなさそうにみえる」と再度報告があり未解決と判明したため、
    原因切り分け用にSesameMessageListenerService（mobile）へLog.d呼び出しを追加した
    （コマンド受信・デバウンス判定・API実行結果・DataItem同期・結果送信の各段階）。
    次回実機操作時にlogcatで(a) mobile側コマンド処理の成否、(b) DataItem同期のタイミング、
    (c) wear側Tile再描画リクエストの発火有無、を突き合わせて真因を特定する
  優先度: P1
  状態: 進行中
  担当: Claude Code
  完了条件: コマンド成功後にTileが自動的に最新状態へ更新されることを実機で確認でき、
    ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebugが成功する
  依存: []

- id: BL-063
  区分: 機能追加
  タスク内容: ユーザー報告により、Tileのデザインが「何のデバイスを操作しようとしているか」
    「今どの状態か」「操作の分かりやすさ」「他のデバイスへの切り替え方法」の観点で不十分と
    判明した。3回にわたり対応した。1回目: (1) デバイス名（displayName）をTile上部に表示、
    (2) 状態表示に絵文字アイコンを追加、(3) 既存のactionLabel（未統合だった）を表示欄として
    統合、(4) Tile下部に「デバイスを変更」ボタンを追加。ユーザーから「テキストが中心に集まって
    いる」と指摘（原因: 状態表示Boxに幅・高さの明示指定がなく内容サイズにしか広がらなかった）。
    2回目: Box(全面)+Column(全面)で上部＝デバイス名/中央＝状態表示（拡大）/下部＝デバイス変更の
    3段構成へ再構成、背景色をタイル全面に敷いた。ユーザーから「左レイアウトの文字が画面に収まって
    いない、ステータス色は右側だけでいい、各領域を角丸の四角ボタンで表現したい」と再指摘
    （原因: 円形画面のセーフエリア（内接正方形）を考慮せずタイル端に要素を配置していたため、
    ラウンドベゼルでテキストが欠けていた）。3回目（現行）: タイルを左右2分割し、左列
    （デバイス名チップ・デバイス変更チップを`DimensionBuilders.weight(1f)`で均等な高さに分割、
    幅76dp固定）と右側の状態チップ（`DimensionBuilders.expand()`で残り全域）を、それぞれ独立した
    角丸背景（`ModifiersBuilders.Corner`、半径12dp）を持つ「チップ」として表現。状態色
    （施錠中=緑/解錠中=赤等）は右側チップの背景にのみ適用し、左側2チップは中立色
    （0xFF424242）にすることで領域とステータス色の意味を区別。タイル全体を端から12dp、
    チップ間を6dp内側へ寄せることでセーフエリアからのはみ出しを防止。detektの
    LongMethod/TooManyFunctions両方に抵触したため、buildLeftColumn/buildCommandClickableの
    抽出とbuildConfigurationLaunchActionの2箇所インライン化で関数数と行数のバランスを調整した。
    実機確認後、ユーザーから3点の追加指摘: (1) 角丸の一部がまだ見切れている→外周パディングを
    約1割増やす（12f→13f）、(2) 左側チップのテキスト色が黒のままで見えない→
    カラーデザインを考慮したテキスト色を設定する、(3) デバイス名タップで状態更新をユーザー
    契機でも実施できるようにする。対応として、
    core.tile.SesameTileContent.statusTextColorArgb（新規）で状態色背景に対するコントラスト
    確保（通信中の明るいアンバー背景のみ濃色0xFF212121、それ以外は白0xFFFFFFFF）、左側2チップは
    中立の白系テキスト色（CHIP_NEUTRAL_TEXT_COLOR_ARGB=0xFFFFFFFF）を全Textへ設定。
    新規wear.action.SesameStatusRefreshActivity（PATH_STATUS_REQUESTをFire-and-forgetで
    送信するのみの軽量Activity）を追加し、AndroidManifest.xmlへexported="true"で登録
    （BL-060の教訓）、デバイス名チップのクリックからLaunchActionで起動するようにした
  優先度: P1
  状態: 進行中
  担当: Claude Code
  完了条件: Tileにデバイス名・状態アイコン・操作ラベル・デバイス変更ボタンが左右2分割の
    角丸チップとして画面内に見切れず表示され、状態色が右チップのみに適用され、全テキストが
    背景色に対し十分なコントラストで視認でき、デバイス名タップで状態更新が行われることを
    実機で確認でき、ktlintCheck/detekt/lintDebug/testDebugUnitTest/assembleDebugが成功する
  依存: []

- id: BL-055
  区分: 人手検証
  タスク内容: BL-053/BL-054完了後、実機（Pixel Watch + 複数のSesame 5実機、3〜5台相当）で
    複数Tileインスタンスをそれぞれ異なるSesameデバイスに設定し、各Tileのロック状態表示・
    施錠/解錠操作・ハプティクスと、各Complicationの表示が正しく対象デバイスと対応することを確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 複数台のSesame実機に対し、それぞれ独立したTile/Complicationで誤りなく施錠/解錠操作と
    状態表示ができることを確認する
  依存:
    - BL-053
    - BL-054

- id: BL-042
  区分: 人手検証
  タスク内容: BL-041のリング寸法修正後、実機（スマホ・Pixel Watch）でwearのランチャーアイコンを
    目視確認し、コンプリケーション風リングがマスク形状で欠けずに表示されることを確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実機のランチャー・アプリ一覧でwearアイコンのリングが欠けずに表示されることを確認する
  依存:
    - BL-041

- id: BL-038
  区分: 人手検証
  タスク内容: BL-036/BL-037完了後、Google Play Console限定公開トラック（BL-034）へ統合後の
    AABをアップロードし、スマホへインストール後にペアリング済みPixel Watchへ自動的にwearアプリが
    プッシュインストールされることを実機で確認する
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: スマホへのインストールのみでPixel Watch側にもwearアプリが自動導入されることを確認する
  依存:
    - BL-036
    - BL-037
    - BL-034

- id: BL-032
  区分: 人手検証
  タスク内容: keytoolコマンド（BL-028で整備した署名設定に対応する形でREADME.mdへ記載する手順）で
    リリース署名用Keystoreを生成し、安全な場所（パスワードマネージャ等）に保管する。秘密鍵の生成・
    保管は自律ループ実行モードの対象外（rules/guardrails-unified.v1.md セクション12.5「実資格情報の
    取り扱い」参照）
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: Keystoreが生成され、BL-028で用意したlocal.properties経由の署名設定から参照できる
    状態になる
  依存:
    - BL-028

- id: BL-033
  区分: 人手検証
  タスク内容: BL-030で作成したプライバシーポリシーを公開可能なURLでホスティングし、Google Play
    ConsoleのData safety（データ安全性）セクションへ申告する
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: プライバシーポリシーURLがPlay Consoleに登録され、データ安全性の申告が完了する
  依存:
    - BL-030

- id: BL-034
  区分: 人手検証
  タスク内容: BL-032のKeystoreを使ってBL-029のリリース設定で署名付きAAB（mobile/wear）をビルドし、
    Google Play Consoleでアプリを新規登録（Play Console提出用の高解像度アイコン画像512x512 PNGの
    用意を含む）、限定公開（内部テストまたはクローズドテスト）トラックへアップロードし、
    テスターを登録する。実機でのスクリーンショット撮影もあわせて行う
  優先度: P3
  状態: 未着手
  担当: ユーザー
  完了条件: Google Play Consoleでテスタートラックにアップロードされ、テスターがインストールできる
  依存:
    - BL-011
    - BL-029
    - BL-030
    - BL-032
    - BL-033

- id: BL-010
  区分: 人手検証
  タスク内容: 実資格情報（uuid/secretKey/apikey）を用いてSesame APIの状態取得・施錠・解錠を疎通確認する。
    SesameStatus（batteryVoltage/position/CHSesame2Status。isInLockRange/isInUnlockRangeは
    CHSesame2Statusからの導出値）のフィールド構成が実際のレスポンスと一致するか、
    施錠/解錠コマンドの署名・ペイロード仕様（DESIGN.md記載、pysesame3参照で判明した推測）が
    実際に通るかもあわせて確認する（未確認事項、DESIGN.md参照）
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実際のSesame 5 + Hub 3に対してGET状態取得・POST施錠/解錠が成功し、
    SesameStatusのデコードエラーが発生しないことを確認する
  依存:
    - BL-003
    - BL-004

- id: BL-011
  区分: 人手検証
  タスク内容: Pixel Watch実機 + Sesame 5実機でTileの施錠/解錠操作・ハプティクス・Complication表示を確認する
  優先度: P2
  状態: 未着手
  担当: ユーザー
  完了条件: 実機で一連の操作が成功し、PLAN.mdのUX要件を満たすことを確認する
  依存:
    - BL-007
    - BL-008
    - BL-009
    - BL-010
    - BL-013
    - BL-014
    - BL-015
    - BL-016
    - BL-022
```
<!-- COPILOT_RECORDS:END -->

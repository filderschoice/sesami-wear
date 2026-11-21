package com.sesamiwear.wear.status

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.display.SesameStatusDetail
import com.sesamiwear.core.display.SesameTileActions
import com.sesamiwear.core.display.SesameTileContent
import java.time.ZoneId

/**
 * ウォッチのアプリ本体に出す、登録済みセサミの状態一覧の中身（BL-170）。
 *
 * Tileには入りきらない情報（電池残量・経路）の置き場として、アプリ本体を状態一覧にする。
 * **この画面から状態取得のリクエストは送らない。** 最後に同期された値をそのまま出すだけで、
 * Sesame Web APIのリクエスト回数を消費しない（BL-142の方針を維持する）。
 *
 * 円形画面では行が長いと行頭・行末が見切れるため（BL-114）、スマートフォンの1行表示
 * （`core.display.SesameDeviceStatusLine`）はそのまま使わず、**短い2行へ分ける**。
 * 1行あたりの目安は[MAX_LINE_CHARS]（`DeviceSelectionContent`と同じ根拠）。
 *
 * 各行はタップで操作できる（BL-254）。BL-295以降、行のタップは状態によらず（状態不明でも）常に
 * 施錠・解錠を選ぶ画面を開く（[TapAction.Choose]、[SesameTileActions.listTapDecision]）。表示と逆の
 * 操作に限らず、施錠中に施錠する・一部解錠のときに解錠することもできる。選ぶ画面で解錠を押したら
 * そこで実行する（確認は重ねない）。長押しの入口は作らない。
 *
 * 一覧の末尾には、タップしたときだけ状態取得を送る「更新」と、2台以上のときの「全台の鍵を操作」を置く
 * （BL-255、[footerChips]）。「全台の鍵を操作」は全デバイスの施錠・解錠を選ぶ画面を開く（BL-308。もとは1タップで送る
 * 「全施錠」だったが、操作パネルの上段（BL-307）・スマホのカードの「鍵を操作」と揃えた）。
 *
 * Android非依存のためユニットテスト対象。
 */
object SesameStatusListContent {
    /**
     * 1行に収めてよい全角換算の文字数の目安。最小構成の円形Wear OS端末（幅192dp）を想定する
     * （`com.sesamiwear.wear.ui.DeviceSelectionContent.MAX_LINE_CHARS`と同じ根拠）。
     */
    const val MAX_LINE_CHARS = 11

    /** 画面の見出し。 */
    const val TITLE = "セサミの状態"

    /** 一覧が空（まだ同期されていない・未登録）のときの案内。行ごとに意味が切れる位置で分ける。 */
    val EMPTY_LINES =
        listOf(
            "スマホで登録すると",
            "ここに状態が出ます",
        )

    /** [EMPTY_LINES]を改行で連結した表示用の文字列。 */
    val emptyMessage: String get() = EMPTY_LINES.joinToString(separator = "\n")

    /** 1台のときの末尾の状態取得チップの文言（BL-255）。 */
    const val REFRESH_CHIP_LABEL = "更新"

    /** 2台以上のときの末尾の状態取得チップの文言。全デバイスへ送ることを示す。 */
    const val REFRESH_ALL_CHIP_LABEL = "すべて更新"

    /** 2台以上のときの末尾の、全デバイスの施錠・解錠を選ぶ画面を開くチップの文言。操作パネルの上段と同じ言い方にする（BL-308）。 */
    const val MENU_ALL_CHIP_LABEL = "全台の鍵を操作"

    /**
     * 一覧の末尾に置くチップ（BL-255）。[targetUuid]は送信先（2台以上なら
     * [SesameWearProtocol.ALL_DEVICES_TARGET_UUID]）で、[action]はタップしたときの動作。
     */
    data class FooterChip(
        val label: String,
        val targetUuid: String,
        val action: TapAction,
    )

    /** 行をタップしたときの動作（BL-254）。 */
    sealed interface TapAction {
        /** 施錠・解錠を選ぶ画面を開く（行のタップ、BL-295。末尾の「全台の鍵を操作」、BL-308）。 */
        data object Choose : TapAction

        /** そのデバイスの状態取得を送る。 */
        data object Refresh : TapAction
    }

    /**
     * 1台分の表示。[displayName]が見出し、[statusLine]が施錠状態と電池、
     * [detailLine]が経路アイコン付きの最終取得時刻（または失敗の理由）、
     * [actionLabel]がタップしたときの操作（「タップで解錠」等）。
     * [state]は行の背景色（Tileの状態色）を決めるために持つ。
     */
    data class DeviceRow(
        val uuid: String,
        val displayName: String,
        val statusLine: String,
        val detailLine: String,
        val state: TileDisplayState,
        val tapAction: TapAction,
        val actionLabel: String,
    )

    /**
     * [devices]の各デバイスについて表示行を組み立てる。
     * [snapshotOf]は同期済みのスナップショットを返す関数（未取得ならnull）。
     */
    fun rows(
        devices: List<SesameDeviceSummary>,
        snapshotOf: (String) -> SesameStatusSnapshot?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DeviceRow> =
        devices.map { device ->
            val snapshot = snapshotOf(device.uuid)
            val state = displayStateOf(snapshot?.isLocked)
            DeviceRow(
                uuid = device.uuid,
                displayName = device.displayName.ifBlank { device.uuid },
                statusLine = statusLineOf(snapshot),
                detailLine =
                    SesameStatusDetail.compactLabel(
                        failure = snapshot?.lastFailure,
                        updatedAtEpochMillis = snapshot?.updatedAtEpochMillis,
                        zoneId = zoneId,
                        route = snapshot?.lastRoute,
                    ),
                state = state,
                tapAction = tapActionOf(),
                actionLabel = SesameTileContent.CHOOSE_ACTION_LABEL,
            )
        }

    /**
     * 一覧の末尾に置くチップ。0台なら何も置かない（案内文だけを出す）。
     * 1台なら「更新」だけ、2台以上なら「全台の鍵を操作」「すべて更新」の順に置く。
     * 一括操作を2台以上に限るのは、1台なら行のタップで足りるため（Tileの「全デバイス」と同じ条件）。
     */
    fun footerChips(devices: List<SesameDeviceSummary>): List<FooterChip> =
        when (devices.size) {
            0 -> emptyList()
            1 -> listOf(FooterChip(REFRESH_CHIP_LABEL, devices.single().uuid, TapAction.Refresh))
            else ->
                listOf(
                    FooterChip(MENU_ALL_CHIP_LABEL, SesameWearProtocol.ALL_DEVICES_TARGET_UUID, TapAction.Choose),
                    FooterChip(REFRESH_ALL_CHIP_LABEL, SesameWearProtocol.ALL_DEVICES_TARGET_UUID, TapAction.Refresh),
                )
        }

    /** 行のタップは状態によらず選ぶ画面を開く（BL-295）。 */
    fun tapActionOf(): TapAction =
        when (SesameTileActions.listTapDecision()) {
            SesameTileActions.TapDecision.OpenChooser -> TapAction.Choose
            SesameTileActions.TapDecision.None -> TapAction.Refresh
        }

    /**
     * 施錠状態と電池残量の行（「🔒施錠中 🔋85%」）。
     * 電池残量が未取得なら状態だけにする。文言・アイコンはTileと同じものを使い、
     * 同じ状態が別の言い方で出ないようにする。
     */
    private fun statusLineOf(snapshot: SesameStatusSnapshot?): String {
        val state = displayStateOf(snapshot?.isLocked)
        val base = "${SesameTileContent.statusIcon(state)}${SesameTileContent.statusLabel(state)}"
        return SesameTileContent.batteryLabel(snapshot?.batteryPercentage)?.let { "$base $it" } ?: base
    }

    /**
     * 施錠状態だけから表示状態を決める。
     *
     * Tileと違いスマートフォンとの接続状態は見ない。この画面は**最後に同期された値を見る**ためのもので、
     * 今つながっているかどうかは表示の意味を変えないため（つながっていなければ値が古くなるだけで、
     * それは最終取得時刻の行が示す）。
     */
    private fun displayStateOf(isLocked: Boolean?): TileDisplayState =
        when (isLocked) {
            true -> TileDisplayState.LOCKED
            false -> TileDisplayState.UNLOCKED
            null -> TileDisplayState.UNKNOWN
        }
}

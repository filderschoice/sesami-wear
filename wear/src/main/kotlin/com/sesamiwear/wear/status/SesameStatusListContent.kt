package com.sesamiwear.wear.status

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameStatusSnapshot
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.api.SesameCommand
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
 * 各行はタップで操作できる（BL-254）。提示するコマンドはTileと同じ規則
 * （[SesameTileActions]。施錠中→解錠、解錠中→施錠）で、解錠は呼び出し先の確認画面を挟む。
 * Tileでは操作なしとなる状態不明の行は、施錠・解錠のどちらとも決められないため、
 * タップでそのデバイスの状態取得を送る（[TapAction.Refresh]）。通信はタップしたときの1回だけ。
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

    /** 状態不明の行に添える操作の文言（[TapAction.Refresh]）。 */
    const val REFRESH_ACTION_LABEL = "タップで更新"

    /** 行をタップしたときの動作（BL-254）。 */
    sealed interface TapAction {
        /** [command]の操作画面を開く（解錠は確認画面を挟む）。 */
        data class Command(
            val command: SesameCommand,
        ) : TapAction

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
        nowEpochMillis: Long,
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
                        nowEpochMillis = nowEpochMillis,
                        zoneId = zoneId,
                        route = snapshot?.lastRoute,
                    ),
                state = state,
                tapAction = tapActionOf(state),
                actionLabel = SesameTileContent.actionLabel(state) ?: REFRESH_ACTION_LABEL,
            )
        }

    /** Tileと同じ規則でコマンドを決め、決まらない（状態不明）ときは状態取得にする。 */
    fun tapActionOf(state: TileDisplayState): TapAction =
        SesameTileActions.commandForState(state)?.let { TapAction.Command(it) } ?: TapAction.Refresh

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

package com.sesamiwear.core.display

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import com.sesamiwear.core.api.SesameCommand

/**
 * ウォッチの「操作専用タイル」（BL-298）の表示内容を決める。Android非依存のためユニットテスト対象。
 *
 * 画面は「上に大きな全施錠・全解錠、下に各デバイスの状態」。台をタップするとその台の施錠・解錠を選ぶ画面を開く。
 * 対象は常に登録済みの全デバイスで、タイルごとの対象デバイスの割り当ては持たない。
 *
 * **並べる行の上限は[MAX_ROWS]（円形192dpの画面で、全体ボタン1段と合わせて縦に3段・タップ領域48dp以上を
 * 確保できる最大数）。** 登録が[MAX_ROWS]台を超えるときは、最後の1行を「他N台」（アプリ本体の一覧を開く）へ
 * 置き換える。0台のときは行を作らず[build]がnullを返す（呼び出し側が案内を出す）。
 *
 * 状態は呼び出し側が渡す最後に同期された値だけで決める（状態取得のリクエストは増やさない、BL-142）。
 * スマホ未接続のときは、全施錠・全解錠・各行をタップ不可にする（既存タイルと同じ）。
 */
object SesameControlTileContent {
    /** 台の行と「他N台」の行を合わせた、縦に並べる行数の上限。 */
    const val MAX_ROWS = 2

    /** 0台のときに出す案内（行ごとに意味が切れる位置で分ける）。 */
    const val EMPTY_MESSAGE = "スマホでセサミを\n登録してください"

    /** [overflowCount]台ぶんの「他N台」の文言。 */
    fun overflowLabel(overflowCount: Int): String = "他${overflowCount}台"

    /** 台ごとの1行。[state]は背景色を、[statusText]は「🔒施錠中」のような状態の文言を決める。 */
    data class DeviceRow(
        val uuid: String,
        val name: String,
        val state: TileDisplayState,
        val statusText: String,
        val tappable: Boolean,
        val description: String,
    )

    /**
     * タイルの表示内容。[targetUuid]は全施錠・全解錠の送信先（2台以上なら全デバイスを表す特別値、1台ならその台）。
     * [overflowCount]が正なら、[rows]の後ろに「他N台」の行を1つ置く。
     */
    data class Content(
        val lockLabel: String,
        val unlockLabel: String,
        val targetUuid: String,
        val buttonsEnabled: Boolean,
        val lockDescription: String,
        val unlockDescription: String,
        val rows: List<DeviceRow>,
        val overflowCount: Int,
    ) {
        val overflowText: String? get() = overflowCount.takeIf { it > 0 }?.let(::overflowLabel)
    }

    /**
     * 表示内容を組み立てる。[devices]は登録順の登録済みデバイス、[isLockedOf]はデバイスごとの最後に同期された
     * 施錠状態（未取得ならnull）、[phoneConnected]はスマホとの接続の有無。[devices]が空ならnull。
     */
    fun build(
        devices: List<SesameDeviceSummary>,
        isLockedOf: (String) -> Boolean?,
        phoneConnected: Boolean,
    ): Content? {
        if (devices.isEmpty()) return null
        val isAll = devices.size >= SesameDeviceTargets.MIN_DEVICES_FOR_ALL_DEVICES_CHOICE
        val lockLabel = SesameTileActions.commandLabel(SesameCommand.LOCK, isAll)
        val unlockLabel = SesameTileActions.commandLabel(SesameCommand.UNLOCK, isAll)
        val visible = if (devices.size > MAX_ROWS) devices.take(MAX_ROWS - 1) else devices
        return Content(
            lockLabel = lockLabel,
            unlockLabel = unlockLabel,
            targetUuid = if (isAll) SesameWearProtocol.ALL_DEVICES_TARGET_UUID else devices.single().uuid,
            buttonsEnabled = phoneConnected,
            lockDescription = if (phoneConnected) "$lockLabel タップですぐに施錠" else "$lockLabel スマホ未接続",
            unlockDescription = if (phoneConnected) "$unlockLabel タップして確認へ" else "$unlockLabel スマホ未接続",
            rows = visible.map { rowOf(it, isLockedOf(it.uuid), phoneConnected) },
            overflowCount = devices.size - visible.size,
        )
    }

    private fun rowOf(
        device: SesameDeviceSummary,
        isLocked: Boolean?,
        phoneConnected: Boolean,
    ): DeviceRow {
        val state = TileDisplayStateResolver.resolve(phoneConnected, false, isLocked)
        val name = device.displayName.ifBlank { device.uuid }
        val label = SesameTileContent.statusLabel(state)
        // 行のタップは状態によらず（状態不明でも）選ぶ画面を開く。操作できないのはスマホ未接続のときだけ。
        val tappable = state != TileDisplayState.DISCONNECTED
        return DeviceRow(
            uuid = device.uuid,
            name = name,
            state = state,
            statusText = "${SesameTileContent.statusIcon(state)}$label",
            tappable = tappable,
            description = if (tappable) "$name $label タップして施錠・解錠を選ぶ" else "$name $label",
        )
    }
}

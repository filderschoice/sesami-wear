package com.sesamiwear.core.display

import com.sesamiwear.core.SesameDeviceSummary
import com.sesamiwear.core.SesameWearProtocol
import com.sesamiwear.core.TileDisplayState
import com.sesamiwear.core.TileDisplayStateResolver
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * ウォッチの「操作専用タイル」（BL-298）の表示内容を決める。Android非依存のためユニットテスト対象。
 *
 * 画面は「上段に大きな『全台の鍵を操作』（1台なら『鍵を操作』）のボタン1つ、その下に台ごとの状態チップを横に並べる」。
 * 上段のボタンをタップすると全台（1台ならその台）の、台のチップをタップするとその台の、施錠・解錠を選ぶ画面を開く。
 * 上段はもとは「全施錠」（タップ1回で実行）と「全解錠」（選ぶ画面）の2ボタンだったが、スマホのカードの「鍵を操作」と
 * 考え方を揃え、どちらも選ぶ画面で押して実行する形へまとめた（BL-307、2026-11-21のユーザー判断。タイルからタップ1回で
 * 施錠する経路は無くなる。ウォッチのアプリの一覧の末尾の「全施錠」は従来どおり1タップ）。対象は常に登録済みの全デバイスで、タイルごとの対象デバイスの割り当ては持たない。
 *
 * **寸法の決め方（円形[CIRCLE_DIAMETER_DP]dpの画面）。** 各段の高さは[TIER_HEIGHT_DP]（タップ領域の最小値）、
 * 段間は[GAP_DP]。段は縦に積み、画面の中心へ揃える。円の端で見切れないよう、段ごとの可用幅は
 * 「その段の中心から遠い側の辺での弦の長さ − 左右の安全余白」とする（[tierWidthDp]。上下の端の段ほど狭い）。
 * 1チップの最小幅は[MIN_CHIP_WIDTH_DP]で、1段に並べられるチップ数は可用幅から決まる（[chipRowCapacities]）。
 * チップが1段（全体ボタンと合わせて2段）に収まるときは2段、収まらないときは3段（チップ2段）で、
 * 3段のときに並べられるチップ数が上限[MAX_CHIPS]。登録が[MAX_CHIPS]台を超えるときは、最後のチップを
 * 「他N台」（アプリ本体の一覧を開く）へ置き換える。0台のときは[build]がnullを返す（呼び出し側が案内を出す）。
 *
 * 状態は呼び出し側が渡す最後に同期された値だけで決める（状態取得のリクエストは増やさない、BL-142）。
 * スマホ未接続のときは、上段のボタン・各チップをタップ不可にする（既存タイルと同じ）。
 */
object SesameControlTileContent {
    /** 円形の画面の直径（dp）。 */
    const val CIRCLE_DIAMETER_DP = 192

    /** 1段（全体ボタンの段・チップの段）の高さ（dp）。タップ領域の最小値48dp。 */
    const val TIER_HEIGHT_DP = 48

    /** 段と段の間隔、およびチップ同士の間隔（dp）。 */
    const val GAP_DP = 4

    /** 円の弦の両端から内側へ空ける安全余白（dp。角丸のチップが円の端で欠けないため）。 */
    const val EDGE_MARGIN_DP = 4

    /** チップの最小幅（dp）。 */
    const val MIN_CHIP_WIDTH_DP = 48

    /** チップ名の表示幅の上限（全角を2、半角を1と数える）。 */
    const val MAX_NAME_UNITS = 8

    /** 上段のボタンの文言（2台以上）。スマホのカードの「鍵を操作」に合わせる（BL-307）。 */
    const val ALL_MENU_LABEL = "全台の鍵を操作"

    /** 上段のボタンの文言（1台）。 */
    const val SINGLE_MENU_LABEL = "鍵を操作"

    /** 0台のときに出す案内（行ごとに意味が切れる位置で分ける）。 */
    const val EMPTY_MESSAGE = "スマホでセサミを\n登録してください"

    private const val TWO_TIER_COUNT = 2
    private const val THREE_TIER_COUNT = 3
    private const val ASCII_MAX = 0x7F
    private val HALFWIDTH_KANA_RANGE = 0xFF61..0xFF9F
    private const val ELLIPSIS = "…"
    private const val ELLIPSIS_UNITS = 2

    /** [tierCount]段を縦に中央揃えしたときの、[tierIndex]段目（0始まり）の可用幅（dp）。 */
    fun tierWidthDp(
        tierCount: Int,
        tierIndex: Int,
    ): Int {
        val total = tierCount * TIER_HEIGHT_DP + (tierCount - 1) * GAP_DP
        val top = -total / 2.0 + tierIndex * (TIER_HEIGHT_DP + GAP_DP)
        val farthest = max(abs(top), abs(top + TIER_HEIGHT_DP))
        val radius = CIRCLE_DIAMETER_DP / 2.0
        val chord = 2 * sqrt(radius * radius - farthest * farthest)
        return floor(chord).toInt() - 2 * EDGE_MARGIN_DP
    }

    /** 幅[widthDp]の段に並べられるチップ数（最小幅[MIN_CHIP_WIDTH_DP]・間隔[GAP_DP]で割り付ける）。 */
    fun chipsPerRow(widthDp: Int): Int = (widthDp + GAP_DP) / (MIN_CHIP_WIDTH_DP + GAP_DP)

    /** [tierCount]段のとき、全体ボタンの段より下のチップの各段に並べられるチップ数。 */
    fun chipRowCapacities(tierCount: Int): List<Int> =
        (1 until tierCount).map { chipsPerRow(tierWidthDp(tierCount, it)) }

    /** チップが1段で済む（全体ボタンと合わせて2段の）ときの最大数。 */
    val singleRowCapacity: Int = chipRowCapacities(TWO_TIER_COUNT).sum()

    /** 並べるチップ（「他N台」を含む）の上限。3段構成のときの、チップの各段の最大数の合計。 */
    val maxChips: Int = chipRowCapacities(THREE_TIER_COUNT).sum()

    /** [overflowCount]台ぶんの「他N台」の文言。 */
    fun overflowLabel(overflowCount: Int): String = "他${overflowCount}台"

    /**
     * チップ名を[MAX_NAME_UNITS]の幅に収める。収まらないときは先頭を残して末尾を「…」にする。
     * コードポイント単位で数えるので、絵文字などのサロゲートペアを途中で切らない。
     */
    fun shortName(name: String): String {
        val codePoints = name.codePoints().toArray()
        if (codePoints.sumOf { unitsOf(it) } <= MAX_NAME_UNITS) return name
        val sb = StringBuilder()
        var used = 0
        for (cp in codePoints) {
            val w = unitsOf(cp)
            if (used + w > MAX_NAME_UNITS - ELLIPSIS_UNITS) break
            sb.appendCodePoint(cp)
            used += w
        }
        return sb.toString() + ELLIPSIS
    }

    /** 表示幅。ASCIIと半角カナ（U+FF61〜U+FF9F）は1、それ以外（全角・絵文字）は2。 */
    private fun unitsOf(codePoint: Int): Int = if (codePoint <= ASCII_MAX || codePoint in HALFWIDTH_KANA_RANGE) 1 else 2

    /**
     * 台ごとのチップ。[state]は背景色を、[icon]は状態アイコン（「🔒」等）を決める。[name]は省略済みで、
     * 読み上げ（[description]）は省略前の名前と状態の文言を使う。
     */
    data class DeviceChip(
        val uuid: String,
        val name: String,
        val state: TileDisplayState,
        val icon: String,
        val tappable: Boolean,
        val description: String,
    )

    /** チップの1段。[widthDp]はこの段の可用幅（円の弦で決まる）。[overflowText]があれば末尾へ「他N台」のチップを置く。 */
    data class ChipRow(
        val widthDp: Int,
        val chips: List<DeviceChip>,
        val overflowText: String?,
    )

    /**
     * タイルの表示内容。[menuLabel]は上段のボタンの文言、[targetUuid]はそのボタンで開く選ぶ画面の対象
     * （2台以上なら全デバイスを表す特別値、1台ならその台）。[buttonRowWidthDp]は上段の可用幅。[chipRows]は上から順のチップの段（1段または2段）。
     * [overflowCount]が正なら、最後の段の末尾が「他N台」のチップになる。
     */
    data class Content(
        val menuLabel: String,
        val targetUuid: String,
        val buttonsEnabled: Boolean,
        val menuDescription: String,
        val buttonRowWidthDp: Int,
        val chipRows: List<ChipRow>,
        val overflowCount: Int,
    ) {
        /** 全チップ（「他N台」を除く）。 */
        val chips: List<DeviceChip> get() = chipRows.flatMap { it.chips }
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
        val menuLabel = if (isAll) ALL_MENU_LABEL else SINGLE_MENU_LABEL
        val slots = minOf(devices.size, maxChips)
        val tierCount = if (slots <= singleRowCapacity) TWO_TIER_COUNT else THREE_TIER_COUNT
        // 登録が上限を超えるときは、最後のスロットを「他N台」に使う。
        val visible = if (devices.size > maxChips) devices.take(maxChips - 1) else devices
        val overflowCount = devices.size - visible.size
        val chips = visible.map { chipOf(it, isLockedOf(it.uuid), phoneConnected) }
        return Content(
            menuLabel = menuLabel,
            targetUuid = if (isAll) SesameWearProtocol.ALL_DEVICES_TARGET_UUID else devices.single().uuid,
            buttonsEnabled = phoneConnected,
            menuDescription = if (phoneConnected) "$menuLabel タップして施錠・解錠を選ぶ" else "$menuLabel スマホ未接続",
            buttonRowWidthDp = tierWidthDp(tierCount, 0),
            chipRows = layoutRows(chips, slots, tierCount, overflowCount),
            overflowCount = overflowCount,
        )
    }

    /** [slots]個のスロット（チップ＋「他N台」）を、上の段が多めになるよう均等に割り振る。 */
    private fun layoutRows(
        chips: List<DeviceChip>,
        slots: Int,
        tierCount: Int,
        overflowCount: Int,
    ): List<ChipRow> {
        val rowCount = tierCount - 1
        var taken = 0
        return (0 until rowCount).map { index ->
            val size = slots / rowCount + if (index < slots % rowCount) 1 else 0
            val hasOverflow = index == rowCount - 1 && overflowCount > 0
            val chipCount = if (hasOverflow) size - 1 else size
            val rowChips = chips.subList(taken, taken + chipCount)
            taken += chipCount
            ChipRow(
                widthDp = tierWidthDp(tierCount, index + 1),
                chips = rowChips,
                overflowText = if (hasOverflow) overflowLabel(overflowCount) else null,
            )
        }
    }

    private fun chipOf(
        device: SesameDeviceSummary,
        isLocked: Boolean?,
        phoneConnected: Boolean,
    ): DeviceChip {
        val state = TileDisplayStateResolver.resolve(phoneConnected, false, isLocked)
        val name = device.displayName.ifBlank { device.uuid }
        val label = SesameTileContent.statusLabel(state)
        // チップのタップは状態によらず（状態不明でも）選ぶ画面を開く。操作できないのはスマホ未接続のときだけ。
        val tappable = state != TileDisplayState.DISCONNECTED
        return DeviceChip(
            uuid = device.uuid,
            name = shortName(name),
            state = state,
            icon = SesameTileContent.statusIcon(state),
            tappable = tappable,
            description = if (tappable) "$name $label タップして施錠・解錠を選ぶ" else "$name $label",
        )
    }
}

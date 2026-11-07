package com.sesamiwear.mobile.radar

import kotlin.math.cos
import kotlin.math.hypot

/**
 * レーダー上でタップできる1台ぶんの領域（ブリップと名前ラベルを合わせた四角、BL-283）。[key]は台を表す識別子。
 */
internal data class RadarTarget(
    val key: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** ブリップの位置（中心）と半径。 */
internal data class BlipShape(
    val x: Float,
    val y: Float,
    val radius: Float,
)

/** 名前ラベルの大きさと、ブリップの中心からラベルまでの間隔。 */
internal data class LabelBox(
    val width: Float,
    val height: Float,
    val gap: Float,
)

/**
 * レーダーのタップ判定とラベルの置き場所（BL-283）。描画（[BleRadar]）とタップの判定で同じ置き場所を使うため、
 * Android非依存の計算として切り出し、単体テストで検証する。
 */
internal object RadarHitTest {
    /**
     * ラベルの左上の位置。ブリップから離れる側（向きの余弦が正なら右、負なら左）へ置き、縦はブリップの高さに揃える。
     */
    fun labelOrigin(
        blip: BlipShape,
        angle: Float,
        label: LabelBox,
    ): Pair<Float, Float> {
        val x = if (cos(angle) >= 0f) blip.x + label.gap else blip.x - label.gap - label.width
        return x to (blip.y - label.height / 2f)
    }

    /**
     * 複数台を並べるときの、[index]番目（全[count]台）の向き（ラジアン）。**全周を均等に分け**、ブリップと名前ラベルが
     * 重ならないようにする（向きがuuidのハッシュだと、近い値の台でラベルが重なった。BL-283の実機確認）。
     * 1台目は右下（30度）から始める（上の題字とぶつからないように）。向きに意味は無い（電波からは向きが分からない）。
     */
    fun evenBearing(
        index: Int,
        count: Int,
    ): Float = (START_BEARING_RADIANS + index * FULL_TURN_RADIANS / maxOf(count, 1)).toFloat()

    private const val FULL_TURN_RADIANS = 2 * Math.PI
    private const val START_BEARING_RADIANS = Math.PI / 6

    /** ブリップと、ラベル（無ければnull）の四角を合わせたタップ領域。 */
    fun targetOf(
        key: String,
        blip: BlipShape,
        angle: Float,
        label: LabelBox?,
    ): RadarTarget {
        var left = blip.x - blip.radius
        var right = blip.x + blip.radius
        var top = blip.y - blip.radius
        var bottom = blip.y + blip.radius
        if (label != null) {
            val (labelX, labelY) = labelOrigin(blip, angle, label)
            left = minOf(left, labelX)
            right = maxOf(right, labelX + label.width)
            top = minOf(top, labelY)
            bottom = maxOf(bottom, labelY + label.height)
        }
        return RadarTarget(key, left, top, right, bottom)
    }

    /**
     * タップ位置（[x], [y]）にある台の識別子。各領域を[slop]だけ広げて判定し、複数に当たったら中心が近い方を選ぶ。
     * どこにも当たらなければnull。
     */
    fun find(
        x: Float,
        y: Float,
        targets: List<RadarTarget>,
        slop: Float,
    ): String? =
        targets
            .filter { x in (it.left - slop)..(it.right + slop) && y in (it.top - slop)..(it.bottom + slop) }
            .minByOrNull { hypot(x - it.centerX, y - it.centerY) }
            ?.key
}

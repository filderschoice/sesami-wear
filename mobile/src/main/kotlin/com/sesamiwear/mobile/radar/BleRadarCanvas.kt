package com.sesamiwear.mobile.radar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.core.ble.SesameBleProximity
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * レーダー表示（BL-272）。中心がスマホ、輪が1m / 3m / 5m / 10m、掃引線が回り、
 * 対象のSesameが捉えられていれば距離の位置へブリップを置く。
 *
 * ブリップの向きは電波からは分からないため、**向きに意味は無い**。デバイスごとに同じ向きへ
 * 固定し（[bearingOf]）、距離の変化だけが動きとして見えるようにする。
 * 見えていない（探索中・圏外）ときはブリップを出さず、掃引線だけが回る。
 * 画面はAndroid依存の描画のみのためユニットテスト対象外（距離の換算はcoreでテスト済み）。
 */
@Composable
internal fun BleRadar(
    snapshot: SesameBleProbeSnapshot,
    deviceUuid: String,
    modifier: Modifier = Modifier,
) = BleRadar(listOf(RadarBlip(deviceUuid, null, snapshot)), "レーダー。" + snapshot.summaryLine(), modifier)

/**
 * 複数台を1枚のレーダーへ置く（BL-280、「まとめて接続確認」）。ブリップは名前つきで、台ごとに固定した向きへ置く
 * （向きに意味は無い）。見えていない台はブリップを出さない。[description]は読み上げ用の説明。
 * [RadarBlip.tappable]な台のブリップと名前ラベルをタップすると、その台の識別子を[onBlipClick]へ渡す
 * （BL-283、ダイヤル操作へ移るため）。
 */
@Composable
internal fun BleRadar(
    blips: List<RadarBlip>,
    description: String,
    modifier: Modifier = Modifier,
    onBlipClick: (String) -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN_DEGREES,
        animationSpec = infiniteRepeatable(tween(SWEEP_MILLIS, easing = LinearEasing)),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Restart),
        label = "pulse",
    )
    val textMeasurer = rememberTextMeasurer()
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sweepColor = MaterialTheme.colorScheme.primary
    val blipColors = blips.map { blipColorOf(it.snapshot) }
    val currentBlips by rememberUpdatedState(blips)
    val currentOnClick by rememberUpdatedState(onBlipClick)
    val tapModifier =
        Modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f - RING_MARGIN_DP.dp.toPx()
                val targets =
                    currentBlips.filter { it.tappable }.mapNotNull { blipTarget(it, center, radius, textMeasurer) }
                RadarHitTest.find(tap.x, tap.y, targets, TAP_SLOP_DP.dp.toPx())?.let(currentOnClick)
            }
        }
    Canvas(modifier = modifier.aspectRatio(1f).semantics { contentDescription = description }.then(tapModifier)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - RING_MARGIN_DP.dp.toPx()
        drawRings(center, radius, ringColor, labelColor, textMeasurer)
        drawSweep(center, radius, sweepAngle, sweepColor)
        drawCircle(labelColor, radius = CENTER_DOT_DP.dp.toPx(), center = center)
        blips.forEachIndexed { index, blip ->
            val at = blipPosition(blip, center, radius) ?: return@forEachIndexed
            drawBlip(at, blipColors[index], pulse)
            blip.label?.let { drawBlipLabel(it, at, blip.bearing, textMeasurer, labelColor) }
        }
    }
}

/** 回転する掃引線と、その後ろへ薄れていく扇形。 */
private fun DrawScope.drawSweep(
    center: Offset,
    radius: Float,
    sweepAngle: Float,
    sweepColor: Color,
) {
    rotate(sweepAngle, center) {
        drawCircle(
            brush =
                Brush.sweepGradient(
                    0f to Color.Transparent,
                    SWEEP_TAIL_STOP to Color.Transparent,
                    1f to sweepColor.copy(alpha = SWEEP_ALPHA),
                    center = center,
                ),
            radius = radius,
            center = center,
        )
        drawLine(sweepColor, center, Offset(center.x + radius, center.y), strokeWidth = SWEEP_LINE_DP.dp.toPx())
    }
}

/** [blip]の画面上の位置。電波を捉えていなければnull。向きはデバイスごとに固定した値（電波の向きではない）。 */
private fun blipPosition(
    blip: RadarBlip,
    center: Offset,
    radius: Float,
): Offset? {
    val fraction = blip.snapshot.radarFraction ?: return null
    val angle = blip.bearing
    return Offset(center.x + radius * fraction * cos(angle), center.y + radius * fraction * sin(angle))
}

/** ブリップの横に名前を描く。置き場所は[RadarHitTest.labelOrigin]（タップ判定と共有）。 */
private fun DrawScope.drawBlipLabel(
    label: String,
    at: Offset,
    angle: Float,
    textMeasurer: TextMeasurer,
    labelColor: Color,
) {
    val text =
        textMeasurer.measure(label, androidx.compose.ui.text.TextStyle(color = labelColor, fontSize = NAME_SP.sp))
    val box = LabelBox(text.size.width.toFloat(), text.size.height.toFloat(), (BLIP_DP + LABEL_PADDING_DP).dp.toPx())
    val (x, y) = RadarHitTest.labelOrigin(BlipShape(at.x, at.y, BLIP_DP.dp.toPx()), angle, box)
    drawText(text, topLeft = Offset(x, y))
}

/** [blip]のタップ領域（ブリップと名前ラベルを合わせた四角）。電波を捉えていなければnull。 */
private fun Density.blipTarget(
    blip: RadarBlip,
    center: Offset,
    radius: Float,
    textMeasurer: TextMeasurer,
): RadarTarget? {
    val at = blipPosition(blip, center, radius) ?: return null
    val gap = (BLIP_DP + LABEL_PADDING_DP).dp.toPx()
    val box =
        blip.label?.let {
            val size = textMeasurer.measure(it, androidx.compose.ui.text.TextStyle(fontSize = NAME_SP.sp)).size
            LabelBox(size.width.toFloat(), size.height.toFloat(), gap)
        }
    return RadarHitTest.targetOf(
        blip.deviceUuid,
        BlipShape(at.x, at.y, BLIP_DP.dp.toPx()),
        blip.bearing,
        box,
    )
}

private fun DrawScope.drawRings(
    center: Offset,
    radius: Float,
    ringColor: Color,
    labelColor: Color,
    textMeasurer: TextMeasurer,
) {
    val rings = SesameBleProximity.RING_DISTANCES_METERS
    rings.forEachIndexed { index, meters ->
        val ringRadius = radius * (index + 1) / rings.size
        drawCircle(ringColor, radius = ringRadius, center = center, style = Stroke(width = RING_STROKE_DP.dp.toPx()))
        val label =
            textMeasurer.measure(
                "${meters}m",
                androidx.compose.ui.text.TextStyle(color = labelColor, fontSize = LABEL_SP.sp),
            )
        // 輪の上端の少し内側へ置く。ブリップと重なっても読めるよう小さく淡くする。
        drawText(label, topLeft = Offset(center.x + LABEL_PADDING_DP.dp.toPx(), center.y - ringRadius))
    }
}

private fun DrawScope.drawBlip(
    at: Offset,
    color: Color,
    pulse: Float,
) {
    val base = BLIP_DP.dp.toPx()
    // 広がりながら薄くなる輪で「今ここにいる」ことを示す。
    drawCircle(color.copy(alpha = (1f - pulse) * PULSE_ALPHA), radius = base * (1f + pulse * PULSE_GROWTH), center = at)
    drawCircle(color, radius = base, center = at)
}

/** ブリップの色。近いほど緑、遠いほど橙、接続の失敗は赤。 */
@Composable
private fun blipColorOf(snapshot: SesameBleProbeSnapshot): Color =
    when {
        snapshot.stage == SesameBleProbeStage.FAILED -> Color(COLOR_BAD)
        snapshot.level == SesameBleProximity.Level.VERY_NEAR || snapshot.level == SesameBleProximity.Level.NEAR ->
            Color(COLOR_GOOD)
        snapshot.level == SesameBleProximity.Level.MEDIUM -> Color(COLOR_WARN)
        else -> MaterialTheme.colorScheme.outline
    }

/** デバイスごとに固定したブリップの向き（ラジアン）。電波の向きではなく、見た目を安定させるためだけの値。 */
internal fun bearingOf(deviceUuid: String): Float {
    val hash = deviceUuid.fold(0) { acc, c -> acc * HASH_MULTIPLIER + c.code }
    return (hash.toUInt().toLong() % BEARING_STEPS) / BEARING_STEPS.toFloat() * TWO_PI
}

private const val FULL_TURN_DEGREES = 360f
private const val SWEEP_MILLIS = 3_000
private const val PULSE_MILLIS = 1_600
private const val SWEEP_TAIL_STOP = 0.75f
private const val SWEEP_ALPHA = 0.45f
private const val SWEEP_LINE_DP = 2
private const val RING_MARGIN_DP = 8
private const val RING_STROKE_DP = 1
private const val CENTER_DOT_DP = 4
private const val BLIP_DP = 9
private const val PULSE_ALPHA = 0.5f
private const val PULSE_GROWTH = 2.2f
private const val LABEL_SP = 10
private const val NAME_SP = 12

/** タップ領域を広げる余白（dp）。指で小さなブリップを押しやすくする。 */
private const val TAP_SLOP_DP = 14
private const val LABEL_PADDING_DP = 4
private const val COLOR_GOOD = 0xFF2E7D32
private const val COLOR_WARN = 0xFFEF6C00
private const val COLOR_BAD = 0xFFC62828
private const val HASH_MULTIPLIER = 31
private const val BEARING_STEPS = 360L
private const val TWO_PI = (2 * Math.PI).toFloat()

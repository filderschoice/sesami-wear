package com.sesamiwear.wear.radar

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import com.sesamiwear.core.ble.SesameBleProbeSnapshot
import com.sesamiwear.core.ble.SesameBleProbeStage
import com.sesamiwear.core.ble.SesameBleProximity
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ウォッチ用のレーダー表示（BL-273）。円形の小さな画面向けに、スマートフォン版から輪のラベルを
 * 省き（読めない大きさのため）、輪・掃引線・ブリップだけを描く。輪の距離（1m / 3m / 5m / 10m）と
 * 距離の換算はスマートフォン版と同じ（`core.ble.SesameBleProximity`）。
 *
 * ブリップの向きに意味は無い（電波からは向きが分からない）ため、デバイスごとに固定した向きへ置く。
 * Android依存の描画のみのためユニットテスト対象外。
 */
@Composable
internal fun BleRadar(
    snapshot: SesameBleProbeSnapshot,
    deviceUuid: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    // 端末の「アニメーションを削除」（アニメーション スケール0）がオンのときは、Composeの無限アニメーションが
    // 進まず、掃引線が一方向で止まる。止まった扇形は「その方向に何かある」と誤解されるため、描かない（2026-11-09）。
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN_DEGREES,
        animationSpec = infiniteRepeatable(tween(SWEEP_MILLIS, easing = LinearEasing)),
        label = "sweep",
    )
    val ringColor = Color.White.copy(alpha = RING_ALPHA)
    val sweepColor = MaterialTheme.colors.primary
    val blipColor = blipColorOf(snapshot)
    val description = "レーダー。" + snapshot.summaryLine()
    Canvas(modifier = modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - RING_MARGIN_DP.dp.toPx()
        val rings = SesameBleProximity.RING_DISTANCES_METERS.size
        for (index in 1..rings) {
            drawCircle(
                ringColor,
                radius = radius * index / rings,
                center = center,
                style = Stroke(width = RING_STROKE_DP.dp.toPx()),
            )
        }
        if (animationsEnabled) {
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
            }
        }
        drawCircle(Color.White, radius = CENTER_DOT_DP.dp.toPx(), center = center)
        snapshot.radarFraction?.let { fraction ->
            val angle = bearingOf(deviceUuid)
            val at = Offset(center.x + radius * fraction * cos(angle), center.y + radius * fraction * sin(angle))
            drawCircle(blipColor.copy(alpha = BLIP_HALO_ALPHA), radius = BLIP_DP.dp.toPx() * 2f, center = at)
            drawCircle(blipColor, radius = BLIP_DP.dp.toPx(), center = at)
        }
    }
}

/** ブリップの色。スマートフォン版と同じ規則（近い＝緑、やや遠い＝橙、接続失敗＝赤、それ以外は灰）。 */
private fun blipColorOf(snapshot: SesameBleProbeSnapshot): Color =
    when {
        snapshot.stage == SesameBleProbeStage.FAILED -> Color(COLOR_BAD)
        snapshot.level == SesameBleProximity.Level.VERY_NEAR || snapshot.level == SesameBleProximity.Level.NEAR ->
            Color(COLOR_GOOD)
        snapshot.level == SesameBleProximity.Level.MEDIUM -> Color(COLOR_WARN)
        else -> Color.Gray
    }

/** デバイスごとに固定したブリップの向き（ラジアン）。スマートフォン版と同じ値になるよう同じ式を使う。 */
internal fun bearingOf(deviceUuid: String): Float {
    val hash = deviceUuid.fold(0) { acc, c -> acc * HASH_MULTIPLIER + c.code }
    return (hash.toUInt().toLong() % BEARING_STEPS) / BEARING_STEPS.toFloat() * TWO_PI
}

private const val FULL_TURN_DEGREES = 360f
private const val SWEEP_MILLIS = 3_000
private const val SWEEP_TAIL_STOP = 0.75f
private const val SWEEP_ALPHA = 0.5f
private const val RING_ALPHA = 0.35f
private const val RING_MARGIN_DP = 2
private const val RING_STROKE_DP = 1
private const val CENTER_DOT_DP = 3
private const val BLIP_DP = 5
private const val BLIP_HALO_ALPHA = 0.3f
private const val COLOR_GOOD = 0xFF4CAF50
private const val COLOR_WARN = 0xFFFFA726
private const val COLOR_BAD = 0xFFEF5350
private const val HASH_MULTIPLIER = 31
private const val BEARING_STEPS = 360L
private const val TWO_PI = (2 * Math.PI).toFloat()

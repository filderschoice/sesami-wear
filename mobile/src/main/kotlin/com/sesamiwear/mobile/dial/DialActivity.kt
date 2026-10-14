package com.sesamiwear.mobile.dial

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sesamiwear.core.SesameCredentials
import com.sesamiwear.core.api.SesameCommand
import com.sesamiwear.mobile.state.SesameDeviceStores
import com.sesamiwear.mobile.ui.SesameTheme
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ダイヤル操作画面（BL-243、スマートフォンのみ）。デバイスカードの「ダイヤル操作」から開く。
 *
 * Bluetoothで接続してサムターンの角度を円形のダイヤルへ表示し、ダイヤルを半周以上なぞると
 * そのときの状態の反対（施錠中なら解錠、解錠中なら施錠）をBluetoothで送る（判定は[DialSweep]）。
 * Bluetoothで届かなければ何もしない（[DialController]）。
 * 画面の組み立てだけのAndroid依存クラスのためユニットテスト対象外。
 */
class DialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uuid = intent.getStringExtra(EXTRA_DEVICE_UUID)
        val credentials = uuid?.let { SesameDeviceStores.credentials(this).loadAll().find { it.uuid == uuid } }
        if (credentials == null) {
            finish()
            return
        }
        val controller = DialController(this, credentials)
        setContent {
            SesameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DialScreen(credentials = credentials, controller = controller, onClose = ::finish)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_DEVICE_UUID = "device_uuid"

        fun createIntent(
            context: Context,
            deviceUuid: String,
        ): Intent = Intent(context, DialActivity::class.java).putExtra(EXTRA_DEVICE_UUID, deviceUuid)
    }
}

@Composable
private fun DialScreen(
    credentials: SesameCredentials,
    controller: DialController,
    onClose: () -> Unit,
) {
    var state by remember { mutableStateOf<DialUiState>(DialUiState.Connecting) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state = controller.load() }

    fun reload() {
        state = DialUiState.Connecting
        scope.launch { state = controller.load() }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = credentials.displayName.ifBlank { DEFAULT_NAME }, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = SUBTITLE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (val current = state) {
                DialUiState.Connecting -> Progress(CONNECTING_LABEL)
                is DialUiState.Running -> Progress(current.label)
                is DialUiState.Unavailable ->
                    Text(
                        text = current.message,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                is DialUiState.Ready ->
                    ReadyDial(
                        ready = current,
                        onTriggered = { command ->
                            state = DialUiState.Running(command)
                            scope.launch { state = controller.run(command) }
                        },
                    )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text(text = "閉じる") }
            Button(
                onClick = ::reload,
                enabled = state !is DialUiState.Connecting && state !is DialUiState.Running,
            ) { Text(text = "もう一度読み込む") }
        }
    }
}

@Composable
private fun Progress(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReadyDial(
    ready: DialUiState.Ready,
    onTriggered: (SesameCommand) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text =
                buildString {
                    append(ready.stateLabel).append(" ・ 角度").append(ready.position)
                    ready.batteryPercentage?.let { append(" ・ 電池").append(it).append('%') }
                },
            style = MaterialTheme.typography.titleMedium,
        )
        Dial(ready = ready, onTriggered = onTriggered)
        Text(text = ready.instruction, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        ready.lastResult?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 円形のダイヤル。外周の輪、現在の角度を指す針、なぞった量を示す円弧を描く。
 * 状態が施錠・解錠のどちらでもない（[DialUiState.Ready.command]がnull）ときは、なぞっても何も送らない。
 */
@Composable
private fun Dial(
    ready: DialUiState.Ready,
    onTriggered: (SesameCommand) -> Unit,
) {
    val sweep = remember { DialSweep() }
    var swept by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val pointerColor = MaterialTheme.colorScheme.onSurface
    val command = ready.command
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth(DIAL_WIDTH_FRACTION)
                .aspectRatio(1f)
                .semantics {
                    contentDescription = "ダイヤル。${ready.stateLabel}、角度${ready.position}。${ready.instruction}"
                }
                .pointerInput(command) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    detectDragGestures(
                        onDragStart = { sweep.start(it.x - center.x, it.y - center.y) },
                        onDrag = { change, _ ->
                            sweep.move(change.position.x - center.x, change.position.y - center.y)
                            swept = sweep.sweptDegrees.toFloat()
                        },
                        onDragEnd = {
                            if (sweep.isTriggered && command != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTriggered(command)
                            }
                            sweep.reset()
                            swept = 0f
                        },
                        onDragCancel = {
                            sweep.reset()
                            swept = 0f
                        },
                    )
                },
    ) {
        drawDial(
            colors =
                DialColors(
                    track = trackColor,
                    progress = if (command == null) trackColor else progressColor,
                    pointer = pointerColor,
                ),
            sweptDegrees = swept,
            position = ready.position,
        )
    }
}

/** ダイヤルの3色。[progress]は操作できないときは輪と同じ色にして、なぞっても進まないことを示す。 */
private class DialColors(
    val track: Color,
    val progress: Color,
    val pointer: Color,
)

private fun DrawScope.drawDial(
    colors: DialColors,
    sweptDegrees: Float,
    position: Int,
) {
    val stroke = STROKE_DP.dp.toPx()
    val radius = min(size.width, size.height) / 2f - stroke
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    drawCircle(color = colors.track, radius = radius, style = Stroke(width = stroke))
    // なぞった量。真上（-90度）から指の回った向きへ伸ばす。
    val limit = DialSweep.TRIGGER_DEGREES.toFloat()
    val progressSweep = sweptDegrees.coerceIn(-limit, limit)
    drawArc(
        color = colors.progress,
        startAngle = -90f,
        sweepAngle = progressSweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // 針。真上を0度とした時計回りの角度で描く。
    val radians = Math.toRadians(DialSweep.pointerDegreesOf(position) - 90.0)
    val length = radius * POINTER_LENGTH
    val tip = Offset(center.x + (length * cos(radians)).toFloat(), center.y + (length * sin(radians)).toFloat())
    drawLine(color = colors.pointer, start = center, end = tip, strokeWidth = stroke, cap = StrokeCap.Round)
    drawCircle(color = colors.pointer, radius = stroke, center = center)
}

private const val DEFAULT_NAME = "セサミ"
private const val SUBTITLE = "Bluetoothで直接操作します。届かないときはインターネット経由では操作しません。"
private const val CONNECTING_LABEL = "Bluetoothで接続しています..."
private const val DIAL_WIDTH_FRACTION = 0.8f
private const val STROKE_DP = 10
private const val POINTER_LENGTH = 0.75

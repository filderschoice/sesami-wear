package com.sesamiwear.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * mobileのホーム画面ウィジェット（BL-121）。wearのTileと同じく、左側にデバイス名と「変更」、右側の大きな
 * 領域に状態アイコン・状態文言・操作文言を置き、状態色は右側にだけ使う。表示内容の決定は
 * [SesameWidgetModelResolver]（Android非依存）が行い、ここはGlanceで並べるだけにしている。
 * 表示領域に応じて、2マス×1マス相当（BL-174）・1マス相当（BL-128、保険）の表示へ切り替える。
 * どれを使うかの判定は[SesameWidgetLayout]（Android非依存）が持つ。
 *
 * 表示の更新は[SesameWidgetUpdater]がウィジェットの状態へ更新トークン（[REFRESH_TOKEN_KEY]）を書き込んで
 * 再描画を要求し、描画側はトークンの変化を契機に保存済みの割り当て・資格情報・ロック状態を読み直す。
 * Glanceのセッションが生きている間は[provideGlance]が再実行されないため、この仕組みで最新値を反映する。
 *
 * ウィジェット一覧へ「2 × 1」としても並べるため、初期サイズだけが違う変種[SesameWidgetSmall]が
 * このクラスを継承している（BL-186）。表示・操作の実装はすべてここにある。
 */
open class SesameWidget : GlanceAppWidget() {
    /**
     * サイズ別レイアウト（BL-128 / BL-174）。提示した候補のうち、実際の表示領域に収まる最大のものが
     * [LocalSize]として渡される。候補は「1マス（1x1）相当」「2マス×1マス相当」「Tile相当（4x2）」の3つ。
     * 縮小の下限は`minResizeWidth`で2マス分（110dp）にしているため、通常は後ろ2つのどちらかになる。
     */
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(COMPACT_WIDTH_DP.dp, COMPACT_HEIGHT_DP.dp),
                DpSize(MEDIUM_WIDTH_DP.dp, MEDIUM_HEIGHT_DP.dp),
                DpSize(FULL_WIDTH_DP.dp, FULL_HEIGHT_DP.dp),
            ),
        )

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialModel = withContext(Dispatchers.IO) { SesameWidgetRepository.loadModel(context, appWidgetId) }
        provideContent {
            val refreshToken = currentState(REFRESH_TOKEN_KEY)
            var model by remember { mutableStateOf(initialModel) }
            LaunchedEffect(refreshToken) {
                model = withContext(Dispatchers.IO) { SesameWidgetRepository.loadModel(context, appWidgetId) }
            }
            val size = LocalSize.current
            SesameWidgetContent(
                model = model,
                actions = WidgetActions(context, appWidgetId, model),
                layout =
                    SesameWidgetLayout.of(
                        widthDp = size.width.value.toInt(),
                        heightDp = size.height.value.toInt(),
                    ),
            )
        }
    }

    companion object {
        /** 再描画要求ごとに書き換える値。中身に意味はなく、変化したことだけを契機に使う。 */
        val REFRESH_TOKEN_KEY = longPreferencesKey("refresh_token")

        /** 対象デバイスの選択画面を開く（未設定時のタップと「変更」で共通）。 */
        fun configureAction(
            context: Context,
            appWidgetId: Int,
        ): Action =
            actionStartActivity(
                Intent(context, WidgetConfigurationActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    // PendingIntentがインスタンス間で共有されないよう、ID ごとに Intent を区別する。
                    .setData(Uri.parse("sesamiwear://widget/configure/$appWidgetId")),
            )
    }
}

/**
 * ウィジェット上のタップ操作（BL-122）。右側は[WidgetTapAction]の判定どおり、施錠は[WidgetCommandReceiver]で
 * 即時実行、解錠は[WidgetUnlockConfirmActivity]を開き、操作できない状態（通信中・状態不明）では何もしない。
 * 左側のデバイス名は状態取得のみ、「変更」は選択画面を開く。
 */
internal class WidgetActions(
    context: Context,
    appWidgetId: Int,
    model: SesameWidgetModel,
) {
    val configure: Action = SesameWidget.configureAction(context, appWidgetId)

    val status: Action? =
        when (val tap = WidgetTapAction.forModel(model)) {
            WidgetTapAction.OpenConfiguration -> configure
            WidgetTapAction.None -> null
            is WidgetTapAction.Run ->
                (model as? SesameWidgetModel.Configured)?.let {
                    actionSendBroadcast(
                        WidgetCommandReceiver.runCommandIntent(context, appWidgetId, it.deviceUuid, tap.command),
                    )
                }
            is WidgetTapAction.Confirm ->
                (model as? SesameWidgetModel.Configured)?.let {
                    actionStartActivity(
                        WidgetUnlockConfirmActivity.createIntent(context, appWidgetId, it.deviceUuid, it.displayName),
                    )
                }
        }

    val refresh: Action? =
        (model as? SesameWidgetModel.Configured)?.let {
            actionSendBroadcast(WidgetCommandReceiver.refreshStatusIntent(context, appWidgetId, it.deviceUuid))
        }

    /** 対象デバイスを1つ前へ戻す（◀、BL-175）。 */
    val cycleBackward: Action? = cycleAction(context, appWidgetId, model, WidgetDeviceCycle.BACKWARD)

    /** 対象デバイスを1つ後ろへ進める（▶、BL-175）。 */
    val cycleForward: Action? = cycleAction(context, appWidgetId, model, WidgetDeviceCycle.FORWARD)
}

private fun cycleAction(
    context: Context,
    appWidgetId: Int,
    model: SesameWidgetModel,
    step: Int,
): Action? =
    (model as? SesameWidgetModel.Configured)?.let {
        actionSendBroadcast(WidgetCommandReceiver.cycleDeviceIntent(context, appWidgetId, it.deviceUuid, step))
    }

@Composable
private fun SesameWidgetContent(
    model: SesameWidgetModel,
    actions: WidgetActions,
    layout: SesameWidgetLayout,
) {
    val containerModifier =
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(WIDGET_BACKGROUND_ARGB)))
            .cornerRadius(CORNER_RADIUS_DP.dp)
            .padding(CONTAINER_PADDING_DP.dp)
    when (model) {
        SesameWidgetModel.Unconfigured ->
            Box(
                modifier = containerModifier.clickable(actions.configure),
                contentAlignment = Alignment.Center,
            ) {
                // 1マスでは「タップして設定」（7文字）が収まらないため短縮する（BL-128）。
                val isCompact = layout == SesameWidgetLayout.COMPACT
                val message = if (isCompact) COMPACT_UNCONFIGURED_MESSAGE else SesameWidgetModel.Unconfigured.MESSAGE
                val sizeSp = if (layout == SesameWidgetLayout.FULL) BODY_SP else CAPTION_SP
                Text(text = message, style = widgetTextStyle(NEUTRAL_TEXT_ARGB, sizeSp), maxLines = 2)
            }
        is SesameWidgetModel.Configured ->
            when (layout) {
                SesameWidgetLayout.COMPACT ->
                    CompactStatusBox(model = model, modifier = containerModifier, onClick = actions.status)
                SesameWidgetLayout.MEDIUM ->
                    Row(modifier = containerModifier) {
                        MediumLeftColumn(displayName = model.displayName, actions = actions)
                        Spacer(modifier = GlanceModifier.width(SPACING_DP.dp))
                        MediumStatusBox(model = model, onClick = actions.status)
                    }
                SesameWidgetLayout.FULL ->
                    Row(modifier = containerModifier) {
                        LeftColumn(displayName = model.displayName, actions = actions)
                        Spacer(modifier = GlanceModifier.width(SPACING_DP.dp))
                        StatusBox(model = model, onClick = actions.status)
                    }
            }
    }
}

/**
 * 1マス（1x1）相当のコンパクト表示（BL-128）。状態アイコンと短い状態文言だけを出す。
 * タップの挙動はTile相当の表示と同じ（[WidgetTapAction]の判定どおり）。
 * デバイス名・「変更」・最終取得時刻は入らないため出さない。対象デバイスの変更は、
 * ウィジェットの長押しメニュー（`widgetFeatures="reconfigurable"`）から行える。
 */
@Composable
private fun CompactStatusBox(
    model: SesameWidgetModel.Configured,
    modifier: GlanceModifier,
    onClick: Action?,
) {
    Box(
        modifier = modifier.clickableOrSelf(onClick).background(ColorProvider(Color(model.backgroundColorArgb))),
        contentAlignment = Alignment.Center,
    ) {
        StatusIconAndLabel(model)
    }
}

/** 左列。上がデバイス名（タップで状態取得）、下が「変更」（選択画面を開く）。いずれも中立色。 */
@Composable
private fun LeftColumn(
    displayName: String,
    actions: WidgetActions,
) {
    Column(modifier = GlanceModifier.width(LEFT_COLUMN_WIDTH_DP.dp).fillMaxHeight()) {
        NeutralChip(text = displayName, modifier = GlanceModifier.defaultWeight().clickableOrSelf(actions.refresh))
        Spacer(modifier = GlanceModifier.height(SPACING_DP.dp))
        NeutralChip(text = CHANGE_LABEL, modifier = GlanceModifier.defaultWeight().clickable(actions.configure))
    }
}

@Composable
private fun StatusTexts(model: SesameWidgetModel.Configured) {
    Text(text = model.statusIcon, style = widgetTextStyle(model.textColorArgb, ICON_SP))
    Text(text = model.statusLabel, style = widgetTextStyle(model.textColorArgb, BODY_SP, bold = true))
    // 最後に状態を取得した時刻（または直近の失敗の理由）と電池残量（BL-142 / BL-140 / BL-171）と、
    // 経路のアイコン（BL-176）。高さ予算が埋まっているため、いずれも行を足さずこの1行へ併記する。
    model.detailWithBatteryLabel?.let {
        DetailRow(route = model.route, text = it, textColorArgb = model.textColorArgb)
    }
    model.actionLabel?.let { Text(text = it, style = widgetTextStyle(model.textColorArgb, CAPTION_SP)) }
}

/** 右側の状態表示。状態色の背景に、アイコン・状態文言・操作文言を中央寄せで並べる。 */
@Composable
private fun RowScope.StatusBox(
    model: SesameWidgetModel.Configured,
    onClick: Action?,
) {
    Column(
        modifier =
            GlanceModifier
                .defaultWeight()
                .clickableOrSelf(onClick)
                .fillMaxHeight()
                .background(ColorProvider(Color(model.backgroundColorArgb)))
                .cornerRadius(CHIP_CORNER_RADIUS_DP.dp)
                .padding(CHIP_INNER_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusTexts(model)
    }
}

private const val CHANGE_LABEL = "変更"

/** 1マス表示で「タップして設定」の代わりに出す文言（BL-128）。 */
private const val COMPACT_UNCONFIGURED_MESSAGE = "設定"

// ウィジェット全体の背景（wearのTileの黒背景に相当する暗色）と、左側チップの文字色。
private const val WIDGET_BACKGROUND_ARGB = 0xFF121212.toInt()

// 寸法はwearのTile（左列76dp・チップ角丸12dp・間隔6dp）を、スマホのホーム画面の広さに合わせて広げたもの。
private const val LEFT_COLUMN_WIDTH_DP = 96
private const val CONTAINER_PADDING_DP = 8
private const val SPACING_DP = 6
private const val CORNER_RADIUS_DP = 16

// サイズ別レイアウト（BL-128）へ提示する候補。実際の表示領域に収まる最大のものが選ばれる。
// 1マスの実寸は端末とランチャーで前後するため、一般的な値より小さめを候補にしている。
private const val COMPACT_WIDTH_DP = 50
private const val COMPACT_HEIGHT_DP = 50
private const val MEDIUM_WIDTH_DP = SesameWidgetLayout.MEDIUM_MIN_WIDTH_DP
private const val MEDIUM_HEIGHT_DP = 50
private const val FULL_WIDTH_DP = SesameWidgetLayout.FULL_MIN_WIDTH_DP
private const val FULL_HEIGHT_DP = SesameWidgetLayout.FULL_MIN_HEIGHT_DP

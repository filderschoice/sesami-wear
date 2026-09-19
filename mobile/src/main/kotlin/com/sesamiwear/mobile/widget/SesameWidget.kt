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
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sesamiwear.core.display.SesameTileContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * mobileのホーム画面ウィジェット（BL-121）。wearのTileと同じく、左側にデバイス名と「変更」、右側の大きな
 * 領域に状態アイコン・状態文言・操作文言を置き、状態色は右側にだけ使う。表示内容の決定は
 * [SesameWidgetModelResolver]（Android非依存）が行い、ここはGlanceで並べるだけにしている。
 * 表示領域に応じて1マス（1x1）相当のコンパクト表示へ切り替える（BL-128）。どちらを使うかの判定は
 * [SesameWidgetLayout]（Android非依存）が持つ。
 *
 * 表示の更新は[SesameWidgetUpdater]がウィジェットの状態へ更新トークン（[REFRESH_TOKEN_KEY]）を書き込んで
 * 再描画を要求し、描画側はトークンの変化を契機に保存済みの割り当て・資格情報・ロック状態を読み直す。
 * Glanceのセッションが生きている間は[provideGlance]が再実行されないため、この仕組みで最新値を反映する。
 */
class SesameWidget : GlanceAppWidget() {
    /**
     * サイズ別レイアウト（BL-128）。提示した候補のうち、実際の表示領域に収まる最大のものが
     * [LocalSize]として渡される。候補は「1マス（1x1）相当」と「Tile相当（4x2）」の2つ。
     */
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(COMPACT_WIDTH_DP.dp, COMPACT_HEIGHT_DP.dp),
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
private class WidgetActions(
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
    val isCompact = layout == SesameWidgetLayout.COMPACT
    when (model) {
        SesameWidgetModel.Unconfigured ->
            Box(
                modifier = containerModifier.clickable(actions.configure),
                contentAlignment = Alignment.Center,
            ) {
                // 1マスでは「タップして設定」（7文字）が収まらないため短縮する（BL-128）。
                val message = if (isCompact) COMPACT_UNCONFIGURED_MESSAGE else SesameWidgetModel.Unconfigured.MESSAGE
                Text(text = message, style = textStyle(NEUTRAL_TEXT_ARGB, BODY_SP), maxLines = 2)
            }
        is SesameWidgetModel.Configured ->
            if (isCompact) {
                CompactStatusBox(model = model, modifier = containerModifier, onClick = actions.status)
            } else {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = model.statusIcon, style = textStyle(model.textColorArgb, COMPACT_ICON_SP))
            Text(
                text = model.statusLabel,
                style = textStyle(model.textColorArgb, CAPTION_SP, bold = true),
                maxLines = 1,
            )
        }
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

private fun GlanceModifier.clickableOrSelf(action: Action?): GlanceModifier = action?.let { clickable(it) } ?: this

@Composable
private fun NeutralChip(
    text: String,
    modifier: GlanceModifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ColorProvider(Color(SesameTileContent.CHIP_NEUTRAL_COLOR_ARGB)))
                .cornerRadius(CHIP_CORNER_RADIUS_DP.dp)
                .padding(CHIP_INNER_PADDING_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = textStyle(NEUTRAL_TEXT_ARGB, CAPTION_SP), maxLines = 2)
    }
}

@Composable
private fun StatusTexts(model: SesameWidgetModel.Configured) {
    Text(text = model.statusIcon, style = textStyle(model.textColorArgb, ICON_SP))
    Text(text = model.statusLabel, style = textStyle(model.textColorArgb, BODY_SP, bold = true))
    // 最後に状態を取得した時刻（または直近の失敗の理由）と電池残量（BL-142 / BL-140 / BL-171）。
    // 高さ予算が埋まっているため、電池は行を足さずこの1行へ併記する。
    model.detailWithBatteryLabel?.let {
        Text(text = it, style = textStyle(model.textColorArgb, FOOTNOTE_SP), maxLines = 2)
    }
    model.actionLabel?.let { Text(text = it, style = textStyle(model.textColorArgb, CAPTION_SP)) }
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

private fun textStyle(
    argb: Int,
    sizeSp: Int,
    bold: Boolean = false,
) = TextStyle(
    color = ColorProvider(Color(argb)),
    fontSize = sizeSp.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textAlign = TextAlign.Center,
)

private const val CHANGE_LABEL = "変更"

/** 1マス表示で「タップして設定」の代わりに出す文言（BL-128）。 */
private const val COMPACT_UNCONFIGURED_MESSAGE = "設定"

// ウィジェット全体の背景（wearのTileの黒背景に相当する暗色）と、左側チップの文字色。
private const val WIDGET_BACKGROUND_ARGB = 0xFF121212.toInt()
private const val NEUTRAL_TEXT_ARGB = 0xFFFFFFFF.toInt()

// 寸法はwearのTile（左列76dp・チップ角丸12dp・間隔6dp）を、スマホのホーム画面の広さに合わせて広げたもの。
private const val LEFT_COLUMN_WIDTH_DP = 96
private const val CONTAINER_PADDING_DP = 8
private const val SPACING_DP = 6
private const val CORNER_RADIUS_DP = 16
private const val CHIP_CORNER_RADIUS_DP = 12
private const val CHIP_INNER_PADDING_DP = 6
private const val ICON_SP = 28
private const val BODY_SP = 16
private const val CAPTION_SP = 13
private const val FOOTNOTE_SP = 11
private const val COMPACT_ICON_SP = 24

// サイズ別レイアウト（BL-128）へ提示する候補。実際の表示領域に収まる最大のものが選ばれる。
// 1マスの実寸は端末とランチャーで前後するため、一般的な値より小さめを候補にしている。
private const val COMPACT_WIDTH_DP = 50
private const val COMPACT_HEIGHT_DP = 50
private const val FULL_WIDTH_DP = SesameWidgetLayout.FULL_MIN_WIDTH_DP
private const val FULL_HEIGHT_DP = SesameWidgetLayout.FULL_MIN_HEIGHT_DP

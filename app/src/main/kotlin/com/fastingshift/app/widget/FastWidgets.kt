package com.fastingshift.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.fastingshift.app.R
import com.fastingshift.app.di.AppEntryPoint
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.service.StartResult
import com.fastingshift.domain.timer.FastTimer
import com.fastingshift.domain.timer.TimerState
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import kotlinx.coroutines.flow.first

/** What every widget renders, read once per update from the single local database. */
private data class WidgetModel(val fast: Fast?, val state: TimerState?, val defaultName: String)

private suspend fun load(context: Context): WidgetModel {
    val ep = EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java)
    val fast = ep.fastRepository().getActive()
    val settings = ep.settingsRepository().settings.first()
    val state = fast?.let { FastTimer.state(it, ep.clock().instant(), settings.reminders.milestones) }
    val proto = settings.defaultProtocolId?.let(Presets::byId) ?: Presets.W16_8
    return WidgetModel(fast, state, proto.name)
}

/** H:MM — widgets show minutes; the display is refreshed at least every minute. */
private fun Duration.hm(): String = "%d:%02d".format(toHours(), toMinutes() % 60)

/** Small widget: progress ring with elapsed time. Tap opens the app. */
class RingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = load(context)
        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(16.dp).clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Ring(model.state?.progress ?: 0f, 96)
                    Text(
                        text = model.state?.elapsed?.hm() ?: context.getString(R.string.widget_idle_short),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                    )
                }
            }
        }
    }
}

/** Medium widget: elapsed, remaining, next milestone and a start/stop button. */
class DetailWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = load(context)
        provideContent {
            GlanceTheme {
                Row(
                    modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())) {
                        Ring(model.state?.progress ?: 0f, 72)
                    }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        val st = model.state
                        val onSurface = TextStyle(color = GlanceTheme.colors.onSurface)
                        if (st != null) {
                            Text(context.getString(R.string.widget_elapsed, st.elapsed.hm()), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
                            Text(
                                if (st.isTargetReached) context.getString(R.string.widget_goal_reached)
                                else context.getString(R.string.widget_remaining, st.remaining.hm()),
                                style = onSurface,
                            )
                            st.nextMilestone?.let { Text(context.getString(R.string.widget_next_milestone, it.toHours()), style = onSurface) }
                        } else {
                            Text(context.getString(R.string.widget_idle), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
                            Text(model.defaultName, style = onSurface)
                        }
                        Spacer(GlanceModifier.size(4.dp))
                        Text(
                            text = context.getString(if (model.fast != null) R.string.timer_end else R.string.timer_start),
                            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(12.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp).clickable(actionRunCallback<StartStopAction>()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Ring(progress: Float, sizeDp: Int) {
    val track = GlanceTheme.colors.surfaceVariant
    val fill = GlanceTheme.colors.primary
    // Colors resolve per configuration; render using the light/dark-resolved values.
    val context = androidx.glance.LocalContext.current
    val bitmap = ringBitmap(progress, sizeDp * 3, track.getColor(context).toArgb(), fill.getColor(context).toArgb())
    Image(provider = ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(sizeDp.dp))
}

private fun ringBitmap(progress: Float, px: Int, trackColor: Int, fillColor: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val stroke = px * 0.1f
    val rect = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    paint.color = trackColor
    canvas.drawArc(rect, 0f, 360f, false, paint)
    if (progress > 0f) {
        paint.color = fillColor
        canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
    }
    return bmp
}

/**
 * Start or end a fast without opening the app (PRD F4). Fasts that need the extended-fast
 * checklist cannot start from a widget: the app opens so the user sees it (F7 is never skipped).
 */
class StartStopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ep = EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java)
        val service = ep.fastService()
        if (ep.fastRepository().getActive() != null) {
            service.endActive()
        } else {
            val proto = ep.settingsRepository().settings.first().defaultProtocolId?.let(Presets::byId) ?: Presets.W16_8
            val needsApp = FastPolicy.requirementsFor(Duration.ofHours(proto.fastHours.toLong())).isNotEmpty()
            val result = if (needsApp) null else service.start(proto)
            if (result !is StartResult.Started && result !is StartResult.AlreadyActive) {
                context.startActivity(
                    android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        WidgetUpdater.updateAll(context)
    }
}

class RingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()
}

class DetailWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DetailWidget()
}

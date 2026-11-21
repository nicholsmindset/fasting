package com.fastingshift.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.unit.ColorProvider
import com.fastingshift.app.R
import com.fastingshift.app.di.AppEntryPoint
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.db.DatabaseKeyProvider
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.timer.FastTimer
import com.fastingshift.domain.timer.TimerState
import dagger.hilt.android.EntryPointAccessors
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** What every widget renders, derived from the single local database. */
private data class WidgetModel(
    val fast: Fast?,
    val state: TimerState?,
    val defaultName: String,
    val startMode: WidgetStartMode,
)

private fun entryPoint(context: Context) = EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java)

private fun model(fast: Fast?, settings: AppSettings, screening: ScreeningRecord?, now: Instant): WidgetModel {
    val proto = settings.defaultProtocolId?.let(Presets::byId) ?: Presets.W16_8
    val mode = WidgetStartMode.decide(
        hasActiveFast = fast != null,
        gate = screening?.gateResult,
        clinicianAcknowledged = screening?.clinicianAcknowledged ?: false,
        planned = Duration.ofHours(proto.fastHours.toLong()),
    )
    return WidgetModel(fast, fast?.let { FastTimer.state(it, now, settings.reminders.milestones) }, proto.name, mode)
}

/** Ticks at each minute boundary so remaining time and the ring stay current while shown. */
private fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        val now = clock.instant()
        emit(now)
        delay(60_000 - now.toEpochMilli() % 60_000)
    }
}

/**
 * Loads the first frame before composing (no blank flash), then keeps reading the database,
 * settings and screening inside the composition, so updates reach a live Glance session.
 */
private suspend fun GlanceAppWidget.provideWidget(context: Context, content: @Composable (WidgetModel) -> Unit) {
    // The encrypted database can't be opened after its key was lost: just point to the app.
    if (FastingDatabase.keyStatus(context) == DatabaseKeyProvider.Status.LOST) {
        provideContent { GlanceTheme { OpenAppOnly(context) } }
    }
    val ep = entryPoint(context)
    val fasts = ep.fastRepository()
    val settings = ep.settingsRepository()
    val screening = ep.screeningRepository()
    val clock = ep.clock()
    val initial = model(fasts.getActive(), settings.settings.first(), screening.getLatest(), clock.instant())
    provideContent {
        val flow = remember {
            combine(fasts.observeActive(), settings.settings, screening.observeLatest(), minuteTicks(clock), ::model)
        }
        val current by flow.collectAsState(initial)
        GlanceTheme { content(current) }
    }
}

@Composable
private fun OpenAppOnly(context: Context) {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp).clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(context.getString(R.string.widget_open_app), style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
    }
}

/** H:MM — minute-resolution text, refreshed by the minute ticker. */
private fun Duration.hm(): String = "%d:%02d".format(toHours(), toMinutes() % 60)

/**
 * Live elapsed time from a RemoteViews Chronometer: the launcher ticks it every second, so it
 * is never stale and costs no widget updates or alarms.
 */
@Composable
private fun ElapsedChronometer(startAt: Instant, format: String?, sizeSp: Float, color: ColorProvider) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startAt.toEpochMilli())
        setChronometer(R.id.widget_chronometer, base, format, true)
        setTextColor(R.id.widget_chronometer, color.getColor(context).toArgb())
        setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(views)
}

/** Small widget: progress ring with elapsed time. Tap opens the app. */
class RingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId): Unit = provideWidget(context) { model ->
        Box(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp).clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Ring(model.state?.progress ?: 0f, 96)
            val fast = model.fast
            if (fast != null) {
                ElapsedChronometer(fast.startAt, null, 14f, GlanceTheme.colors.onSurface)
            } else {
                Text(
                    text = context.getString(R.string.widget_idle_short),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                )
            }
        }
    }
}

/** Medium widget: elapsed, remaining, next milestone and a start/stop button. */
class DetailWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId): Unit = provideWidget(context) { model ->
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
                val fast = model.fast
                val onSurface = TextStyle(color = GlanceTheme.colors.onSurface)
                val title = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface)
                if (st != null && fast != null) {
                    ElapsedChronometer(fast.startAt, context.getString(R.string.widget_elapsed, "%s"), 16f, GlanceTheme.colors.onSurface)
                    Text(
                        if (st.isTargetReached) context.getString(R.string.widget_goal_reached)
                        else context.getString(R.string.widget_remaining, st.remaining.hm()),
                        style = onSurface,
                    )
                    st.nextMilestone?.let { Text(context.getString(R.string.widget_next_milestone, it.toHours()), style = onSurface) }
                } else {
                    Text(context.getString(R.string.widget_idle), style = title)
                    // Education-only and unscreened users are never shown a protocol.
                    if (model.startMode != WidgetStartMode.OPEN_APP_ONLY) Text(model.defaultName, style = onSurface)
                }
                Spacer(GlanceModifier.size(4.dp))
                ActionButton(context, model.startMode)
            }
        }
    }
}

@Composable
private fun ActionButton(context: Context, mode: WidgetStartMode) {
    if (mode == WidgetStartMode.OPEN_APP_ONLY) {
        Text(
            text = context.getString(R.string.widget_open_app),
            style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.padding(vertical = 6.dp).clickable(actionStartActivity<MainActivity>()),
        )
        return
    }
    val action: Action = when (mode) {
        WidgetStartMode.END -> actionRunCallback<StartStopAction>(actionParametersOf(StartStopAction.INTENT to StartStopAction.END))
        WidgetStartMode.START_IN_BACKGROUND -> actionRunCallback<StartStopAction>(actionParametersOf(StartStopAction.INTENT to StartStopAction.START))
        // Activity launch straight from the click: background starts are blocked on Android 14+.
        else -> actionStartActivity<MainActivity>()
    }
    Text(
        text = context.getString(if (mode == WidgetStartMode.END) R.string.timer_end else R.string.timer_start),
        style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(12.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp).clickable(action),
    )
}

@Composable
private fun Ring(progress: Float, sizeDp: Int) {
    val track = GlanceTheme.colors.surfaceVariant
    val fill = GlanceTheme.colors.primary
    // Colors resolve per configuration; render using the light/dark-resolved values.
    val context = LocalContext.current
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
 * Start or end a fast without opening the app (PRD F4). Only used when [WidgetStartMode]
 * decided at draw time that no app screen is needed; fasts that need the checklist get an
 * activity-launch button instead (F7 is never skipped).
 */
class StartStopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (FastingDatabase.keyStatus(context) == DatabaseKeyProvider.Status.LOST) return
        val ep = entryPoint(context)
        val settings = ep.settingsRepository()
        val active = ep.fastRepository().getActive() != null
        // Do only what the tapped button said. A stale "Start" must never end a running fast,
        // and a stale "End" must never start one; just redraw with the right button.
        val intent = parameters[INTENT]
        if ((intent == START && active) || (intent == END && !active)) {
            ep.reminderCoordinator().reschedule()
            return
        }
        if (active) {
            val result = ep.fastService().endActive()
            // The Timer screen shows refeeding guidance for long fasts ended elsewhere (SR-5).
            if (result?.refeeding != null) settings.setPendingRefeeding(result.fast.id)
        } else {
            val proto = settings.current().defaultProtocolId?.let(Presets::byId) ?: Presets.W16_8
            // The service re-checks every rule. If something changed since the widget was
            // drawn the start is refused; rescheduling redraws it with the right button.
            ep.fastService().start(proto)
        }
        // Clears or arms alarms, the ongoing notification and refreshes every widget.
        ep.reminderCoordinator().reschedule()
    }

    companion object {
        val INTENT = ActionParameters.Key<String>("intent")
        const val START = "start"
        const val END = "end"
    }
}

class RingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!anyWidgetPlaced(context)) WidgetUpdater.cancelPeriodic(context)
    }
}

class DetailWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DetailWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!anyWidgetPlaced(context)) WidgetUpdater.cancelPeriodic(context)
    }
}

/** Whether any widget of either kind is still on a home screen. */
internal fun anyWidgetPlaced(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    return listOf(RingWidgetReceiver::class.java, DetailWidgetReceiver::class.java).any {
        manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
    }
}

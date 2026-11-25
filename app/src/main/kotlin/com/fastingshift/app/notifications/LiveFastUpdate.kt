package com.fastingshift.app.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.ProgressStyle
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import com.fastingshift.app.R
import com.fastingshift.app.alarm.AlarmReceiver
import com.fastingshift.app.ui.theme.Emerald
import com.fastingshift.app.ui.theme.LightEmerald
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.timeline.FastProgressScale
import java.time.Duration
import java.time.Instant
import java.util.Date

internal object LiveFastUpdate {
    private const val PREFS = "live_fast_notification"
    private const val DISMISSED = "dismissed_fast"
    const val ACTION_TICK = "com.fastingshift.app.action.LIVE_FAST_TICK"
    const val ACTION_DISMISS = "com.fastingshift.app.action.LIVE_FAST_DISMISS"

    fun dismissed(context: Context, id: Long): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(DISMISSED, -1) == id

    fun rememberDismissal(context: Context, id: Long) {
        if (id >= 0) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(DISMISSED, id).apply()
    }

    fun clearDismissal(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(DISMISSED)) prefs.edit().remove(DISMISSED).apply()
    }

    @RequiresApi(36)
    fun eligible(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = manager.getNotificationChannel(Notifications.CHANNEL_ONGOING)?.importance ?: return false
        return importance > NotificationManager.IMPORTANCE_MIN &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.canPostPromotedNotifications()
    }

    @RequiresApi(36)
    fun decorate(context: Context, builder: NotificationCompat.Builder, fast: Fast, now: Instant) {
        val elapsed = Duration.between(fast.startAt, now).coerceAtLeast(Duration.ZERO)
        val scale = FastProgressScale.forFast(fast.plannedDuration, elapsed)
        val colors = stageColors
        val style = ProgressStyle()
            .setProgressSegments(scale.segmentMinutes.mapIndexed { i, length ->
                ProgressStyle.Segment(length).setColor(colors[i % colors.size])
            })
            .setProgressPoints(scale.transitionMinutes.mapIndexed { i, point ->
                ProgressStyle.Point(point).setColor(colors[(i + 1) % colors.size])
            })
            .setProgress(scale.progress)

        val preset = Presets.byId(fast.protocolId)
        val label = if (preset?.type == ProtocolType.WINDOW) {
            context.getString(R.string.live_fast_protocol, preset.name)
        } else context.getString(R.string.notif_ongoing_title)
        val end = DateFormat.getTimeFormat(context).format(Date.from(fast.targetEndAt))
        builder.setContentTitle(context.getString(R.string.live_fast_title, label, elapsed.toHours(), elapsed.toMinutes() % 60))
            .setContentText(context.getString(R.string.live_fast_ends, end))
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(context.getString(R.string.live_fast_chip, elapsed.toHours(), elapsed.toMinutes() % 60))
            .setStyle(style)
    }

    private val stageColors by lazy {
        val dark = Emerald.toArgb()
        val light = LightEmerald.toArgb()
        List(6) { i -> ColorUtils.blendARGB(dark, light, i / 5f) }
    }

    fun dismissIntent(context: Context, fastId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, AlarmReceiver::class.java).setAction(ACTION_DISMISS)
            .setData(Uri.parse("fastingshift-internal://live-dismiss/$fastId"))
            .putExtra(AlarmReceiver.EXTRA_FAST_ID, fastId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A single non-wakeup alarm. Elapsed chrono keeps advancing if sleep defers a refresh. */
    fun schedule(context: Context, next: Instant?) {
        if (Build.VERSION.SDK_INT < 36) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_TICK)
        if (next == null) {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { manager.cancel(it); it.cancel() }
            return
        }
        val tick = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (manager.canScheduleExactAlarms()) {
            try {
                manager.setExact(AlarmManager.RTC, next.toEpochMilli(), tick)
                return
            } catch (_: SecurityException) {
                // The permission may be revoked between the capability check and scheduling.
            }
        }
        manager.set(AlarmManager.RTC, next.toEpochMilli(), tick)
    }

    fun nextUpdate(start: Instant, end: Instant, now: Instant): Instant? {
        if (now >= end) return null
        val elapsedMinutes = Duration.between(start, now).toMinutes().coerceAtLeast(0)
        return minOf(end, start.plusSeconds((elapsedMinutes / 15 + 1) * 15 * 60))
    }
}

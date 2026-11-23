package com.fastingshift.app.ui.components

import android.app.NotificationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fastingshift.app.notifications.Notifications

@Composable
internal fun startReminderNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    fun enabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(Notifications.CHANNEL_REMINDERS)?.importance != NotificationManager.IMPORTANCE_NONE

    var available by remember(context) { mutableStateOf(enabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { available = enabled() }
    return available
}

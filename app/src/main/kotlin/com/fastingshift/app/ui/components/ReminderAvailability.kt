package com.fastingshift.app.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fastingshift.app.notifications.Notifications

internal fun appNotificationsEnabled(context: Context): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

internal fun openAppNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

@Composable
internal fun startReminderNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    fun enabled(): Boolean =
        appNotificationsEnabled(context) &&
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(Notifications.CHANNEL_REMINDERS)?.importance != NotificationManager.IMPORTANCE_NONE

    var available by remember(context) { mutableStateOf(enabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { available = enabled() }
    return available
}

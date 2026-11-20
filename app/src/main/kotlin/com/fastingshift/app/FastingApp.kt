package com.fastingshift.app

import android.app.Application
import com.fastingshift.app.alarm.ReminderCoordinator
import com.fastingshift.app.notifications.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FastingApp : Application() {
    @Inject lateinit var reminders: ReminderCoordinator

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Keeps alarms in step with the database: any start, end or edit (from the app,
        // a widget or a notification action) reschedules automatically.
        reminders.start()
    }
}

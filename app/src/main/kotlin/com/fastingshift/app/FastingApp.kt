package com.fastingshift.app

import android.app.Application
import com.fastingshift.app.alarm.ReminderCoordinator
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.di.ApplicationScope
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.data.db.DatabaseKeyProvider
import com.fastingshift.data.db.FastingDatabase
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class FastingApp : Application() {
    // Lazy: building the coordinator opens the encrypted database (Keystore + SQLCipher),
    // which must not happen on the main thread, and must not happen at all if the key is lost.
    @Inject lateinit var reminders: Lazy<ReminderCoordinator>
    @Inject lateinit var billing: BillingManager
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        scope.launch(Dispatchers.IO) { com.fastingshift.app.share.FileSharing.purgeOld(this@FastingApp) }
        billing.configure()
        scope.launch(Dispatchers.Default) {
            // A lost key means the data can't be read; MainActivity shows the recovery screen.
            if (FastingDatabase.keyStatus(this@FastingApp) == DatabaseKeyProvider.Status.LOST) return@launch
            // Keeps alarms, the ongoing notification and widgets in step with the database: any
            // start, end or edit (from the app, a widget or a notification action) flows through.
            reminders.get().start()
        }
    }
}

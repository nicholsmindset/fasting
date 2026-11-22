package com.fastingshift.app.trust

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.fastingshift.app.alarm.AlarmGateway
import com.fastingshift.app.alarm.AlarmScheduler
import com.fastingshift.app.share.FileSharing
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.db.FastingDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Delete all data" from the Trust center: removes everything the app keeps on this phone (the
 * encrypted database and its Keystore key, settings, cached files, scheduled reminders), then
 * restarts the app fresh. Data the user exported, shared or synced to Health Connect lives
 * outside the app and is not touched; purchases stay with the Google Play account.
 */
@Singleton
class DataWiper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FastingDatabase,
    private val alarms: AlarmGateway,
) {
    suspend fun wipeAndRestart() {
        withContext(Dispatchers.IO) {
            runCatching { WorkManager.getInstance(context).cancelAllWork() }
            runCatching { AlarmScheduler(alarms).apply(emptyList()) }
            runCatching { NotificationManagerCompat.from(context).cancelAll() }
            runCatching { db.close() }
            FastingDatabase.resetEncrypted(context)
            // Settings (DataStore), widget state and small preference files.
            File(context.filesDir, "datastore").deleteRecursively()
            File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { it.delete() }
            FileSharing.clear(context)
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        // In-memory singletons still hold the old data: start a new process from a clean graph.
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Runtime.getRuntime().exit(0)
    }
}

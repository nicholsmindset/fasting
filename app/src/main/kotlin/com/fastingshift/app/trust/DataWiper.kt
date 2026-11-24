package com.fastingshift.app.trust

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.await
import com.fastingshift.app.alarm.AlarmGateway
import com.fastingshift.app.alarm.AlarmScheduler
import com.fastingshift.app.billing.BillingReminderAction
import com.fastingshift.app.billing.BillingReminderScheduler
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    private val settings: SettingsRepository,
    private val billingReminders: BillingReminderScheduler,
) {
    suspend fun wipeAndRestart() {
        withContext(Dispatchers.IO) {
            // Wait for the cancellation to be saved: the process exits right after.
            WorkManager.getInstance(context).cancelAllWork().await()
            AlarmScheduler(alarms).apply(emptyList())
            billingReminders.apply(BillingReminderAction.Cancel)
            NotificationManagerCompat.from(context).cancelAll()

            // Remove shared medical reports before changing anything that might leave the Trust screen.
            deleteDirectoryContents(context.cacheDir)
            // The live settings DataStore must clear its own contents. Remove other stores (e.g. widgets).
            deleteDirectoryContents(File(context.filesDir, "datastore"), keep = "settings.preferences_pb")
            db.close()
            FastingDatabase.resetEncrypted(context)
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (!Files.notExists(prefsDir.toPath())) {
                Files.newDirectoryStream(prefsDir.toPath()).use { entries ->
                    for (entry in entries) {
                        val name = entry.fileName.toString().removeSuffix(".bak")
                        if (name.endsWith(".xml")) {
                            check(context.deleteSharedPreferences(name.removeSuffix(".xml"))) { "Could not remove preferences" }
                        }
                    }
                }
                deleteDirectoryContents(prefsDir)
            }
            // Clearing onboarding can dispose the Trust screen. Finish the committed reset even then.
            withContext(NonCancellable) {
                settings.clearAll()
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                Runtime.getRuntime().exit(0)
            }
        }
    }
}

/** Missing files make a retry a no-op. An inaccessible directory or failed deletion is an error. */
internal fun deleteDirectoryContents(directory: File, keep: String? = null) {
    val root = directory.toPath()
    if (Files.notExists(root)) return
    Files.newDirectoryStream(root).use { entries ->
        for (entry in entries) {
            if (entry.fileName.toString() == keep) continue
            // Files.walk does not follow symlinks. Remove children before their parent.
            Files.walk(entry).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}

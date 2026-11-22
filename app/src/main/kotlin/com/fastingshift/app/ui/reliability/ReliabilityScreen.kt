package com.fastingshift.app.ui.reliability

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fastingshift.app.R
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup

/**
 * Reliability check (PRD F3): exact alarms, notifications, and battery optimisation, with
 * deep links to the OEM screens that most often kill alarms (Samsung, Xiaomi, Oppo, Vivo).
 */
@Composable
fun ReliabilityRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val checks = remember(refresh) { Checks.read(context) }

    SecondaryScaffold(title = stringResource(R.string.rel_title), onBack = onBack) {
        Text(
            stringResource(R.string.rel_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        InsetGroup(footer = stringResource(R.string.rel_fix_footer)) {
            Item(R.string.rel_exact, checks.exactAlarms, R.string.rel_exact_fix) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
            GroupDivider()
            Item(R.string.rel_notifications, checks.notifications, R.string.rel_notifications_fix) {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            // Safety alerts and check-ins can be switched off one channel at a time.
            if (checks.notifications) {
                for ((channel, name) in SAFETY_CHANNELS) {
                    if (channel in checks.blockedChannels) {
                        GroupDivider()
                        Item(stringResource(R.string.rel_channel_title, stringResource(name)), false, R.string.rel_channel_fix) {
                            context.startActivity(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, channel),
                            )
                        }
                    }
                }
            }
            GroupDivider()
            Item(R.string.rel_battery, checks.unrestrictedBattery, R.string.rel_battery_fix) {
                // Opens the system list; the app never requests the exemption directly (Play policy).
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        OemHelp.forManufacturer(Build.MANUFACTURER)?.let { oem ->
            InsetGroup(header = stringResource(R.string.rel_oem_title, oem.label), footer = stringResource(oem.instructions)) {
                GroupRow(
                    stringResource(R.string.rel_open_settings),
                    titleColor = MaterialTheme.colorScheme.primary,
                    showChevron = true,
                    onClick = { OemHelp.open(context, oem) },
                )
            }
        }
    }
}

private val SAFETY_CHANNELS = listOf(
    Notifications.CHANNEL_FAST_ALERTS to R.string.channel_fast_alerts,
    Notifications.CHANNEL_CHECK_INS to R.string.channel_check_ins,
)

@Composable
private fun Item(label: Int, ok: Boolean, fixLabel: Int, onFix: () -> Unit) = Item(stringResource(label), ok, fixLabel, onFix)

/** One check: status on the right; when it needs attention the row is tappable and says how to fix it. */
@Composable
private fun Item(label: String, ok: Boolean, fixLabel: Int, onFix: () -> Unit) {
    GroupRow(
        title = label,
        subtitle = if (ok) null else stringResource(fixLabel),
        showChevron = !ok,
        onClick = if (ok) null else { { runCatching { onFix() } } },
    ) {
        Text(
            stringResource(if (ok) R.string.rel_ok else R.string.rel_needs_attention),
            style = MaterialTheme.typography.bodyLarge,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private data class Checks(
    val exactAlarms: Boolean,
    /** Permission granted and notifications not switched off for the whole app. */
    val notifications: Boolean,
    val unrestrictedBattery: Boolean,
    /** Channels the user set to "off" (importance NONE). */
    val blockedChannels: Set<String>,
) {
    companion object {
        fun read(context: Context): Checks {
            val am = context.getSystemService(AlarmManager::class.java)
            val pm = context.getSystemService(PowerManager::class.java)
            val nm = context.getSystemService(NotificationManager::class.java)
            return Checks(
                exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms(),
                notifications = (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    ) && NotificationManagerCompat.from(context).areNotificationsEnabled(),
                unrestrictedBattery = pm.isIgnoringBatteryOptimizations(context.packageName),
                blockedChannels = SAFETY_CHANNELS.map { it.first }
                    .filter { nm.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }
                    .toSet(),
            )
        }
    }
}

private data class Oem(val label: String, val instructions: Int, val components: List<ComponentName>)

private object OemHelp {
    private val oems = mapOf(
        "samsung" to Oem(
            "Samsung", R.string.rel_oem_samsung,
            listOf(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
        ),
        "xiaomi" to Oem(
            "Xiaomi", R.string.rel_oem_xiaomi,
            listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        ),
        "oppo" to Oem(
            "Oppo", R.string.rel_oem_oppo,
            listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ),
        ),
        "vivo" to Oem(
            "Vivo", R.string.rel_oem_vivo,
            listOf(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        ),
    )

    fun forManufacturer(m: String): Oem? = oems[m.lowercase()] ?: if (m.equals("redmi", true) || m.equals("poco", true)) oems["xiaomi"] else null

    /** Tries each known OEM screen; falls back to this app's system settings page. */
    fun open(context: Context, oem: Oem) {
        for (c in oem.components) {
            val intent = Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
}
